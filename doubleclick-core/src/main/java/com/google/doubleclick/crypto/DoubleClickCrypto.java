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
import com.google.common.primitives.Ints;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.BaseNCodec;
import org.apache.commons.codec.binary.StringUtils;
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

  private static final int INITV_BASE = 0;
  private static final int INITV_SIZE = 16;
  private static final int INITV_SECS_OFFSET = 0;
  private static final int INITV_MICROS_OFFSET = 4;
  private static final int INITV_SERVERID_OFFSET = 8;
  private static final int PAYLOAD_BASE = INITV_BASE + INITV_SIZE;
  private static final int SIGNATURE_SIZE = 4;
  private static final int OVERHEAD_SIZE = INITV_SIZE + SIGNATURE_SIZE;
  private static final int COUNTER_PAGESIZE = 20;
  private static final int COUNTER_SECTIONS = 3*256 + 1;

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
   * The default implementation performs Base64 URL-safe decoding.
   */
  protected @Nullable byte[] decode(@Nullable String data) {
    return data == null
        ? null
        : CryptoBase64.forSize(data.length()).decode(data);
  }

  /**
   * Encodes data, from binary form to string.
   * The default implementation performs Base64 URL-safe encoding.
   */
  protected @Nullable String encode(@Nullable byte[] data) {
    return data == null
        ? null
        : StringUtils.newStringUtf8(CryptoBase64.forSize(data.length).encode(data));
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

    if (workBytes.length != 0) {
      integrityHmac.update(workBytes, PAYLOAD_BASE, workBytes.length - OVERHEAD_SIZE);
      integrityHmac.update(workBytes, INITV_BASE, INITV_SIZE);
    }
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
     * @param priceValue the price
     * @param initVector up to 16 bytes of nonce data
     * @return encrypted price
     * @throws DoubleClickCryptoException if the encryption fails
     */
    public byte[] encryptPrice(long priceValue, @Nullable byte[] initVector) {
      byte[] plainData = initPlainData(PAYLOAD_SIZE, initVector);
      ByteBuffer.wrap(plainData).putLong(PAYLOAD_BASE, priceValue);
      return encrypt(plainData);
    }

    /**
     * Decrypts the winning price.
     *
     * @param priceCipher encrypted price
     * @return the price value
     * @throws DoubleClickCryptoException if the decryption fails
     */
    public long decryptPrice(byte[] priceCipher) {
      if (priceCipher.length != (OVERHEAD_SIZE + PAYLOAD_SIZE)) {
        throw new DoubleClickCryptoException(
            "Price is " + priceCipher.length + " bytes, should be "
                + (OVERHEAD_SIZE + PAYLOAD_SIZE));
      }

      byte[] plainData = decrypt(priceCipher);
      return ByteBuffer.wrap(plainData).getLong(PAYLOAD_BASE);
    }

    /**
     * Encrypts and encodes the winning price.
     *
     * @param priceValue the price
     * @param initVector up to 16 bytes of nonce data
     * @return encrypted price, encoded as websafe-base64
     * @throws DoubleClickCryptoException if the encryption fails
     */
    public String encodePrice(long priceValue, @Nullable byte[] initVector) {
      return encode(encryptPrice(priceValue, initVector));
    }

    /**
     * Decodes and decrypts the winning price.
     *
     * @param priceCipher encrypted price, encoded as websafe-base64
     * @return the price value
     * @throws DoubleClickCryptoException if the decryption fails
     */
    public long decodePrice(String priceCipher) {
      return decryptPrice(decode(priceCipher));
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

    public byte[] encryptAdId(byte[] adidPlain, @Nullable byte[] initVector) {
      if (adidPlain.length != PAYLOAD_SIZE) {
        throw new DoubleClickCryptoException(
            "AdId is " + adidPlain.length + " bytes, should be " + PAYLOAD_SIZE);
      }

      byte[] plainData = initPlainData(PAYLOAD_SIZE, initVector);
      System.arraycopy(adidPlain, 0, plainData, PAYLOAD_BASE, PAYLOAD_SIZE);
      return encrypt(plainData);
    }

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

    public byte[] encryptIdfa(byte[] idfaPlain, @Nullable byte[] initVector) {
      if (idfaPlain.length < 1) {
        throw new DoubleClickCryptoException(
            "IDFA is " + idfaPlain.length + " bytes, should be >= 1");
      }

      byte[] plainData = initPlainData(idfaPlain.length, initVector);
      System.arraycopy(idfaPlain, 0, plainData, PAYLOAD_BASE, idfaPlain.length);
      return encrypt(plainData);
    }

    public byte[] decryptIdfa(byte[] idfaCipher) {
      byte[] plainData = decrypt(idfaCipher);
      return Arrays.copyOfRange(plainData, PAYLOAD_BASE, plainData.length - SIGNATURE_SIZE);
    }

    public String encodeIdfa(byte[] idfaPlain, @Nullable byte[] initVector) {
      return encode(encryptIdfa(idfaPlain, initVector));
    }

    public byte[] decodeIdfa(String idfaCipher) {
      return decryptIdfa(decode(idfaCipher));
    }
  }

  /**
   * Encryption for Hyperlocal geofence information.
   * <p> See
   * <a href="https://developers.google.com/ad-exchange/rtb/response-guide/decrypt-hyperlocal">
   * Decrypting Hyperlocal Targeting Signals</a>.
   */
  public static class Hyperlocal extends DoubleClickCrypto {
    @Inject
    public Hyperlocal(Keys keys) {
      super(keys);
    }

    public byte[] encryptHyperlocal(byte[] hyperlocalPlain, @Nullable byte[] initVector) {
      if (hyperlocalPlain.length < 1) {
        throw new DoubleClickCryptoException(
            "Hyperlocal is " + hyperlocalPlain.length + " bytes, should be >= 1");
      }

      byte[] plainData = initPlainData(hyperlocalPlain.length, initVector);
      System.arraycopy(hyperlocalPlain, 0, plainData, PAYLOAD_BASE, hyperlocalPlain.length);
      return encrypt(plainData);
    }

    public byte[] decryptHyperlocal(byte[] hyperlocalCipher) {
      byte[] plainData = decrypt(hyperlocalCipher);
      return Arrays.copyOfRange(plainData, PAYLOAD_BASE, plainData.length - SIGNATURE_SIZE);
    }
  }

  /**
   * Helper subclass to work around Base64's very bad defaults for buffer sizes.
   */
  static class CryptoBase64 extends Base64 {
    static final int SMALL_SIZE = 64;
    static final CryptoBase64 small = new CryptoBase64(SMALL_SIZE);
    final int defaultBufferSize;
    CryptoBase64(int defaultBufferSize) {
      super(BaseNCodec.MIME_CHUNK_SIZE, null, true);
      this.defaultBufferSize = defaultBufferSize;
    }
    @Override protected int getDefaultBufferSize() {
      return defaultBufferSize;
    }
    static CryptoBase64 forSize(int size) {
      return size <= SMALL_SIZE ? small : new CryptoBase64(size);
    }
  }
}
