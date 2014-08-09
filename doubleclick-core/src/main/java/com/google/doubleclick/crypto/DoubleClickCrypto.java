/*
 * Copyright 2014 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.doubleclick.crypto;

import static java.lang.Math.min;

import com.google.common.base.Objects;
import com.google.common.io.BaseEncoding;
import com.google.common.primitives.Ints;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.text.DateFormat;
import java.util.Arrays;
import java.util.Date;

import javax.annotation.Nullable;
import javax.crypto.Mac;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.SecretKeySpec;
import javax.inject.Inject;

/**
* Encryption and decryption support for the DoubleClick Ad Exchange RTB protocol.
* <p>
* Encrypted information is in the general format:
* <code>
* initVector:16 || E(payload:?) || I(signature:4)
* </code>
* <br>where:
* <ol>
*   <li>initVector = timeMillis:4 || timeMicros:4 || serverId:8 (AdX convention)</li>
*   <li>E(payload) = payload ^ hmac(encryptionKey, initVector) for each max-20-byte block</li>
*   <li>I(signature) = hmac(integrityKey, payload || initVector)[0..3]</li>
* </ol>
*/
public class DoubleClickCrypto {
  private static final Logger logger = LoggerFactory.getLogger(DoubleClickCrypto.class);
  public static final String KEY_ALGORITHM = "HmacSHA1";

  /** Initialization vector offset in the crypto package. */
  public static final int INITV_BASE = 0;
  /** Initialization vector size. */
  public static final int INITV_SIZE = 16;
  /** Seconds subfield offset in the initialization vector. */
  public static final int INITV_SECS_OFFSET = 0;
  /** Microseconds subfield offset in the initialization vector. */
  public static final int INITV_MICROS_OFFSET = 4;
  /** ServerId subfield offset in the initialization vector. */
  public static final int INITV_SERVERID_OFFSET = 8;
  /** Payload offset in the crypto package. */
  public static final int PAYLOAD_BASE = INITV_BASE + INITV_SIZE;
  /** Integrity signature size. */
  public static final int SIGNATURE_SIZE = 4;
  /** Overhead (non-Payload data) total size. */
  public static final int OVERHEAD_SIZE = INITV_SIZE + SIGNATURE_SIZE;

  private static final int COUNTER_PAGESIZE = 20;
  private static final int COUNTER_SECTIONS = 3*256 + 1;

  private static final int MICROS_PER_CURRENCY_UNIT = 1_000_000;

  private final Keys keys;

  /**
   * Initializes with the encryption keys.
   *
   * @param keys Keys for the buyer's Ad Exchange account
   */
  public DoubleClickCrypto(Keys keys) {
    this.keys = keys;
  }

  /**
   * Decodes data, from string to binary form.
   * The default implementation performs websafe-base64 decoding (RFC 3548).
   */
  protected @Nullable byte[] decode(@Nullable String data) {
    return data == null
        ? null
        : BaseEncoding.base64Url().decode(data);
  }

  /**
   * Encodes data, from binary form to string.
   * The default implementation performs websafe-base64 encoding (RFC 3548).
   */
  protected @Nullable String encode(@Nullable byte[] data) {
    return data == null
        ? null
        : BaseEncoding.base64Url().encode(data);
  }

  /**
   * Decrypts data.
   *
   * @param cipherData initVector || E(payload) || I(signature:4)
   * @return initVector || payload || I'(signature)
   * Where I'(signature) == I(signature) for success, different for failure
   * @throws DoubleClickCryptoException if the decryption fails
   */
  public byte[] decrypt(byte[] cipherData) {
    if (cipherData.length < OVERHEAD_SIZE) {
      throw new DoubleClickCryptoException("Invalid cipherData, " + cipherData.length + " bytes");
    }

    // workBytes := initVector || E(payload) || I(signature)
    byte[] workBytes = cipherData.clone();
    ByteBuffer workBuffer = ByteBuffer.wrap(workBytes);

    try {
      // workBytes := initVector || payload || I(signature)
      xorPayloadToHmacPad(workBytes);
      // workBytes := initVector || payload || I'(signature)
      int confirmationSignature = hmacSignature(workBytes);
      int integritySignature = workBuffer.getInt(workBytes.length - SIGNATURE_SIZE);
      workBuffer.putInt(workBytes.length - SIGNATURE_SIZE, confirmationSignature);

      if (confirmationSignature != integritySignature) {
        throw new DoubleClickCryptoException("Signature mismatch: "
            + Integer.toHexString(confirmationSignature)
            + " vs " + Integer.toHexString(integritySignature));
      }

      if (logger.isDebugEnabled()) {
        logger.debug(dump("Decrypted", cipherData, workBytes));
      }

      return workBytes;
    } catch (InvalidKeyException | NoSuchAlgorithmException | ShortBufferException e) {
      if (logger.isWarnEnabled()) {
        logger.warn(dump("Decrypted (failed)", cipherData, workBytes));
      }
      throw new DoubleClickCryptoException(e);
    }
  }

