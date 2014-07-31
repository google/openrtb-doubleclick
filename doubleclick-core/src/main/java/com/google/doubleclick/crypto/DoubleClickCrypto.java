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

import static java.lang.Math.max;
import static java.lang.Math.min;

import com.google.common.base.Objects;
import com.google.common.base.Strings;
import com.google.common.primitives.Ints;
import com.google.doubleclick.Doubleclick.BidRequest.HyperlocalSet;
import com.google.protobuf.InvalidProtocolBufferException;

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
import javax.crypto.spec.SecretKeySpec;
import javax.inject.Inject;

/**
* Encryption and decryption support for the DoubleClick Ad Exchange RTB protocol.
* <p>
* Encrypted information is in the general format:
* <code>
* WebSafe-Base64(nonce:16 | E(ciphertext:?) | E(signature:4))
* </code>
* <br>where:
* <ol>
*   <li>nonce = timeMillis:4 | timeMicros:4 | serverId:8 (AdX convention)</li>
*   <li>E(ciphertext) = payload ^ SHA1(nonce, encryptionKey)[0..?]</li>
*   <li>E(signature) = SHA1(ciphertext | nonce, integrityKey)[0..3]</li>
* </ol>
*/
public class DoubleClickCrypto {
  private static final Logger logger = LoggerFactory.getLogger(DoubleClickCrypto.class);
  public static final String KEY_ALGORITHM = "HmacSHA1";

  private static final int NONCE_BASE = 0;
  private static final int NONCE_SIZE = 16;
  private static final int NONCE_SECS_OFFSET = 0;
  private static final int NONCE_MICROS_OFFSET = 4;
  private static final int NONCE_SERVERID_OFFSET = 8;
  private static final int PAYLOAD_BASE = NONCE_BASE + NONCE_SIZE;
  private static final int SIGNATURE_SIZE = 4;
  private static final int OVERHEAD_SIZE = NONCE_SIZE + SIGNATURE_SIZE;

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
   * @param ciphertext binary ciphertext
   * @return binary plaintext
   * @throws DoubleClickCryptoException if the decryption fails
   */
  public byte[] decrypt(byte[] ciphertext) {
    if (ciphertext.length <= OVERHEAD_SIZE) {
      throw new DoubleClickCryptoException("Invalid ciphertext, " + ciphertext.length + " bytes");
    }

    byte[] plaintext = ciphertext.clone();

    try {
      byte[] hashNonce = hashNonce(plaintext);
      xorTo(plaintext, hashNonce);
      int cryptoSignature = ByteBuffer.wrap(plaintext).getInt(plaintext.length - SIGNATURE_SIZE);
      int plainSignature = hashSignature(plaintext);

      if (plainSignature != cryptoSignature) {
        throw new DoubleClickCryptoException("Signature mismatch: "
            + Integer.toHexString(plainSignature) + " vs " + Integer.toHexString(cryptoSignature));
      }

      if (logger.isDebugEnabled()) {
        logger.debug(dump("Decrypted", ciphertext, plaintext));
      }

      return plaintext;
    } catch (InvalidKeyException | NoSuchAlgorithmException e) {
      if (logger.isWarnEnabled()) {
        logger.warn(dump("Decrypted (failed)", ciphertext, plaintext));
      }
      throw new DoubleClickCryptoException(e);
    }
  }

  /**
   * Encrypts data.
   *
   * @param plaintext binary plaintext
   * @return binary ciphertext
   * @throws DoubleClickCryptoException if the encryption fails
   */
  public byte[] encrypt(byte[] plaintext) {
    if (plaintext.length < OVERHEAD_SIZE) {
      throw new DoubleClickCryptoException("Invalid plaintext, " + plaintext.length + " bytes");
    }

    try {
      byte[] ciphertext = plaintext.clone();
      ByteBuffer.wrap(ciphertext).putInt(
          ciphertext.length - SIGNATURE_SIZE, hashSignature(ciphertext));
      byte[] hashNonce = hashNonce(ciphertext);
      xorTo(ciphertext, hashNonce);

      if (logger.isDebugEnabled()) {
        logger.debug(dump("Encrypted", plaintext, ciphertext));
      }

      return ciphertext;
    } catch (InvalidKeyException | NoSuchAlgorithmException e) {
      if (logger.isWarnEnabled()) {
        logger.warn(dump("Encrypted (failed)", plaintext, null));
      }
      throw new DoubleClickCryptoException(e);
    }
  }

