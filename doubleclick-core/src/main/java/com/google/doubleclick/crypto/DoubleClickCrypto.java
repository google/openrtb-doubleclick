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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.Math.min;

import com.google.common.base.MoreObjects;
import com.google.common.io.BaseEncoding;
import com.google.common.primitives.Ints;
import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.text.DateFormat;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nullable;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.ShortBufferException;
import javax.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
* Encryption and decryption support for the DoubleClick Ad Exchange RTB protocol.
*
* <p>Encrypted payloads are wrapped by "packages" in the general format:
* <code>
* initVector:16 || E(payload:?) || I(signature:4)
* </code>
* <br>where:
* <ol>
*   <li>{@code initVector = timestamp:8 || serverId:8} (AdX convention)</li>
*   <li>{@code E(payload) = payload ^ hmac(encryptionKey, initVector)} per max-20-byte block</li>
*   <li>{@code I(signature) = hmac(integrityKey, payload || initVector)[0..3]}</li>
* </ol>
*
* <p>This class, and all nested classes / subclasses, are threadsafe.
*/
public class DoubleClickCrypto {
  private static final Logger logger = LoggerFactory.getLogger(DoubleClickCrypto.class);
  public static final String KEY_ALGORITHM = "HmacSHA1";

  /** Initialization vector offset in the crypto package. */
  public static final int INITV_BASE = 0;
  /** Initialization vector size. */
  public static final int INITV_SIZE = 16;
  /** Timestamp subfield offset in the initialization vector. */
  public static final int INITV_TIMESTAMP_OFFSET = 0;
  /** ServerId subfield offset in the initialization vector. */
  public static final int INITV_SERVERID_OFFSET = 8;
  /** Payload offset in the crypto package. */
  public static final int PAYLOAD_BASE = INITV_BASE + INITV_SIZE;
  /** Integrity signature size. */
  public static final int SIGNATURE_SIZE = 4;
  /** Overhead (non-Payload data) total size. */
  public static final int OVERHEAD_SIZE = INITV_SIZE + SIGNATURE_SIZE;

  private static final int COUNTER_PAGESIZE = 20;
  private static final int COUNTER_SECTIONS = 3 * 256 + 1;

  private static final int MICROS_PER_CURRENCY_UNIT = 1_000_000;

  private final Keys keys;
  private final ThreadLocalRandom fastRandom = ThreadLocalRandom.current();

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
  @Nullable protected byte[] decode(@Nullable String data) {
    return data == null ? null : Base64.getUrlDecoder().decode(data);
  }

  /**
   * Encodes data, from binary form to string.
   * The default implementation performs websafe-base64 encoding (RFC 3548).
   */
  @Nullable protected String encode(@Nullable byte[] data) {
    return data == null ? null : Base64.getUrlEncoder().encodeToString(data);
  }

  /**
   * Decrypts data.
   *
   * @param cipherData {@code initVector || E(payload) || I(signature)}
   * @return {@code initVector || payload || I'(signature)}
   *     Where I'(signature) == I(signature) for success, different for failure
   */
  public byte[] decrypt(byte[] cipherData) throws SignatureException {
    checkArgument(cipherData.length >= OVERHEAD_SIZE,
        "Invalid cipherData, %s bytes", cipherData.length);

    // workBytes := initVector || E(payload) || I(signature)
    byte[] workBytes = cipherData.clone();
    ByteBuffer workBuffer = ByteBuffer.wrap(workBytes);
    boolean success = false;

    try {
      // workBytes := initVector || payload || I(signature)
      xorPayloadToHmacPad(workBytes);
      // workBytes := initVector || payload || I'(signature)
      int confirmationSignature = hmacSignature(workBytes);
      int integritySignature = workBuffer.getInt(workBytes.length - SIGNATURE_SIZE);
      workBuffer.putInt(workBytes.length - SIGNATURE_SIZE, confirmationSignature);

      if (confirmationSignature != integritySignature) {
        throw new SignatureException("Signature mismatch: "
            + Integer.toHexString(confirmationSignature)
            + " vs " + Integer.toHexString(integritySignature));
      }

      if (logger.isDebugEnabled()) {
        logger.debug(dump("Decrypted", cipherData, workBytes));
      }

      success = true;
      return workBytes;
    } finally {
      if (!success && logger.isDebugEnabled()) {
        logger.debug(dump("Decrypted (failed)", cipherData, workBytes));
      }
    }
  }