  /**
   * Encrypts data.
   *
   * @param plainData initVector || payload || zeros:4
   * @return initVector || E(payload) || I(signature)
   * @throws DoubleClickCryptoException if the encryption fails
   */
  public byte[] encrypt(byte[] plainData) {
    if (plainData.length < OVERHEAD_SIZE) {
      throw new DoubleClickCryptoException("Invalid plainData, " + plainData.length + " bytes");
    }

    // workBytes := initVector || payload || zeros:4
    byte[] workBytes = plainData.clone();
    ByteBuffer workBuffer = ByteBuffer.wrap(workBytes);

    try {
      // workBytes := initVector || payload || I(signature)
      int signature = hmacSignature(workBytes);
      workBuffer.putInt(workBytes.length - SIGNATURE_SIZE, signature);
      // workBytes := initVector || E(payload) || I(signature)
      xorPayloadToHmacPad(workBytes);

      if (logger.isDebugEnabled()) {
        logger.debug(dump("Encrypted", plainData, workBytes));
      }

      return workBytes;
    } catch (InvalidKeyException | NoSuchAlgorithmException | ShortBufferException e) {
      if (logger.isWarnEnabled()) {
        logger.warn(dump("Encrypted (failed)", plainData, workBytes));
      }
      throw new DoubleClickCryptoException(e);
    }
  }

  /**
   * Creates the initialization vector from component { timestamp, serverId } fields.
   * This is the format used by DoubleClick, and it's a good format generally,
   * even though the initialization vector can be any random data (a cryptographic nonce).
   */
  public byte[] createInitVector(@Nullable Date timestamp, long serverId) {
    byte[] initVector = new byte[INITV_SIZE];
    ByteBuffer byteBuffer = ByteBuffer.wrap(initVector);

    if (timestamp != null) {
      byteBuffer.putInt(INITV_SECS_OFFSET, (int) (timestamp.getTime() / 1000));
      byteBuffer.putInt(INITV_MICROS_OFFSET, (int) (timestamp.getTime() % 1000 * 1000));
    }

    byteBuffer.putLong(INITV_SERVERID_OFFSET, serverId);
    return initVector;
  }

  /**
   * Packages plaintext payload for encryption; returns initVector || payload || zeros:4.
   */
  protected byte[] initPlainData(int payloadSize, @Nullable byte[] initVector) {
    byte[] plainData = new byte[OVERHEAD_SIZE + payloadSize];

    if (initVector != null) {
      if (initVector.length != INITV_SIZE) {
        throw new DoubleClickCryptoException(
            "InitVector is " + initVector.length + " bytes, should be " + INITV_SIZE);
      }

      System.arraycopy(initVector, 0, plainData, INITV_BASE, INITV_SIZE);
    }

    return plainData;
  }

