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

import static com.google.common.truth.Truth.assertThat;

import com.google.doubleclick.TestUtil;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protos.adx.NetworkBid.BidRequest.Hyperlocal;
import com.google.protos.adx.NetworkBid.BidRequest.HyperlocalSet;

import org.junit.Test;

import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.SignatureException;
import java.util.Base64;
import java.util.Date;

import javax.crypto.spec.SecretKeySpec;

/**
 * Tests for {@link DoubleClickCrypto}.
 */
public class DoubleClickCryptoTest {

  static final DoubleClickCrypto.Keys KEYS = createKeys();
  static final double PLAIN_PRICE = 1.2;
  static final Date INITV_TIMESTAMP = new Date(0xE679B0BE000CD140L);
  static final long INITV_SERVERID = 0x0123456789ABCDEFL;
  static final byte[] INITV = new byte[] {
    (byte) 0xE6, (byte) 0x79, (byte) 0xB0, (byte) 0xBE,
    (byte) 0x00, (byte) 0x0C, (byte) 0xD1, (byte) 0x40,
    (byte) 0x01, (byte) 0x23, (byte) 0x45, (byte) 0x67,
    (byte) 0x89, (byte) 0xAB, (byte) 0xCD, (byte) 0xEF,
  };
  static final String CIPHER_PRICE = "5nmwvgAM0UABI0VniavN72_sy3T6V9ohlpvOpA==";
  static final byte[] PLAIN_IDFA = new byte[]{ 0,1,2,3,4,5,6,7 };
  static final String CIPHER_IDFA = "5nmwvgAM0UABI0VniavN72_tyXf-QJOmeDOf7A==";
  static final byte[] PLAIN_ADID = new byte[]{ 0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15 };
  static final byte[] CIPHER_ADID = new byte[]{
    (byte) 0xE6, (byte) 0x79, (byte) 0xB0, (byte) 0xBE,
    (byte) 0x00, (byte) 0x0C, (byte) 0xD1, (byte) 0x40,
    (byte) 0x01, (byte) 0x23, (byte) 0x45, (byte) 0x67,
    (byte) 0x89, (byte) 0xAB, (byte) 0xCD, (byte) 0xEF,
    (byte) 0x6F, (byte) 0xED, (byte) 0xC9, (byte) 0x77,
    (byte) 0xFE, (byte) 0x40, (byte) 0x93, (byte) 0xA6,
    (byte) 0x41, (byte) 0xD2, (byte) 0xF4, (byte) 0xB6,
    (byte) 0x68, (byte) 0x7F, (byte) 0x7D, (byte) 0xDB,
    (byte) 0x81, (byte) 0xDA, (byte) 0x0A, (byte) 0x3F,
  };
  static final byte[] PLAIN_HYPERLOCAL = createHyperlocal(1);
  static final byte[] CIPHER_HYPERLOCAL = new byte[]{
    (byte) 0xE6, (byte) 0x79, (byte) 0xB0, (byte) 0xBE,
    (byte) 0x00, (byte) 0x0C, (byte) 0xD1, (byte) 0x40,
    (byte) 0x01, (byte) 0x23, (byte) 0x45, (byte) 0x67,
    (byte) 0x89, (byte) 0xAB, (byte) 0xCD, (byte) 0xEF,
    (byte) 0x7D, (byte) 0xE6, (byte) 0xC6, (byte) 0x74,
    (byte) 0xFA, (byte) 0x71, (byte) 0xD7, (byte) 0xB4,
    (byte) 0x49, (byte) 0xDB, (byte) 0xCA, (byte) 0xFF,
    (byte) 0x68, (byte) 0xB1, (byte) 0xC9, (byte) 0x1A,
  };
  static final DoubleClickCrypto baseCrypto = new DoubleClickCrypto(KEYS);
  static final DoubleClickCrypto.Price priceCrypto = new DoubleClickCrypto.Price(KEYS);
  static final DoubleClickCrypto.Idfa idfaCrypto = new DoubleClickCrypto.Idfa(KEYS);
  static final DoubleClickCrypto.AdId adidCrypto = new DoubleClickCrypto.AdId(KEYS);
  static final DoubleClickCrypto.Hyperlocal hyperlocalCrypto =
      new DoubleClickCrypto.Hyperlocal(KEYS);

