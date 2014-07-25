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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.apache.commons.codec.binary.Base64;
import org.junit.Test;

import java.util.Date;

import javax.crypto.spec.SecretKeySpec;

/**
 * Tests for {@link DoubleClickCrypto}.
 */
public class DoubleClickCryptoTest {

  static final DoubleClickCrypto.Keys KEYS = new DoubleClickCrypto.Keys(
      new SecretKeySpec(
          Base64.decodeBase64("sIxwz7yw62yrfoLGt12lIHKuYrK/S5kLuApI2BQe7Ac="), "HmacSHA1"),
      new SecretKeySpec(
          Base64.decodeBase64("v3fsVcMBMMHYzRhi7SpM0sdqwzvAxM6KPTu9OtVod5I="), "HmacSHA1"));
  static final long PLAIN_PRICE = 0x000000002A512000L;
  static final Date NONCE_TIMESTAMP = new Date(0x0F1E2D3C4B5A6978L);
  static final long NONCE_SERVERID = 0x0123456789ABCDEFL;
  static final byte[] NONCE = new byte[] {
    (byte) 0xE6, (byte) 0x79, (byte) 0xB0, (byte) 0xBE,
    (byte) 0x00, (byte) 0x0C, (byte) 0xD1, (byte) 0x40,
    (byte) 0x01, (byte) 0x23, (byte) 0x45, (byte) 0x67,
    (byte) 0x89, (byte) 0xAB, (byte) 0xCD, (byte) 0xEF,
  };
  static final String CIPHER_PRICE = "5nmwvgAM0UABI0VniavN72_sy3TQFLWhVys-IA";
  static final byte[] PLAIN_IDFA = new byte[]{ 0,1,2,3,4,5,6,7 };
  static final String CIPHER_IDFA = "5nmwvgAM0UABI0VniavN72_tyXf-QJOmeDOf7A";
  static final byte[] PLAIN_ADID = new byte[]{ 0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15 };
  static final String CIPHER_ADID = "5nmwvgAM0UABI0VniavN72_tyXf-QJOmQdL0tmh_fduB2go_";
  static final byte[] PLAIN_HYPERLOCAL = new byte[]{ 7,6,5,4,3,2,1,0 };
  static final String CIPHER_HYPERLOCAL = "5nmwvgAM0UABI0VniavN72jqznD5R5ShB_0m0g";
  static final DoubleClickCrypto baseCrypto = new DoubleClickCrypto(KEYS);
  static final DoubleClickCrypto.Price priceCrypto = new DoubleClickCrypto.Price(KEYS);
  static final DoubleClickCrypto.Idfa idfaCrypto = new DoubleClickCrypto.Idfa(KEYS);
  static final DoubleClickCrypto.AdId adidCrypto = new DoubleClickCrypto.AdId(KEYS);
  static final DoubleClickCrypto.Hyperlocal hyperlocalCrypto =
      new DoubleClickCrypto.Hyperlocal(KEYS);

  @Test
  public void testDoubleClickCryptoException() {
    DoubleClickCryptoException e1 = new DoubleClickCryptoException("1");
    DoubleClickCryptoException e2 = new DoubleClickCryptoException(new NullPointerException());
    assertNotEquals(e1, e2);
  }

  @Test
  public void testEncodeDecode() {
    assertNull(baseCrypto.decode(null));
    assertNull(baseCrypto.encode(null));
    byte[] data = new byte[]{ 1, 2, 3 };
    String encoded = baseCrypto.encode(data);
    assertNotNull(encoded);
    assertArrayEquals(data, baseCrypto.decode(encoded));
  }

  @Test(expected = DoubleClickCryptoException.class)
  public void testEncryptBytes_empty() {
    baseCrypto.encrypt(new byte[0]);
  }

  @Test(expected = DoubleClickCryptoException.class)
  public void testDecryptBytes_empty() {
    baseCrypto.decrypt(new byte[0]);
  }

  @Test
  public void testCreateNonce() {
    assertArrayEquals(NONCE, baseCrypto.createNonce(NONCE_TIMESTAMP, NONCE_SERVERID));
  }