  /**
   * payload = payload ^ hmac(encryptionKey, initVector || counterBytes) per max-20-byte blocks.
   */
  private void xorPayloadToHmacPad(byte[] workBytes)
      throws NoSuchAlgorithmException, InvalidKeyException, ShortBufferException {
    int payloadSize = workBytes.length - OVERHEAD_SIZE;
    int sections = (payloadSize + COUNTER_PAGESIZE - 1) / COUNTER_PAGESIZE;
    if (sections > COUNTER_SECTIONS) {
      throw new DoubleClickCryptoException("Payload is " + payloadSize
          + " bytes, exceeds limit of " + COUNTER_PAGESIZE * COUNTER_SECTIONS);
    }

    Mac encryptionHmac = Mac.getInstance("HmacSHA1");
    byte[] pad = new byte[COUNTER_PAGESIZE + 3];
    int counterSize = 0;

    for (int section = 0; section < sections; ++section) {
      int sectionBase = section * COUNTER_PAGESIZE;
      int sectionSize = min(payloadSize - sectionBase, COUNTER_PAGESIZE);
      encryptionHmac.reset();
      encryptionHmac.init(keys.getEncryptionKey());
      encryptionHmac.update(workBytes, INITV_BASE, INITV_SIZE);
      if (counterSize != 0) {
        encryptionHmac.update(pad, COUNTER_PAGESIZE, counterSize);
      }
      encryptionHmac.doFinal(pad, 0);

      for (int i = 0; i < sectionSize; ++i) {
        workBytes[PAYLOAD_BASE + sectionBase + i] ^= pad[i];
      }

      if (counterSize == 0 || ++pad[COUNTER_PAGESIZE + counterSize - 1] == 0) {
        ++counterSize;
      }
    }
  }

  /**
   * Return signature = hmac(integrityKey, payload || initVector)
   */
  private int hmacSignature(byte[] workBytes) throws NoSuchAlgorithmException, InvalidKeyException {
    Mac integrityHmac = Mac.getInstance("HmacSHA1");
    integrityHmac.init(keys.getIntegrityKey());
    integrityHmac.update(workBytes, PAYLOAD_BASE, workBytes.length - OVERHEAD_SIZE);
    integrityHmac.update(workBytes, INITV_BASE, INITV_SIZE);
    return Ints.fromByteArray(integrityHmac.doFinal());
  }

  private static String dump(String header, byte[] inData, byte[] workBytes) {
    ByteBuffer initvBuffer = ByteBuffer.wrap(workBytes, INITV_BASE, INITV_SIZE);
    Date timestamp = new Date(initvBuffer.getInt(INITV_BASE + INITV_SECS_OFFSET) * 1000L
        + initvBuffer.getInt(INITV_BASE + INITV_MICROS_OFFSET) / 1000);
    long serverId = initvBuffer.getLong(INITV_BASE + INITV_SERVERID_OFFSET);
    return new StringBuilder()
        .append(header)
        .append(": initVector={timestamp ")
            .append(DateFormat.getDateTimeInstance().format(timestamp))
            .append(", serverId ").append(serverId)
        .append("}\ninput  =").append(toHexString(inData))
        .append("\noutput =").append(toHexString(workBytes))
        .toString();
  }

  private static String toHexString(byte[] bytes) {
    StringBuilder sb = new StringBuilder("[");

    for (byte b : bytes) {
      sb.append(Integer.toHexString(b >> 4 & 0xF)).append(Integer.toHexString(b & 0xF)).append(' ');
    }

    if (bytes.length != 0) {
      sb.setLength(sb.length() - 1);
    }

    return sb.append("]").toString();
  }

  /**
   * Holds the keys used to configure DoubleClick cryptography.
   */
  public static class Keys {
    private final SecretKeySpec encryptionKey;
    private final SecretKeySpec integrityKey;

    public Keys(SecretKeySpec encryptionKey, SecretKeySpec integrityKey) {
      this.encryptionKey = encryptionKey;
      this.integrityKey = integrityKey;
    }

    public SecretKeySpec getEncryptionKey() {
      return encryptionKey;
    }

    public SecretKeySpec getIntegrityKey() {
      return integrityKey;
    }

    @Override public String toString() {
      return Objects.toStringHelper(this).omitNullValues()
          .add("encryptionKey", encryptionKey.getAlgorithm() + '/' + encryptionKey.getFormat())
          .add("integrityKey", integrityKey.getAlgorithm() + '/' + integrityKey.getFormat())
          .toString();
    }
  }

  /**
   * Encryption for winning price.
   * <p>
   * See <a href="https://developers.google.com/ad-exchange/rtb/response-guide/decrypt-price">
   * Decrypting Price Confirmations</a>.
   */
  public static class Price extends DoubleClickCrypto {
    private static final int PAYLOAD_SIZE = 8;

    @Inject
    public Price(Keys keys) {
      super(keys);
    }