  /**
   * Encrypts data.
   *
   * @param plainData {@code initVector || payload || zeros:4}
   * @return {@code initVector || E(payload) || I(signature)}
   */
  public byte[] encrypt(byte[] plainData) {
    checkArgument(plainData.length >= OVERHEAD_SIZE,
        "Invalid plainData, %s bytes", plainData.length);

    // workBytes := initVector || payload || zeros:4
    byte[] workBytes = plainData.clone();
    ByteBuffer workBuffer = ByteBuffer.wrap(workBytes);
    boolean success = false;

    try {
      // workBytes := initVector || payload || I(signature)
      int signature = hmacSignature(workBytes);
      workBuffer.putInt(workBytes.length - SIGNATURE_SIZE, signature);
      // workBytes := initVector || E(payload) || I(signature)
      xorPayloadToHmacPad(workBytes);

      if (logger.isDebugEnabled()) {
        logger.debug(dump("Encrypted", plainData, workBytes));
      }

      success = true;
      return workBytes;
    } finally {
      if (!success && logger.isDebugEnabled()) {
        logger.debug(dump("Encrypted (failed)", plainData, workBytes));
      }
    }
  }

  /**
   * Creates the initialization vector from component {@code (timestamp, serverId)} fields.
   *
   * @param timestamp Timestamp subfield. Notice that Data is not ideal for this because it's
   *     limited to millisecond precision, which leaves leave some bits unused in the init vector
   * @param serverId Server ID subfield (whatever a server uses as a public ID, e.g. its IPv4)
   * @return initialization vector
   * @see #createInitVector(long, long)
   */
  @SuppressWarnings("deprecation")
  public byte[] createInitVector(@Nullable Date timestamp, long serverId) {
    return createInitVector(
        timestamp == null ? 0L : millisToSecsAndMicros(timestamp.getTime()),
        serverId);
  }

  /**
   * Creates the initialization vector from component {@code (timestamp, serverId)} fields.
   * This is the format used by DoubleClick, and it's a good format generally,
   * even though the initialization vector can be any random data (a cryptographic nonce).
   *
   * <p>NOTE: Follow the advice from
   * https://developers.google.com/ad-exchange/rtb/response-guide/decrypt-price#detecting_stale
   * by using a high-resolution timestamp; also if the {@code serverId} is not necessary, providing
   * a random value there helps further to prevent replay attacks. In all methods that have
   * a {@code initVector} parameter, passing null will cause {@code (current time, random)}
   * to be used (so if you really want all-zeros {@code initVector}, e.g. in unit tests to make
   * results reproducible, pass a zero-filled array).
   *
   * @param timestamp Timestamp subfield. Notice this is not supposed to be a millis/nanos value
   *     like in common Java API; it should be: seconds-since-epoch in the upper 32bits,
   *     microseconds in the lower 32 bits
   * @param serverId Server ID subfield (whatever a server uses as a public ID, e.g. its IPv4)
   * @return initialization vector
   */
  public byte[] createInitVector(long timestamp, long serverId) {
    byte[] initVector = new byte[INITV_SIZE];
    ByteBuffer byteBuffer = ByteBuffer.wrap(initVector);
    byteBuffer.putLong(INITV_TIMESTAMP_OFFSET, timestamp);
    byteBuffer.putLong(INITV_SERVERID_OFFSET, serverId);
    return initVector;
  }

  /**
   * Returns the {@code timestamp} field from encrypted or decrypted data. Assumes that its
   * initialization vector has the structure {@code (timestamp, serverId)}.
   *
   * @param data Encrypted or decrypted data (the initialization vector is never encrypted)
   * @return Timestamp subfield of the initialization vector, in the form of a Date.
   *     This assumes the init vector was created with {@link #createInitVector(Date, long)}
   *     or similar method consistent with the DoubleClick crypto specification
   */
  public Date getTimestamp(byte[] data) {
    long secondsAndMicros = ByteBuffer.wrap(data).getLong(INITV_BASE + INITV_TIMESTAMP_OFFSET);
    return new Date(secsAndMicrosToMillis(secondsAndMicros));
  }

  /**
   * Returns the {@code serverId} field from encrypted or decrypted data. Assumes that its
   * initialization vector has the structure {@code (timestamp, serverId)}.
   *
   * @param data Encrypted or decrypted data (the initialization vector is never encrypted)
   * @return Timestamp subfield of the initialization vector.
   */
  public long getServerId(byte[] data) {
    return ByteBuffer.wrap(data).getLong(INITV_BASE + INITV_SERVERID_OFFSET);
  }

