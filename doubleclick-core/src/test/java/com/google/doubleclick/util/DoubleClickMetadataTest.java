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

import static com.google.common.truth.Truth.assertThat;

import com.google.doubleclick.TestUtil;
import com.google.doubleclick.util.DoubleClickMetadata.ResourceTransport;
import com.google.doubleclick.util.DoubleClickMetadata.Transport;
import com.google.doubleclick.util.DoubleClickMetadata.URLConnectionTransport;
import java.io.IOException;
import java.io.InputStream;
import org.junit.Test;

/**
 * Tests for  {@link DoubleClickMetadata}.
 */
public class DoubleClickMetadataTest {

  @Test
  public void test() {
    DoubleClickMetadata metadata = new DoubleClickMetadata(
        new DoubleClickMetadata.ResourceTransport());

    assertThat(metadata.toString()).isNotNull();
    assertThat(DoubleClickMetadata.toString(metadata.publisherExcludableCreativeAttributes(), 1))
        .isEqualTo("1: CreativeAttribute1");
    assertThat(DoubleClickMetadata.toString(metadata.buyerDeclarableCreativeAttributes(), 1))
        .isEqualTo("1: CreativeAttribute1");
    assertThat(DoubleClickMetadata.toString(metadata.allCreativeAttributes(), 1))
        .isEqualTo("1: CreativeAttribute1");
    assertThat(DoubleClickMetadata.toString(metadata.creativeStatusCodes(), 1))
        .isEqualTo("1: Creative won the auction");
    assertThat(DoubleClickMetadata.toString(metadata.sensitiveCategories(), 1))
        .isEqualTo("1: SensitiveCategory1");
    assertThat(DoubleClickMetadata.toString(metadata.restrictedCategories(), 1))
        .isEqualTo("1: RestrictedCategory1");
    assertThat(DoubleClickMetadata.toString(metadata.productCategories(), 1))
        .isEqualTo("1: ProductCategory1");
    assertThat(DoubleClickMetadata.toString(metadata.agencies(), 1))
        .isEqualTo("1: NONE");
    assertThat(DoubleClickMetadata.toString(metadata.vendors(), 1))
        .isEqualTo("1: Vendor1");
    assertThat(DoubleClickMetadata.toString(metadata.sellerNetworks(), 1))
        .isEqualTo("1: GDN");
    assertThat(DoubleClickMetadata.toString(metadata.siteLists(), 31))
        .isEqualTo("31: Brand Select");
    assertThat(DoubleClickMetadata.toString(metadata.contentLabels(), 1))
        .isEqualTo("1: ContentLabel1");
    assertThat(DoubleClickMetadata.toString(metadata.publisherVerticals(), 1))
        .isEqualTo("1: /Vertical1");
    assertThat(DoubleClickMetadata.toString(metadata.sensitiveCategories(), 9999))
        .isEqualTo("9999: <invalid>");
    assertThat(metadata.geoTargetFor(1023191).canonParent().canonParent().name())
        .isEqualTo("United States");
    assertThat(metadata.geoTargets().isEmpty()).isFalse();
    assertThat(metadata.mobileCarriers().get(70092)).isEqualTo("Verizon");

    GeoTarget geoTarget1 = metadata.geoTargetFor(GeoTarget.Type.COUNTRY, "United States");
    GeoTarget geoTarget2 = new GeoTarget(
        2840, GeoTarget.Type.COUNTRY, "United States", "United States", "US");
    GeoTarget geoTarget3 = metadata.geoTargetFor(GeoTarget.Type.COUNTRY, "France");
    TestUtil.testCommonMethods(geoTarget1, geoTarget2, geoTarget3);

    assertThat(geoTarget1.criteriaId()).isEqualTo(2840);
    assertThat(geoTarget1.name()).isEqualTo("United States");
    assertThat(geoTarget1.canonicalName()).isEqualTo("United States");
    assertThat(geoTarget1.countryCode()).isEqualTo("US");
    assertThat(geoTarget1.type()).isSameInstanceAs(GeoTarget.Type.COUNTRY);
    assertThat(geoTarget1.getCanonAncestor(GeoTarget.Type.COUNTRY)).isSameInstanceAs(geoTarget1);
    assertThat(geoTarget1.getCanonAncestor(GeoTarget.Type.CITY)).isNull();
    assertThat(geoTarget1.getIdAncestor(GeoTarget.Type.COUNTRY)).isSameInstanceAs(geoTarget1);
    assertThat(geoTarget1.getIdAncestor(GeoTarget.Type.CITY)).isNull();
    TestUtil.testCommonEnum(GeoTarget.Type.values());

    CountryCodes country1 = metadata.countryCodes().get("US");
    CountryCodes country2 = new CountryCodes(840, "US", "USA");
    CountryCodes country3 = new CountryCodes(840, "US", "USB");
    TestUtil.testCommonMethods(country1, country2, country3);
    assertThat(metadata.countryCodes().get("USA")).isSameInstanceAs(country1);
    assertThat(metadata.countryCodes().get(840)).isSameInstanceAs(country1);
    assertThat(country1.numeric()).isEqualTo(840);
    assertThat(country1.alpha2()).isEqualTo("US");
    assertThat(country1.alpha3()).isEqualTo("USA");

    // https://github.com/google/openrtb-doubleclick/issues/28
    GeoTarget postalTarget = metadata.geoTargetFor(9012102);
    assertThat(postalTarget.getCanonAncestor(GeoTarget.Type.CITY)).isNull();
    assertThat(postalTarget.getIdAncestor(GeoTarget.Type.CITY).name()).isEqualTo("Tampa");

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
}