    /**
     * Encrypts the winning price.
     *
     * @param priceValue the price in millis (1/1000th of the currency unit)
     * @param initVector up to 16 bytes of nonce data
     * @return encrypted price
     * @throws DoubleClickCryptoException if the encryption fails
     */
    public byte[] encryptPriceMillis(long priceValue, @Nullable byte[] initVector) {
      byte[] plainData = initPlainData(PAYLOAD_SIZE, initVector);
      ByteBuffer.wrap(plainData).putLong(PAYLOAD_BASE, priceValue);
      return encrypt(plainData);
    }

    /**
     * Decrypts the winning price.
     *
     * @param priceCipher encrypted price
     * @return the price value in millis (1/1000th of the currency unit)
     * @throws DoubleClickCryptoException if the decryption fails
     */
    public long decryptPriceMillis(byte[] priceCipher) {
      if (priceCipher.length != (OVERHEAD_SIZE + PAYLOAD_SIZE)) {
        throw new DoubleClickCryptoException("Price is " + priceCipher.length
            + " bytes, should be " + (OVERHEAD_SIZE + PAYLOAD_SIZE));
      }

      byte[] plainData = decrypt(priceCipher);
      return ByteBuffer.wrap(plainData).getLong(PAYLOAD_BASE);
    }

    /**
     * Encrypts and encodes the winning price.
     *
     * @param priceMillis the price in millis (1/1000th of the currency unit)
     * @param initVector up to 16 bytes of nonce data
     * @return encrypted price, encoded as websafe-base64
     * @throws DoubleClickCryptoException if the encryption fails
     */
    public String encodePriceMillis(long priceMillis, @Nullable byte[] initVector) {
      return encode(encryptPriceMillis(priceMillis, initVector));
    }

    /**
     * Encrypts and encodes the winning price.
     *
     * @param priceValue the price
     * @param initVector up to 16 bytes of nonce data
     * @return encrypted price, encoded as websafe-base64
     * @throws DoubleClickCryptoException if the encryption fails
     */
    public String encodePriceValue(double priceValue, @Nullable byte[] initVector) {
      return encodePriceMillis((long) (priceValue * MICROS_PER_CURRENCY_UNIT), initVector);
    }

    /**
     * Decodes and decrypts the winning price.
     *
     * @param priceCipher encrypted price, encoded as websafe-base64
     * @return the price value in millis (1/1000th of the currency unit)
     * @throws DoubleClickCryptoException if the decryption fails
     */
    public long decodePriceMillis(String priceCipher) {
      return decryptPriceMillis(decode(priceCipher));
    }

    /**
     * Decodes and decrypts the winning price.
     *
     * @param priceCipher encrypted price, encoded as websafe-base64
     * @return the price value
     * @throws DoubleClickCryptoException if the decryption fails
     */
    public double decodePriceValue(String priceCipher) {
      return decodePriceMillis(priceCipher) / ((double) MICROS_PER_CURRENCY_UNIT);
    }
  }

  /**
   * Encryption for advertising ID.
   * <p> See
   * <a href="https://developers.google.com/ad-exchange/rtb/response-guide/decrypt-advertising-id">
   * Decrypting Advertising ID</a>.
   */
  public static class AdId extends DoubleClickCrypto {
    private static final int PAYLOAD_SIZE = 16;

    @Inject
    public AdId(Keys keys) {
      super(keys);
    }

    /**
     * Encrypts the Advertising Id.
     *
     * @param adidPlain the AdId
     * @param initVector up to 16 bytes of nonce data
     * @return encrypted AdId
     * @throws DoubleClickCryptoException if the encryption fails
     */
    public byte[] encryptAdId(byte[] adidPlain, @Nullable byte[] initVector) {
      if (adidPlain.length != PAYLOAD_SIZE) {
        throw new DoubleClickCryptoException(
            "AdId is " + adidPlain.length + " bytes, should be " + PAYLOAD_SIZE);
      }

      byte[] plainData = initPlainData(PAYLOAD_SIZE, initVector);
      System.arraycopy(adidPlain, 0, plainData, PAYLOAD_BASE, PAYLOAD_SIZE);
      return encrypt(plainData);
    }