  /**
   * Packages plaintext payload for encryption; returns {@code initVector || payload || zeros:4}.
   */
  protected byte[] initPlainData(int payloadSize, @Nullable byte[] initVector) {
    byte[] plainData = new byte[OVERHEAD_SIZE + payloadSize];

    if (initVector == null) {
      ByteBuffer byteBuffer = ByteBuffer.wrap(plainData);
      byteBuffer.putLong(INITV_TIMESTAMP_OFFSET, millisToSecsAndMicros(System.currentTimeMillis()));
      byteBuffer.putLong(INITV_SERVERID_OFFSET, fastRandom.nextLong());
    } else {
      System.arraycopy(initVector, 0, plainData, INITV_BASE, min(INITV_SIZE, initVector.length));
    }

    return plainData;
  }

  /**
   * {@code payload = payload ^ hmac(encryptionKey, initVector || counterBytes)}
   * per max-20-byte blocks.
   */
  private void xorPayloadToHmacPad(byte[] workBytes) {
    int payloadSize = workBytes.length - OVERHEAD_SIZE;
    int sections = (payloadSize + COUNTER_PAGESIZE - 1) / COUNTER_PAGESIZE;
    checkArgument(sections <= COUNTER_SECTIONS, "Payload is %s bytes, exceeds limit of %s",
        payloadSize, COUNTER_PAGESIZE * COUNTER_SECTIONS);

    Mac encryptionHmac = createMac();

    byte[] pad = new byte[COUNTER_PAGESIZE + 3];
    int counterSize = 0;

    for (int section = 0; section < sections; ++section) {
      int sectionBase = section * COUNTER_PAGESIZE;
      int sectionSize = min(payloadSize - sectionBase, COUNTER_PAGESIZE);

      try {
        encryptionHmac.reset();
        encryptionHmac.init(keys.getEncryptionKey());
        encryptionHmac.update(workBytes, INITV_BASE, INITV_SIZE);
        if (counterSize != 0) {
          encryptionHmac.update(pad, COUNTER_PAGESIZE, counterSize);
        }
        encryptionHmac.doFinal(pad, 0);
      } catch (ShortBufferException | InvalidKeyException e) {
        throw new IllegalStateException(e);
      }

      for (int i = 0; i < sectionSize; ++i) {
        workBytes[PAYLOAD_BASE + sectionBase + i] ^= pad[i];
      }

      Arrays.fill(pad, 0, COUNTER_PAGESIZE, (byte) 0);

      if (counterSize == 0 || ++pad[COUNTER_PAGESIZE + counterSize - 1] == 0) {
        ++counterSize;
      }
    }
  }

