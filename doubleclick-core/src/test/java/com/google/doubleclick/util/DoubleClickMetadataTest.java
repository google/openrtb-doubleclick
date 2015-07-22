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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import com.google.doubleclick.TestUtil;
import com.google.doubleclick.util.DoubleClickMetadata.ResourceTransport;
import com.google.doubleclick.util.DoubleClickMetadata.Transport;
import com.google.doubleclick.util.DoubleClickMetadata.URLConnectionTransport;

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

    assertNotNull(metadata.toString());
    assertEquals("1: CreativeAttribute1",
        DoubleClickMetadata.toString(metadata.publisherExcludableCreativeAttributes(), 1));
    assertEquals("1: CreativeAttribute1",
        DoubleClickMetadata.toString(metadata.buyerDeclarableCreativeAttributes(), 1));
    assertEquals("1: CreativeAttribute1",
        DoubleClickMetadata.toString(metadata.allCreativeAttributes(), 1));
    assertEquals("1: Creative won the auction",
        DoubleClickMetadata.toString(metadata.creativeStatusCodes(), 1));
    assertEquals("1: SensitiveCategory1",
        DoubleClickMetadata.toString(metadata.sensitiveCategories(), 1));
    assertEquals("1: RestrictedCategory1",
        DoubleClickMetadata.toString(metadata.restrictedCategories(), 1));
    assertEquals("1: ProductCategory1",
        DoubleClickMetadata.toString(metadata.productCategories(), 1));
    assertEquals("1: NONE",
        DoubleClickMetadata.toString(metadata.agencies(), 1));
    assertEquals("1: Vendor1",
        DoubleClickMetadata.toString(metadata.vendors(), 1));
    assertEquals("1: GDNVendor1",
        DoubleClickMetadata.toString(metadata.gdnVendors(), 1));
    assertEquals("1: GDN",
        DoubleClickMetadata.toString(metadata.sellerNetworks(), 1));
    assertEquals("31: Brand Select",
        DoubleClickMetadata.toString(metadata.siteLists(), 31));
    assertEquals("1: ContentLabel1",
        DoubleClickMetadata.toString(metadata.contentLabels(), 1));
    assertEquals("1: /Vertical1",
        DoubleClickMetadata.toString(metadata.publisherVerticals(), 1));
    assertEquals("9999: <invalid>",
        DoubleClickMetadata.toString(metadata.sensitiveCategories(), 9999));
    assertEquals("United States",
        metadata.geoTargetFor(1023191).canonParent().canonParent().name());
    assertFalse(metadata.geoTargets().isEmpty());
    assertEquals(
        new CityDMARegionValue(624, "West Bend", "Iowa"),
        metadata.dmaRegions().get(new CityDMARegionKey(1016100, "Sioux City, IA")));

    GeoTarget geoTarget1 = metadata.geoTargetFor(GeoTarget.Type.COUNTRY, "United States");
    GeoTarget geoTarget2 = new GeoTarget(
        2840, GeoTarget.Type.COUNTRY, "United States", "United States", "US", null, null);
    GeoTarget geoTarget3 = metadata.geoTargetFor(GeoTarget.Type.COUNTRY, "France");
    TestUtil.testCommonMethods(geoTarget1, geoTarget2, geoTarget3);

    assertEquals(2840, geoTarget1.criteriaId());
    assertEquals("United States", geoTarget1.name());
    assertEquals("United States", geoTarget1.canonicalName());
    assertEquals("US", geoTarget1.countryCode());
    assertEquals(GeoTarget.Type.COUNTRY, geoTarget1.type());
    assertSame(geoTarget1, geoTarget1.getCanonAncestor(GeoTarget.Type.COUNTRY));
    assertNull(geoTarget1.getCanonAncestor(GeoTarget.Type.CITY));
    assertSame(geoTarget1, geoTarget1.getIdAncestor(GeoTarget.Type.COUNTRY));
    assertNull(geoTarget1.getIdAncestor(GeoTarget.Type.CITY));
    TestUtil.testCommonEnum(GeoTarget.Type.values());

    CountryCodes country1 = metadata.countryCodes().get("US");
    CountryCodes country2 = new CountryCodes(840, "US", "USA");
    CountryCodes country3 = new CountryCodes(840, "US", "USB");
    TestUtil.testCommonMethods(country1, country2, country3);
    assertSame(country1, metadata.countryCodes().get("USA"));
    assertSame(country1, metadata.countryCodes().get(840));
    assertEquals(840, country1.numeric());
    assertEquals("US", country1.alpha2());
    assertEquals("USA", country1.alpha3());

    // https://github.com/google/openrtb-doubleclick/issues/28
    GeoTarget postalTarget = metadata.geoTargetFor(9012102);
    assertNull(postalTarget.getCanonAncestor(GeoTarget.Type.CITY));
    assertEquals("Tampa", postalTarget.getIdAncestor(GeoTarget.Type.CITY).name());

    GeoTarget.CanonicalKey canKey1 = new GeoTarget.CanonicalKey(GeoTarget.Type.CITY, "A");
    GeoTarget.CanonicalKey canKey2 = new GeoTarget.CanonicalKey(GeoTarget.Type.CITY, "A");
    GeoTarget.CanonicalKey canKey3 = new GeoTarget.CanonicalKey(GeoTarget.Type.CITY, "B");
    TestUtil.testCommonMethods(canKey1, canKey2, canKey3);
  }

  @Test
  public void testResourceTransport() throws IOException {
    ResourceTransport transport = new ResourceTransport();
    try (InputStream is = transport.open("/adx-rtb-dictionaries/countries.txt")) {
    }
  }

  @Test(expected = IOException.class)
  public void testResourceTransport_notFound() throws IOException {
    ResourceTransport transport = new ResourceTransport();
    try (InputStream is = transport.open("/adx-rtb-dictionaries/doesnt.exist")) {
    }
  }

  @Test
  public void testJavaNetTransport() throws IOException {
    Transport transport = new URLConnectionTransport();
    try (InputStream is = transport.open(
        "https://storage.googleapis.com/adx-rtb-dictionaries/vendors.txt")) {
    }
  }

  @Test(expected = IOException.class)
  public void testJavaNetTransport_notFound() throws IOException {
    Transport transport = new URLConnectionTransport();
    try (InputStream is = transport.open(
        "https://storage.googleapis.com/adx-rtb-dictionaries/doesnt.exist")) {
    }
  }

  @Test
  public void testCityDMARegion() {
    CityDMARegionKey key1 = new CityDMARegionKey(1, "a");
    CityDMARegionKey key2 = new CityDMARegionKey(1, "a");
    CityDMARegionKey key3 = new CityDMARegionKey(1, "b");
    TestUtil.testCommonMethods(key1, key2, key3);
    assertEquals(1, key1.criteriaId());
    assertEquals("a", key1.regionName());
    CityDMARegionValue value1 = new CityDMARegionValue(1, "a", "b");
    CityDMARegionValue value2 = new CityDMARegionValue(1, "a", "b");
    CityDMARegionValue value3 = new CityDMARegionValue(1, "a", "c");
    assertEquals(1, value1.regionCode());
    assertEquals("a", value1.city());
    assertEquals("b", value1.state());
    TestUtil.testCommonMethods(value1, value2, value3);
  }
}