  private static void xorTo(byte[] data, byte[] hashNonce) {
    int end = min(hashNonce.length, data.length - OVERHEAD_SIZE);

    for (int i = 0; i < end; ++i) {
      data[PAYLOAD_BASE + i] ^= hashNonce[i];
    }
  }

  private byte[] hashNonce(byte[] data) throws NoSuchAlgorithmException, InvalidKeyException {
    Mac encryptionHmac = Mac.getInstance("HmacSHA1");
    encryptionHmac.init(keys.getEncryptionKey());
    encryptionHmac.update(data, NONCE_BASE, NONCE_SIZE);
    hashNoncePadding(data, encryptionHmac);
    return encryptionHmac.doFinal();
  }

  protected void hashNoncePadding(byte[] data, Mac encryptionHmac) {
    int payloadSize = data.length - OVERHEAD_SIZE;
    int pageSize = 20;
    if (payloadSize <= pageSize) {
      return;
    }

    int sections = (payloadSize + pageSize - 1) / pageSize;
    byte[] pad = new byte[sections + max(0, sections - 256) + max(0, sections - 512)];
    int padPos = 0;

    for (int section = 1; section <= sections; ++section) {
      if (section <= 256) {
        pad[padPos++] = (byte) (section - 1);
      } else if (section <= 512) {
        pad[padPos++] = 0;
        pad[padPos++] = (byte) (section - 256 - 1);
      } else if (section <= 768) {
        pad[padPos++] = 0;
        pad[padPos++] = 0;
        pad[padPos++] = (byte) (section - 512 - 1);
      } else {
        throw new DoubleClickCryptoException(
            "Payload is " + payloadSize + " bytes, exceeds limit of " + pageSize * 768);
      }
    }

    encryptionHmac.update(pad);
  }

  private int hashSignature(byte[] data) throws NoSuchAlgorithmException, InvalidKeyException {
    Mac integrityHmac = Mac.getInstance("HmacSHA1");
    integrityHmac.init(keys.getIntegrityKey());
    if (data.length != 0) {
      integrityHmac.update(data, PAYLOAD_BASE, data.length - OVERHEAD_SIZE);
      integrityHmac.update(data, NONCE_BASE, NONCE_SIZE);
    }
    return Ints.fromByteArray(integrityHmac.doFinal());
  }