  private static Mac createMac() {
    try {
      return Mac.getInstance("HmacSHA1");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * {@code signature = hmac(integrityKey, payload || initVector)}
   */
  private int hmacSignature(byte[] workBytes) {
    try {
      Mac integrityHmac = createMac();
      integrityHmac.init(keys.getIntegrityKey());
      integrityHmac.update(workBytes, PAYLOAD_BASE, workBytes.length - OVERHEAD_SIZE);
      integrityHmac.update(workBytes, INITV_BASE, INITV_SIZE);
      return Ints.fromByteArray(integrityHmac.doFinal());
    } catch (InvalidKeyException e) {
      throw new IllegalStateException(e);
    }
  }

  private static String dump(String header, byte[] inData, byte[] workBytes) {
    ByteBuffer initvBuffer = ByteBuffer.wrap(workBytes, INITV_BASE, INITV_SIZE);
    Date timestamp = new Date(initvBuffer.getLong(INITV_BASE + INITV_TIMESTAMP_OFFSET));
    long serverId = initvBuffer.getLong(INITV_BASE + INITV_SERVERID_OFFSET);
    return new StringBuilder()
        .append(header)
        .append(": initVector={timestamp ")
            .append(DateFormat.getDateTimeInstance().format(timestamp))
            .append(", serverId ").append(serverId)
        .append("}, input =").append(BaseEncoding.base16().encode(inData))
        .append(", output =").append(BaseEncoding.base16().encode(workBytes))
        .toString();
  }

  @Override public String toString() {
    return MoreObjects.toStringHelper(this).omitNullValues()
        .add("keys", keys)
        .toString();
  }

  private static long millisToSecsAndMicros(long timestamp) {
    return ((timestamp / 1000) << 32) | ((timestamp % 1000) * 1000);
  }

  private static long secsAndMicrosToMillis(long secondsAndMicros) {
    return ((secondsAndMicros >> 32) * 1000) + (secondsAndMicros & 0xFFFFFFFFL) / 1000;
  }

  /**
   * Holds the keys used to configure DoubleClick cryptography.
   */
  public static class Keys {
    private final SecretKey encryptionKey;
    private final SecretKey integrityKey;

    public Keys(SecretKey encryptionKey, SecretKey integrityKey) throws InvalidKeyException {
      this.encryptionKey = encryptionKey;
      this.integrityKey = integrityKey;

      // Forces early failure if any of the keys are not good.
      // This allows us to spare callers from InvalidKeyException in several methods.
      Mac hmac = DoubleClickCrypto.createMac();
      hmac.init(encryptionKey);
      hmac.reset();
      hmac.init(integrityKey);
      hmac.reset();
    }

    public SecretKey getEncryptionKey() {
      return encryptionKey;
    }

    public SecretKey getIntegrityKey() {
      return integrityKey;
    }

    @Override public int hashCode() {
      return encryptionKey.hashCode() ^ integrityKey.hashCode();
    }

    @Override public boolean equals(Object obj) {
      if (obj == this) {
        return true;
      } else if (!(obj instanceof Keys)) {
        return false;
      }
      Keys other = (Keys) obj;
      return encryptionKey.equals(other.encryptionKey) && integrityKey.equals(other.integrityKey);
    }

    @Override public String toString() {
      return MoreObjects.toStringHelper(this).omitNullValues()
          .add("encryptionKey", encryptionKey.getAlgorithm() + '/' + encryptionKey.getFormat())
          .add("integrityKey", integrityKey.getAlgorithm() + '/' + integrityKey.getFormat())
          .toString();
    }
  }

  /**
   * Encryption for winning price.
   *
   * <p>See <a href="https://developers.google.com/ad-exchange/rtb/response-guide/decrypt-price">
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
     * @param priceValue the price in micros (1/1.000.000th of the currency unit)
     * @param initVector up to 16 bytes of nonce data
     * @return encrypted price
     * @see #createInitVector(Date, long)
     */
    public byte[] encryptPriceMicros(long priceValue, @Nullable byte[] initVector) {
      byte[] plainData = initPlainData(PAYLOAD_SIZE, initVector);
      ByteBuffer.wrap(plainData).putLong(PAYLOAD_BASE, priceValue);
      return encrypt(plainData);
    }

    /**
     * Decrypts the winning price.
     *
     * @param priceCipher encrypted price
     * @return the price value in micros (1/1.000.000th of the currency unit)
     */
    public long decryptPriceMicros(byte[] priceCipher) throws SignatureException {
      checkArgument(priceCipher.length == (OVERHEAD_SIZE + PAYLOAD_SIZE),
          "Price is %s bytes, should be %s", priceCipher.length, (OVERHEAD_SIZE + PAYLOAD_SIZE));

      byte[] plainData = decrypt(priceCipher);
      return ByteBuffer.wrap(plainData).getLong(PAYLOAD_BASE);
    }

    /**
     * Encrypts and encodes the winning price.
     *
     * @param priceMicros the price in micros (1/1.000.000th of the currency unit)
     * @param initVector up to 16 bytes of nonce data, or {@code null} for default
     *     generated data (see {@link #createInitVector(Date, long)}
     * @return encrypted price, encoded as websafe-base64
     */
    public String encodePriceMicros(long priceMicros, @Nullable byte[] initVector) {
      return encode(encryptPriceMicros(priceMicros, initVector));
    }

    /**
     * Encrypts and encodes the winning price.
     *
     * @param priceValue the price
     * @param initVector up to 16 bytes of nonce data, or {@code null} for default
     *     generated data (see {@link #createInitVector(Date, long)}
     * @return encrypted price, encoded as websafe-base64
     */
    public String encodePriceValue(double priceValue, @Nullable byte[] initVector) {
      return encodePriceMicros((long) (priceValue * MICROS_PER_CURRENCY_UNIT), initVector);
    }

    /**
     * Decodes and decrypts the winning price.
     *
     * @param priceCipher encrypted price, encoded as websafe-base64
     * @return the price value in micros (1/1.000.000th of the currency unit)
     */
    public long decodePriceMicros(String priceCipher) throws SignatureException {
      return decryptPriceMicros(decode(checkNotNull(priceCipher)));
    }

    /**
     * Decodes and decrypts the winning price.
     *
     * @param priceCipher encrypted price, encoded as websafe-base64
     * @return the price value
     */
    public double decodePriceValue(String priceCipher) throws SignatureException {
      return decodePriceMicros(priceCipher) / ((double) MICROS_PER_CURRENCY_UNIT);
    }
  }

  /**
   * Encryption for Advertising ID.
   *
   * <p>See
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
     * @param initVector up to 16 bytes of nonce data, or {@code null} for default
     *     generated data (see {@link #createInitVector(Date, long)}
     * @return encrypted AdId
     */
    public byte[] encryptAdId(byte[] adidPlain, @Nullable byte[] initVector) {
      checkArgument(adidPlain.length == PAYLOAD_SIZE,
            "AdId is %s bytes, should be %s", adidPlain.length, PAYLOAD_SIZE);

      byte[] plainData = initPlainData(PAYLOAD_SIZE, initVector);
      System.arraycopy(adidPlain, 0, plainData, PAYLOAD_BASE, PAYLOAD_SIZE);
      return encrypt(plainData);
    }

    /**
     * Decrypts the AdId.
     *
     * @param adidCipher encrypted AdId
     * @return the AdId
     */
    public byte[] decryptAdId(byte[] adidCipher) throws SignatureException {
      checkArgument(adidCipher.length == (OVERHEAD_SIZE + PAYLOAD_SIZE),
            "AdId is %s bytes, should be %s", adidCipher.length, (OVERHEAD_SIZE + PAYLOAD_SIZE));

      byte[] plainData = decrypt(adidCipher);
      return Arrays.copyOfRange(plainData, PAYLOAD_BASE, plainData.length - SIGNATURE_SIZE);
    }
  }