    /**
     * Decrypts the AdId.
     *
     * @param adidCipher encrypted AdId
     * @return the AdId
     * @throws DoubleClickCryptoException if the decryption fails
     */
    public byte[] decryptAdId(byte[] adidCipher) {
      if (adidCipher.length != (OVERHEAD_SIZE + PAYLOAD_SIZE)) {
        throw new DoubleClickCryptoException(
            "AdId is " + adidCipher.length + " bytes, should be " + (OVERHEAD_SIZE + PAYLOAD_SIZE));
      }

      byte[] plainData = decrypt(adidCipher);
      return Arrays.copyOfRange(plainData, PAYLOAD_BASE, plainData.length - SIGNATURE_SIZE);
    }
  }

  /**
   * Encryption for IDFA.
   * <p> See
   * <a href="https://support.google.com/adxbuyer/answer/3221407">
   * Targeting mobile app inventory with IDFA</a>.
   */
  public static class Idfa extends DoubleClickCrypto {
    @Inject
    public Idfa(Keys keys) {
      super(keys);
    }

    /**
     * Encrypts the IDFA.
     *
     * @param idfaPlain the IDFA
     * @param initVector up to 16 bytes of nonce data
     * @return encrypted IDFA
     * @throws DoubleClickCryptoException if the encryption fails
     */
    public byte[] encryptIdfa(byte[] idfaPlain, @Nullable byte[] initVector) {
      byte[] plainData = initPlainData(idfaPlain.length, initVector);
      System.arraycopy(idfaPlain, 0, plainData, PAYLOAD_BASE, idfaPlain.length);
      return encrypt(plainData);
    }

    /**
     * Decrypts the IDFA.
     *
     * @param idfaCipher encrypted IDFA
     * @return the IDFA
     * @throws DoubleClickCryptoException if the decryption fails
     */
    public byte[] decryptIdfa(byte[] idfaCipher) {
      byte[] plainData = decrypt(idfaCipher);
      return Arrays.copyOfRange(plainData, PAYLOAD_BASE, plainData.length - SIGNATURE_SIZE);
    }

    /**
     * Encrypts and encodes the IDFA.
     *
     * @param idfaPlain the IDFA
     * @param initVector up to 16 bytes of nonce data
     * @return encrypted IDFA, websafe-base64 encoded
     * @throws DoubleClickCryptoException if the encryption fails
     */
    public String encodeIdfa(byte[] idfaPlain, @Nullable byte[] initVector) {
      return encode(encryptIdfa(idfaPlain, initVector));
    }

    /**
     * Decodes and decrypts the IDFA.
     *
     * @param idfaCipher encrypted IDFA, websafe-base64 encoded
     * @return the IDFA
     * @throws DoubleClickCryptoException if the decryption fails
     */
    public byte[] decodeIdfa(String idfaCipher) {
      return decryptIdfa(decode(idfaCipher));
    }
  }

  /**
   * Encryption for HyperlocalSet geofence information.
   * <p> See
   * <a href="https://developers.google.com/ad-exchange/rtb/response-guide/decrypt-hyperlocal">
   * Decrypting Hyperlocal Targeting Signals</a>.
   */
  public static class Hyperlocal extends DoubleClickCrypto {
    @Inject
    public Hyperlocal(Keys keys) {
      super(keys);
    }

    /**
     * Encrypts the serialized HyperlocalSet.
     *
     * @param hyperlocalPlain the HyperlocalSet
     * @param initVector up to 16 bytes of nonce data
     * @return encrypted HyperlocalSet
     * @throws DoubleClickCryptoException if the encryption fails
     */
    public byte[] encryptHyperlocal(byte[] hyperlocalPlain, @Nullable byte[] initVector) {
      byte[] plainData = initPlainData(hyperlocalPlain.length, initVector);
      System.arraycopy(hyperlocalPlain, 0, plainData, PAYLOAD_BASE, hyperlocalPlain.length);
      return encrypt(plainData);
    }

    /**
     * Decrypts the serialized HyperlocalSet.
     *
     * @param hyperlocalCipher encrypted HyperlocalSet
     * @return the HyperLocalSet
     * @throws DoubleClickCryptoException if the decryption fails
     */
    public byte[] decryptHyperlocal(byte[] hyperlocalCipher) {
      byte[] plainData = decrypt(hyperlocalCipher);
      return Arrays.copyOfRange(plainData, PAYLOAD_BASE, plainData.length - SIGNATURE_SIZE);
    }
  }
}