  private static String dump(String header, byte[] inData, @Nullable byte[] outData) {
    StringBuilder sb = new StringBuilder();
    sb.append(header).append(": ");

    if (outData != null) {
      byte[] nonce = Arrays.copyOfRange(outData, NONCE_BASE, NONCE_SIZE);
      ByteBuffer nonceBuffer = ByteBuffer.wrap(nonce);
      Date timestamp = new Date(nonceBuffer.getInt(NONCE_BASE + NONCE_SECS_OFFSET) * 1000L
          + nonceBuffer.getInt(NONCE_BASE + NONCE_MICROS_OFFSET) / 1000);
      long serverId = nonceBuffer.getLong(NONCE_BASE + NONCE_SERVERID_OFFSET);
      sb.append("nonce:timestamp=").append(DateFormat.getDateTimeInstance().format(timestamp));
      sb.append(", nonce:serverId=").append(serverId);
    }

    sb.append("\ninput  =").append(toHexString(inData));

    if (outData != null && outData != inData) {
      sb.append("\noutput =").append(toHexString(outData));
    }

    return sb.toString();
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

  public byte[] createNonce(@Nullable Date timestamp, long serverId) {
    byte[] nonce = new byte[NONCE_SIZE];
    ByteBuffer byteBuffer = ByteBuffer.wrap(nonce);

    if (timestamp != null) {
      byteBuffer.putInt(NONCE_SECS_OFFSET, (int) (timestamp.getTime() / 1000));
      byteBuffer.putInt(NONCE_MICROS_OFFSET, (int) (timestamp.getTime() % 1000 * 1000));
    }

    byteBuffer.putLong(NONCE_SERVERID_OFFSET, serverId);
    return nonce;
  }

  protected byte[] initPlaintext(int payloadSize, @Nullable byte[] nonce) {
    byte[] plaintext = new byte[OVERHEAD_SIZE + payloadSize];

    if (nonce != null) {
      if (nonce.length != NONCE_SIZE) {
        throw new DoubleClickCryptoException(
            "Nonce is " + nonce.length + " bytes, should be " + NONCE_SIZE);
      }

      System.arraycopy(nonce, 0, plaintext, NONCE_BASE, NONCE_SIZE);
    }

    return plaintext;
  }

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
     * Encrypts the winning price. Accepts the price and raw nonce data.
     *
     * @param priceValue the price
     * @param nonce up to 16 bytes of nonce data
     * @return encrypted data encoded in AdX's format (web-safe base64)
     * @throws DoubleClickCryptoException if the encryption fails
     */
    public String encryptPrice(long priceValue, @Nullable byte[] nonce) {
      byte[] plaintext = initPlaintext(PAYLOAD_SIZE, nonce);
      ByteBuffer.wrap(plaintext).putLong(PAYLOAD_BASE, priceValue);
      return encode(encrypt(plaintext));
    }

    /**
     * Decrypts the winning price.
     *
     * @param encodedCiphertext encoded ciphertext
     * @return plaintext
     * @throws DoubleClickCryptoException if the decryption fails
     */
    public long decryptPrice(String encodedCiphertext) {
      if (Strings.isNullOrEmpty(encodedCiphertext)) {
        throw new DoubleClickCryptoException("Empty encoded ciphertext");
      }

      return ByteBuffer.wrap(decrypt(decode(encodedCiphertext))).getLong(PAYLOAD_BASE);
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

    public String encryptAdId(byte[] adidValue, @Nullable byte[] nonce) {
      if (adidValue.length != PAYLOAD_SIZE) {
        throw new DoubleClickCryptoException(
            "AdId is " + adidValue.length + " bytes, should be " + PAYLOAD_SIZE);
      }

      byte[] plaintext = initPlaintext(PAYLOAD_SIZE, nonce);
      System.arraycopy(adidValue, 0, plaintext, PAYLOAD_BASE, PAYLOAD_SIZE);
      return encode(encrypt(plaintext));
    }

    public byte[] decryptAdId(String encodedCiphertext) {
      if (Strings.isNullOrEmpty(encodedCiphertext)) {
        throw new DoubleClickCryptoException("Empty encoded ciphertext");
      }

      byte[] plaintext = decrypt(decode(encodedCiphertext));
      return Arrays.copyOfRange(plaintext, PAYLOAD_BASE, plaintext.length - SIGNATURE_SIZE);
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

    public String encryptIdfa(byte[] idfaValue, @Nullable byte[] nonce) {
      byte[] plaintext = initPlaintext(idfaValue.length, nonce);
      System.arraycopy(idfaValue, 0, plaintext, PAYLOAD_BASE, idfaValue.length);
      return encode(encrypt(plaintext));
    }

    public byte[] decryptIdfa(String encodedCiphertext) {
      if (Strings.isNullOrEmpty(encodedCiphertext)) {
        throw new DoubleClickCryptoException("Empty encoded ciphertext");
      }

      byte[] plaintext = decrypt(decode(encodedCiphertext));
      return Arrays.copyOfRange(plaintext, PAYLOAD_BASE, plaintext.length - SIGNATURE_SIZE);
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

    public byte[] encryptHyperlocal(HyperlocalSet hyperlocalValue, @Nullable byte[] nonce) {
      byte[] bytes = hyperlocalValue.toByteArray();
      byte[] plaintext = initPlaintext(bytes.length, nonce);
      System.arraycopy(bytes, 0, plaintext, PAYLOAD_BASE, bytes.length);
      return encrypt(plaintext);
    }

    public HyperlocalSet decryptHyperlocal(byte[] hyperlocalValue)
        throws InvalidProtocolBufferException {
      byte[] plaintext = decrypt(hyperlocalValue);
      byte[] bytes = Arrays.copyOfRange(plaintext, PAYLOAD_BASE, plaintext.length - SIGNATURE_SIZE);
      return HyperlocalSet.parseFrom(bytes);
    }
  }

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