  private static DoubleClickCrypto.Keys createKeys() {
    try {
      return new DoubleClickCrypto.Keys(
          new SecretKeySpec(Base64.getUrlDecoder().decode(
              "sIxwz7yw62yrfoLGt12lIHKuYrK_S5kLuApI2BQe7Ac="), "HmacSHA1"),
          new SecretKeySpec(Base64.getUrlDecoder().decode(
              "v3fsVcMBMMHYzRhi7SpM0sdqwzvAxM6KPTu9OtVod5I="), "HmacSHA1"));
    } catch (InvalidKeyException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  // DoubleClickCrypto

  @Test
  public void testEncodeDecode() {
    assertThat(baseCrypto.decode(null)).isNull();
    assertThat(baseCrypto.encode(null)).isNull();
    byte[] data = new byte[]{ 1, 2, 3 };
    String encoded = baseCrypto.encode(data);
    assertThat(encoded).isNotNull();
    assertThat(baseCrypto.decode(encoded)).isEqualTo(data);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testEncryptBytes_empty() {
    baseCrypto.encrypt(new byte[0]);
  }

  @Test
  public void testEncryptBytes_noData() {
    assertThat(baseCrypto.encrypt(new byte[DoubleClickCrypto.OVERHEAD_SIZE]))
        .hasLength(DoubleClickCrypto.OVERHEAD_SIZE);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testDecryptBytes_empty() throws GeneralSecurityException {
    baseCrypto.decrypt(new byte[0]);
  }

  @Test
  public void testDecryptBytes_noData() throws GeneralSecurityException {
    baseCrypto.decrypt(baseCrypto.encrypt(new byte[DoubleClickCrypto.OVERHEAD_SIZE]));
  }

  @Test
  public void testCreateNonce() {
    assertThat(baseCrypto.createInitVector(INITV_TIMESTAMP, INITV_SERVERID)).isEqualTo(INITV);
    assertThat(baseCrypto.getTimestamp(INITV)).isEqualTo(INITV_TIMESTAMP);
    assertThat(baseCrypto.getServerId(INITV)).isEqualTo(INITV_SERVERID);
  }

  @Test
  public void testKeys() throws InvalidKeyException {
    SecretKeySpec key1 = new SecretKeySpec(new byte[]{0}, "HmacSHA1");
    SecretKeySpec key2 = new SecretKeySpec(new byte[]{1}, "HmacSHA1");
    DoubleClickCrypto.Keys keys1 = new DoubleClickCrypto.Keys(key1, key1);
    DoubleClickCrypto.Keys keys2 = new DoubleClickCrypto.Keys(key1, key1);
    DoubleClickCrypto.Keys keys3 = new DoubleClickCrypto.Keys(key1, key2);
    TestUtil.testCommonMethods(keys1, keys2, keys3);
  }

  @Test
  public void testCreateNonce_nullTimestamp() {
    assertThat(baseCrypto.createInitVector(null, INITV_SERVERID))
        .isEqualTo(new byte[] {
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x01, (byte) 0x23, (byte) 0x45, (byte) 0x67,
            (byte) 0x89, (byte) 0xAB, (byte) 0xCD, (byte) 0xEF,
        });
  }

  @Test
  public void testEncrypt_noNonce() {
    baseCrypto.initPlainData(8, null);
  }

  @Test
  public void testEncrypt_nonceSize() {
    baseCrypto.initPlainData(8, new byte[0]);
    baseCrypto.initPlainData(8, new byte[DoubleClickCrypto.INITV_SIZE]);
    baseCrypto.initPlainData(8, new byte[DoubleClickCrypto.INITV_SIZE * 10]);
  }

  // DoubleClickCrypto.Price

  @Test
  public void testPriceEncrypt() {
    String cryptoData = priceCrypto.encodePriceValue(PLAIN_PRICE, INITV);
    assertThat(cryptoData).isEqualTo(CIPHER_PRICE);
  }

  @Test
  public void testPriceDecrypt() throws SignatureException {
    double decryptedPrice = priceCrypto.decodePriceValue(CIPHER_PRICE);
    assertThat(decryptedPrice).isWithin(1e-9).of(PLAIN_PRICE);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testPriceDecrypt_badData() throws SignatureException {
    priceCrypto.decodePriceMicros("garbage");
  }

  @Test(expected = IllegalArgumentException.class)
  public void testPriceDecrypt_empty() throws SignatureException {
    priceCrypto.decodePriceMicros("");
  }

  @Test(expected = SignatureException.class)
  public void testPriceDecrypt_wrongKeys() throws InvalidKeyException, SignatureException {
    new DoubleClickCrypto.Price(
            new DoubleClickCrypto.Keys(KEYS.getIntegrityKey(), KEYS.getEncryptionKey()))
        .decodePriceMicros(CIPHER_PRICE);
  }

  @Test
  public void testPriceRecrypt() throws SignatureException {
    String encrypted = priceCrypto.encodePriceValue(PLAIN_PRICE, INITV);
    assertThat(priceCrypto.decodePriceValue(encrypted)).isWithin(1e-9).of(PLAIN_PRICE);
  }

  // DoubleClickCrypto.Idfa

  @Test
  public void testIdfaEncrypt() {
    assertThat(idfaCrypto.encodeIdfa(PLAIN_IDFA, INITV)).isEqualTo(CIPHER_IDFA);
  }

  @Test
  public void testIdfaDecrypt() throws SignatureException {
    byte[] decrypted = idfaCrypto.decodeIdfa(CIPHER_IDFA);
    assertThat(decrypted).isEqualTo(PLAIN_IDFA);
  }

  @Test
  public void testIdfa_dataRange() {
    // Smallest data possible
    idfaCrypto.encryptIdfa(createData(1), INITV);
    // Biggest data possible: 769 sections = 15380 bytes
    idfaCrypto.encryptIdfa(createData(20 * 769), INITV);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testIdfa_dataTooBig() {
    idfaCrypto.encryptIdfa(createData(20 * 769 + 1), INITV);
  }

  @Test
  public void testIdfaRecrypt() throws SignatureException {
    String encrypted = idfaCrypto.encodeIdfa(PLAIN_IDFA, INITV);
    assertThat(idfaCrypto.decodeIdfa(encrypted)).isEqualTo(PLAIN_IDFA);
  }

  // DoubleClickCrypto.AdId

  @Test
  public void testAdidEncrypt() {
    assertThat(adidCrypto.encryptAdId(PLAIN_ADID, INITV)).isEqualTo(CIPHER_ADID);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testAdidEncrypt_badSize() {
    adidCrypto.encryptAdId(new byte[1], INITV);
  }

  @Test
  public void testAdidDecrypt() throws SignatureException {
    byte[] decrypted = adidCrypto.decryptAdId(CIPHER_ADID);
    assertThat(decrypted).isEqualTo(PLAIN_ADID);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testAdidDecrypt_badSize() throws SignatureException {
    adidCrypto.decryptAdId(new byte[1]);
  }

  @Test
  public void testAdidRecrypt() throws SignatureException {
    byte[] encrypted = adidCrypto.encryptAdId(PLAIN_ADID, INITV);
    assertThat(adidCrypto.decryptAdId(encrypted)).isEqualTo(PLAIN_ADID);
  }

  // DoubleClickCrypto.Hyperlocal

  @Test
  public void testHyperlocalEncrypt() {
    assertThat(hyperlocalCrypto.encryptHyperlocal(PLAIN_HYPERLOCAL, INITV))
        .isEqualTo(CIPHER_HYPERLOCAL);
  }

  @Test
  public void testHyperlocalDecrypt() throws InvalidProtocolBufferException, SignatureException {
    byte[] decrypted = hyperlocalCrypto.decryptHyperlocal(CIPHER_HYPERLOCAL);
    HyperlocalSet.parseFrom(decrypted);
    assertThat(decrypted).isEqualTo(PLAIN_HYPERLOCAL);
  }

  @Test
  public void testHyperlocal_dataRange() {
    // Smallest data possible. Note: createHyperlocal(0) would fail because
    // an empty protobuf message serializes to byte[0], which fails to encrypt.
    hyperlocalCrypto.encryptHyperlocal(createHyperlocal(1), INITV);
    // Biggest data possible: 15362 bytes, max is 15380 (768*20)
    hyperlocalCrypto.encryptHyperlocal(createHyperlocal(308), INITV);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testHyperlocal_dataTooBig() {
    // 15412 bytes, max is 15380 (768*20)
    hyperlocalCrypto.encryptHyperlocal(createHyperlocal(309), INITV);
  }

  @Test
  public void testHyperlocalRecrypt() throws SignatureException {
    byte[] encrypted = hyperlocalCrypto.encryptHyperlocal(PLAIN_HYPERLOCAL, INITV);
    assertThat(hyperlocalCrypto.decryptHyperlocal(encrypted)).isEqualTo(PLAIN_HYPERLOCAL);
  }

  // Utilities

  static final byte[] createData(int size) {
    byte[] data = new byte[size];
    for (int i = 0; i < data.length; ++i) {
      data[i] = (byte) (i & 0xFF);
    }
    return data;
  }

  static byte[] createHyperlocal(int size) {
    HyperlocalSet.Builder ret = HyperlocalSet.newBuilder();
    if (size != 0) {
      ret.setCenterPoint(Hyperlocal.Point.newBuilder().setLatitude(45).setLongitude(45));
      for (int i = 1; i < size; ++i) {
        ret.addHyperlocal(Hyperlocal.newBuilder()
            .addCorners(Hyperlocal.Point.newBuilder().setLatitude(100).setLongitude(100))
            .addCorners(Hyperlocal.Point.newBuilder().setLatitude(100).setLongitude(101))
            .addCorners(Hyperlocal.Point.newBuilder().setLatitude(101).setLongitude(101))
            .addCorners(Hyperlocal.Point.newBuilder().setLatitude(101).setLongitude(100)));
      }
    }
    return ret.build().toByteArray();
  }
}
