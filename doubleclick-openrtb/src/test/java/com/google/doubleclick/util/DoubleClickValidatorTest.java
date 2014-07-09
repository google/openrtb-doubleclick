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

import static java.util.Arrays.asList;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.Iterables;
import com.google.doubleclick.DcExt;
import com.google.doubleclick.Doubleclick.BidRequest.AdSlot;
import com.google.doubleclick.Doubleclick.BidRequest.AdSlot.MatchingAdData;
import com.google.doubleclick.Doubleclick.BidResponse.Ad;
import com.google.openrtb.OpenRtb.BidRequest;
import com.google.openrtb.OpenRtb.BidRequest.Impression;
import com.google.openrtb.OpenRtb.BidRequest.Impression.Banner;
import com.google.openrtb.OpenRtb.BidResponse;
import com.google.openrtb.OpenRtb.BidResponse.SeatBid;
import com.google.openrtb.OpenRtb.BidResponse.SeatBid.Bid;
import com.google.openrtb.util.OpenRtbUtils;

import com.codahale.metrics.MetricRegistry;

import org.junit.Before;
import org.junit.Test;

/**
 * Tests for {@link DoubleClickValidator}.
 */
public class DoubleClickValidatorTest {
  private static BidRequest request = BidRequest.newBuilder()
      .setId("1")
      .addImp(Impression.newBuilder()
          .setId("1")
          .setBanner(Banner.newBuilder().setId("1"))
          .setExtension(DcExt.adSlot, AdSlot.newBuilder()
              .setId(1)
              .addWidth(200)
              .addHeight(50)
              .addMatchingAdData(MatchingAdData.newBuilder().setAdgroupId(10))
              .addExcludedAttribute(1)
              .addExcludedProductCategory(1)
              .addExcludedSensitiveCategory(1)
              .addAllowedVendorType(1)
              .addAllowedRestrictedCategory(1).build()))
      .build();

  private MetricRegistry metricRegistry;
  private DoubleClickMetadata metadata = new DoubleClickMetadata(
      new DoubleClickMetadata.ResourceTransport());
  private DoubleClickValidator validator;

  @Before
  public void setUp() {
    metricRegistry = new MetricRegistry();
    validator = new DoubleClickValidator(metricRegistry, metadata);
  }

  @Test
  public void testGoodAttrs() {
    BidResponse.Builder response = testResponse(testBid().setExtension(DcExt.ad, Ad.newBuilder()
        .addAttribute(2)
        .addCategory(2)
        .addVendorType(1)
        .addRestrictedCategory(1)
        .build()));
    validator.validate(request, response);
    assertFalse(Iterables.isEmpty(OpenRtbUtils.bids(response)));
  }

  @Test
  public void testNoAttrs() {
    BidResponse.Builder response = testResponse(testBid()
        .setExtension(DcExt.ad, Ad.newBuilder().build()));
    validator.validate(request, response);
    assertFalse(Iterables.isEmpty(OpenRtbUtils.bids(response)));
  }

  @Test
  public void testNoAdExt() {
    BidResponse.Builder response = testResponse(testBid());
    validator.validate(request, response);
    assertFalse(Iterables.isEmpty(OpenRtbUtils.bids(response)));
  }

  @Test
  public void testExcludedAttribute() {
    BidResponse.Builder response = testResponse(testBid().setExtension(DcExt.ad, Ad.newBuilder()
        .addAllAttribute(asList(1, 2))
        .build()));
    validator.validate(request, response);
    assertTrue(Iterables.isEmpty(OpenRtbUtils.bids(response)));
  }

  @Test
  public void testExcludedProductCategory() {
    BidResponse.Builder response = testResponse(testBid().setExtension(DcExt.ad, Ad.newBuilder()
        .addAllCategory(asList(1, 2))
        .build()));
    validator.validate(request, response);
    assertTrue(Iterables.isEmpty(OpenRtbUtils.bids(response)));
  }

  @Test
  public void testExcludedSensitiveCategory() {
    BidResponse.Builder response = testResponse(testBid().setExtension(DcExt.ad, Ad.newBuilder()
        .addAllCategory(asList(10, 11, 12, 4))
        .build()));
    validator.validate(request, response);
    assertTrue(Iterables.isEmpty(OpenRtbUtils.bids(response)));
  }

  @Test
  public void testNotAllowedVendor() {
    BidResponse.Builder response = testResponse(testBid().setExtension(DcExt.ad, Ad.newBuilder()
        .addAllVendorType(asList(2, 3))
        .build()));
    validator.validate(request, response);
    assertTrue(Iterables.isEmpty(OpenRtbUtils.bids(response)));
  }

  @Test
  public void testNotAllowedRestrictedCategory() {
    BidResponse.Builder response = testResponse(testBid().setExtension(DcExt.ad, Ad.newBuilder()
        .addAllRestrictedCategory(asList(2, 3))
        .build()));
    validator.validate(request, response);
    assertTrue(Iterables.isEmpty(OpenRtbUtils.bids(response)));
  }

  @Test
  public void testFlashless() {
    BidRequest request = BidRequest.newBuilder()
        .setId("1")
        .addImp(Impression.newBuilder()
            .setId("1")
            .setBanner(Banner.newBuilder().setId("1"))
            .setExtension(DcExt.adSlot, AdSlot.newBuilder()
                .setId(1)
                .addWidth(200)
                .addHeight(50)
                .addMatchingAdData(MatchingAdData.newBuilder().setAdgroupId(10))
                .addExcludedAttribute(DoubleClickValidator.CREATIVE_FLASH).build()))
        .build();

    BidResponse.Builder goodResp = testResponse(testBid().setExtension(DcExt.ad, Ad.newBuilder()
        .addAttribute(DoubleClickValidator.CREATIVE_NON_FLASH)
        .build()));
    validator.validate(request, goodResp);
    assertFalse(Iterables.isEmpty(OpenRtbUtils.bids(goodResp)));

    BidResponse.Builder badResp1 = testResponse(testBid().setExtension(DcExt.ad, Ad.newBuilder()
        .build()));
    validator.validate(request, badResp1);
    assertTrue(Iterables.isEmpty(OpenRtbUtils.bids(badResp1)));

    BidResponse.Builder badResp2 = testResponse(testBid());
    validator.validate(request, badResp2);
    assertTrue(Iterables.isEmpty(OpenRtbUtils.bids(badResp2)));
  }

  @Test
  public void testNoImp() {
    BidResponse.Builder response = testResponse(testBid().setImpid("2"));
    validator.validate(request, response);
    assertTrue(Iterables.isEmpty(OpenRtbUtils.bids(response)));
  }

  private static BidResponse.Builder testResponse(Bid.Builder bid) {
    return BidResponse.newBuilder().addSeatbid(SeatBid.newBuilder().addBid(bid));
  }

  private static Bid.Builder testBid() {
    return Bid.newBuilder()
        .setId("1")
        .setImpid("1")
        .setPrice(100.0f);
  }
}
