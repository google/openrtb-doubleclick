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

package com.google.doubleclick.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import com.google.doubleclick.TestUtil;
import com.google.doubleclick.util.DoubleClickMetadata.CountryCodes;
import com.google.doubleclick.util.DoubleClickMetadata.GeoTarget;
import com.google.doubleclick.util.DoubleClickMetadata.GeoTarget.TargetType;
import com.google.doubleclick.util.DoubleClickMetadata.ResourceTransport;

import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;

/**
 * Tests for  {@link DoubleClickMetadata}.
 */
public class DoubleClickMetadataTest {

  @Test
  public void test() {
    DoubleClickMetadata metadata = new DoubleClickMetadata(
        new DoubleClickMetadata.ResourceTransport());

    assertEquals("1: CreativeAttribute1",
        DoubleClickMetadata.toString(metadata.getPublisherExcludableCreativeAttributes(), 1));
    assertEquals("1: CreativeAttribute1",
        DoubleClickMetadata.toString(metadata.getBuyerDeclarableCreativeAttributes(), 1));
    assertEquals("1: CreativeAttribute1",
        DoubleClickMetadata.toString(metadata.getAllCreativeAttributes(), 1));
    assertEquals("1: SensitiveCategory1",
        DoubleClickMetadata.toString(metadata.getSensitiveCategories(), 1));
    assertEquals("1: RestrictedCategory1",
        DoubleClickMetadata.toString(metadata.getRestrictedCategories(), 1));
    assertEquals("1: ProductCategory1",
        DoubleClickMetadata.toString(metadata.getProductCategories(), 1));
    assertEquals("1: Vendor1",
        DoubleClickMetadata.toString(metadata.getVendors(), 1));
    assertEquals("1: GDNVendor1",
        DoubleClickMetadata.toString(metadata.getGdnVendors(), 1));
    assertEquals("1: GDN",
        DoubleClickMetadata.toString(metadata.getSellerNetworks(), 1));
    assertEquals("9999: <invalid>",
        DoubleClickMetadata.toString(metadata.getSensitiveCategories(), 9999));
    assertEquals("United States", metadata.getGeoTarget(1023191).getParent().getParent().getName());
    assertFalse(metadata.getTargetsByCriteriaId().isEmpty());

    GeoTarget geoTarget1 = metadata.getGeoTarget(TargetType.COUNTRY, "United States");
    GeoTarget geoTarget2 =
        new GeoTarget(2840, "United States", "United States", null, "US", TargetType.COUNTRY);
    GeoTarget geoTarget3 = metadata.getGeoTarget(TargetType.COUNTRY, "France");
    TestUtil.testCommonMethods(geoTarget1, geoTarget2, geoTarget3);

    assertEquals(2840, geoTarget1.getCriteriaId());
    assertEquals("United States", geoTarget1.getName());
    assertEquals("United States", geoTarget1.getCanonicalName());
    assertEquals("US", geoTarget1.getCountryCode());
    assertEquals(TargetType.COUNTRY, geoTarget1.getTargetType());
    assertSame(geoTarget1, geoTarget1.getAncestor(TargetType.COUNTRY));
    assertNull(geoTarget1.getAncestor(TargetType.CITY));
    TestUtil.testCommonEnum(TargetType.values());

    CountryCodes country1 = metadata.getCountryCodes().get("US");
    CountryCodes country2 = new CountryCodes(840, "US", "USA");
    CountryCodes country3 = new CountryCodes(840, "US", "USB");
    TestUtil.testCommonMethods(country1, country2, country3);
    assertSame(country1, metadata.getCountryCodes().get("USA"));
    assertSame(country1, metadata.getCountryCodes().get(840));
    assertEquals(840, country1.getNumeric());
    assertEquals("US", country1.getAlpha2());
    assertEquals("USA", country1.getAlpha3());

    GeoTarget.CanonicalKey canKey1 = new GeoTarget.CanonicalKey(TargetType.CITY, "A");
    GeoTarget.CanonicalKey canKey2 = new GeoTarget.CanonicalKey(TargetType.CITY, "A");
    GeoTarget.CanonicalKey canKey3 = new GeoTarget.CanonicalKey(TargetType.CITY, "B");
    TestUtil.testCommonMethods(canKey1, canKey2, canKey3);
  }

  @Test
  public void testTransport() throws IOException {
    ResourceTransport transport = new ResourceTransport();
    transport.setResourceName("/adx-openrtb/countries.txt");
    assertEquals("/adx-openrtb/countries.txt", transport.getResourceName());
    try (InputStream is = transport.open(null)) {}
  }
}