  /**
   * Encryption for IDFA.
   *
   * <p>See
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
     * @param initVector up to 16 bytes of nonce data, or {@code null} for default
     *     generated data (see {@link #createInitVector(Date, long)}
     * @return encrypted IDFA
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
     */
    public byte[] decryptIdfa(byte[] idfaCipher) throws SignatureException {
      byte[] plainData = decrypt(idfaCipher);
      return Arrays.copyOfRange(plainData, PAYLOAD_BASE, plainData.length - SIGNATURE_SIZE);
    }

    /**
     * Encrypts and encodes the IDFA.
     *
     * @param idfaPlain the IDFA
     * @param initVector up to 16 bytes of nonce data, or {@code null} for default
     *     generated data (see {@link #createInitVector(Date, long)}
     * @return encrypted IDFA, websafe-base64 encoded
     */
    public String encodeIdfa(byte[] idfaPlain, @Nullable byte[] initVector) {
      return encode(encryptIdfa(idfaPlain, initVector));
    }

    /**
     * Decodes and decrypts the IDFA.
     *
     * @param idfaCipher encrypted IDFA, websafe-base64 encoded
     * @return the IDFA
     */
    public byte[] decodeIdfa(String idfaCipher) throws SignatureException {
      return decryptIdfa(decode(idfaCipher));
    }
  }

  /**
   * Encryption for {@code HyperlocalSet} geofence information.
   *
   * <p>See
   * <a href="https://developers.google.com/ad-exchange/rtb/response-guide/decrypt-hyperlocal">
   * Decrypting Hyperlocal Targeting Signals</a>.
   */
  public static class Hyperlocal extends DoubleClickCrypto {
    @Inject
    public Hyperlocal(Keys keys) {
      super(keys);
    }

    /**
     * Encrypts the serialized {@code HyperlocalSet}.
     *
     * @param hyperlocalPlain the {@code HyperlocalSet}
     * @param initVector up to 16 bytes of nonce data, or {@code null} for default
     *     generated data (see {@link #createInitVector(Date, long)}
     * @return encrypted {@code HyperlocalSet}
     */
    public byte[] encryptHyperlocal(byte[] hyperlocalPlain, @Nullable byte[] initVector) {
      byte[] plainData = initPlainData(hyperlocalPlain.length, initVector);
      System.arraycopy(hyperlocalPlain, 0, plainData, PAYLOAD_BASE, hyperlocalPlain.length);
      return encrypt(plainData);
    }

    /**
     * Decrypts the serialized {@code HyperlocalSet}.
     *
     * @param hyperlocalCipher encrypted {@code HyperlocalSet}
     * @return the {@code HyperLocalSet}
     */
    public byte[] decryptHyperlocal(byte[] hyperlocalCipher) throws SignatureException {
      byte[] plainData = decrypt(hyperlocalCipher);
      return Arrays.copyOfRange(plainData, PAYLOAD_BASE, plainData.length - SIGNATURE_SIZE);
    }
  }
}