  @Test
  public void testCreateNonce_nullTimestamp() {
    assertArrayEquals(
        new byte[] {
          (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
          (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
          (byte) 0x01, (byte) 0x23, (byte) 0x45, (byte) 0x67,
          (byte) 0x89, (byte) 0xAB, (byte) 0xCD, (byte) 0xEF,
        },
        baseCrypto.createNonce(null, NONCE_SERVERID));
  }

  @Test
  public void testEncrypt_noNonce() {
    baseCrypto.initPlaintext(8, null);
  }

  @Test(expected = DoubleClickCryptoException.class)
  public void testEncrypt_badNonce() {
    baseCrypto.initPlaintext(8, new byte[] { 0 } );
  }

  @Test
  public void testPriceEncrypt() {
    String cryptoData = priceCrypto.encryptPrice(PLAIN_PRICE, NONCE);
    assertEquals(CIPHER_PRICE, cryptoData);
  }

  @Test
  public void testPriceDecrypt() {
    long decryptedPrice = priceCrypto.decryptPrice(CIPHER_PRICE);
    assertEquals(PLAIN_PRICE, decryptedPrice);
  }

  @Test(expected = DoubleClickCryptoException.class)
  public void testPriceDecrypt_badData() {
    long decryptedPrice = priceCrypto.decryptPrice("garbage");
    assertEquals(PLAIN_PRICE, decryptedPrice);
  }

  @Test(expected = DoubleClickCryptoException.class)
  public void testPriceDecrypt_empty() {
    long decryptedPrice = priceCrypto.decryptPrice("");
    assertEquals(PLAIN_PRICE, decryptedPrice);
  }

  @Test(expected = DoubleClickCryptoException.class)
  public void testPriceDecrypt_wrongKeys() {
    new DoubleClickCrypto.Price(
            new DoubleClickCrypto.Keys(KEYS.getIntegrityKey(), KEYS.getEncryptionKey()))
        .decryptPrice(CIPHER_PRICE);
  }

  @Test
  public void testPriceRecrypt() {
    String encrypted = priceCrypto.encryptPrice(PLAIN_PRICE, NONCE);
    assertEquals(PLAIN_PRICE, priceCrypto.decryptPrice(encrypted));
  }

  @Test
  public void testIdfaEncrypt() {
    assertEquals(CIPHER_IDFA, idfaCrypto.encryptIdfa(PLAIN_IDFA, NONCE));
  }

  @Test
  public void testIdfaDecrypt() {
    byte[] decrypted = idfaCrypto.decryptIdfa(CIPHER_IDFA);
    assertArrayEquals(PLAIN_IDFA, decrypted);
  }

  @Test(expected = DoubleClickCryptoException.class)
  public void testIdfaDecrypt_noData() {
    idfaCrypto.decryptIdfa("");
  }

  @Test
  public void testIdfa_dataRange() {
    // Smaller data possible
    idfaCrypto.encryptIdfa(createData(1), NONCE);
    // Bigger data possible: 768 sections = 15360 bytes
    idfaCrypto.encryptIdfa(createData(20 * 768), NONCE);
  }

  @Test(expected = DoubleClickCryptoException.class)
  public void testIdfa_dataTooBig() {
    idfaCrypto.encryptIdfa(createData(20 * 768 + 1), NONCE);
  }

  @Test
  public void testIdfaRecrypt() {
    String encrypted = idfaCrypto.encryptIdfa(PLAIN_IDFA, NONCE);
    assertArrayEquals(PLAIN_IDFA, idfaCrypto.decryptIdfa(encrypted));
  }

  @Test
  public void testAdidEncrypt() {
    assertEquals(CIPHER_ADID, adidCrypto.encryptAdId(PLAIN_ADID, NONCE));
  }

  @Test(expected = DoubleClickCryptoException.class)
  public void testAdidEncrypt_badSize() {
    adidCrypto.encryptAdId(new byte[]{ 0,1,2,3,4,5,6,7 }, NONCE);
  }

  @Test
  public void testAdidDecrypt() {
    byte[] decrypted = adidCrypto.decryptAdId(CIPHER_ADID);
    assertArrayEquals(PLAIN_ADID, decrypted);
  }

  @Test(expected = DoubleClickCryptoException.class)
  public void testAdidDecrypt_noData() {
    adidCrypto.decryptAdId("");
  }

  @Test
  public void testAdidRecrypt() {
    String encrypted = adidCrypto.encryptAdId(PLAIN_ADID, NONCE);
    assertArrayEquals(PLAIN_ADID, adidCrypto.decryptAdId(encrypted));
  }

  @Test
  public void testHyperlocalEncrypt() {
    assertEquals(CIPHER_HYPERLOCAL, hyperlocalCrypto.encryptHyperlocal(PLAIN_HYPERLOCAL, NONCE));
  }

  @Test
  public void testHyperlocalDecrypt() {
    byte[] decrypted = hyperlocalCrypto.decryptHyperlocal(CIPHER_HYPERLOCAL);
    assertArrayEquals(PLAIN_HYPERLOCAL, decrypted);
  }

  @Test(expected = DoubleClickCryptoException.class)
  public void testHyperlocalDecrypt_noData() {
    hyperlocalCrypto.decryptHyperlocal("");
  }

  @Test
  public void testHyperlocal_dataRange() {
    // Smaller data possible
    hyperlocalCrypto.encryptHyperlocal(createData(1), NONCE);
    // Bigger data possible: 768 sections = 15360 bytes
    hyperlocalCrypto.encryptHyperlocal(createData(20 * 768), NONCE);
  }

  @Test(expected = DoubleClickCryptoException.class)
  public void testHyperlocal_dataTooBig() {
    hyperlocalCrypto.encryptHyperlocal(createData(20 * 768 + 1), NONCE);
  }

  @Test
  public void testHyperlocalRecrypt() {
    String encrypted = hyperlocalCrypto.encryptHyperlocal(PLAIN_HYPERLOCAL, NONCE);
    assertArrayEquals(PLAIN_HYPERLOCAL, hyperlocalCrypto.decryptHyperlocal(encrypted));
  }

  private static final byte[] createData(int size) {
    byte[] data = new byte[size];
    for (int i = 0; i < data.length; ++i) {
      data[i] = (byte) (i & 0xFF);
    }
    return data;
  }
}
