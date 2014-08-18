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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.doubleclick.Doubleclick.BidRequest;
import com.google.doubleclick.Doubleclick.BidRequest.AdSlot.MatchingAdData;
import com.google.doubleclick.Doubleclick.BidRequest.AdSlot.MatchingAdData.DirectDeal;
import com.google.doubleclick.Doubleclick.BidResponse;
import com.google.protobuf.ByteString;

import com.codahale.metrics.MetricRegistry;

import org.junit.Before;
import org.junit.Test;

/**
 * Tests for {@link DoubleClickValidator}.
 */
public class DoubleClickValidatorTest {
  private static BidRequest request = BidRequest.newBuilder()
      .setId(ByteString.copyFromUtf8("0"))
      .addAdslot(BidRequest.AdSlot.newBuilder()
          .setId(1)
          .setId(1)
          .addWidth(200)
          .addHeight(50)
          .addMatchingAdData(MatchingAdData.newBuilder()
              .setAdgroupId(10)
              .addDirectDeal(DirectDeal.newBuilder()
                  .setDirectDealId(1)))
          .addExcludedAttribute(1)
          .addExcludedProductCategory(1)
          .addExcludedSensitiveCategory(1)
          .addAllowedVendorType(1)
          .addAllowedRestrictedCategory(1))
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
    BidResponse.Builder response = BidResponse.newBuilder().addAd(testBid()
        .addAttribute(2)
        .addCategory(2)
        .addVendorType(1)
        .addRestrictedCategory(1));
    validator.validate(request, response);
    assertFalse(Iterables.isEmpty(bids(response)));
  }

  @Test
  public void testNoAttrs() {
    BidResponse.Builder response = BidResponse.newBuilder().addAd(testBid());
    validator.validate(request, response);
    assertFalse(Iterables.isEmpty(bids(response)));
  }

  @Test
  public void testExcludedAttribute() {
    BidResponse.Builder response = BidResponse.newBuilder().addAd(testBid()
        .addAllAttribute(asList(1, 2)));
    validator.validate(request, response);
    assertTrue(Iterables.isEmpty(bids(response)));
  }

  @Test
  public void testExcludedProductCategory() {
    BidResponse.Builder response = BidResponse.newBuilder().addAd(testBid()
        .addAllCategory(asList(1, 2)));
    validator.validate(request, response);
    assertTrue(Iterables.isEmpty(bids(response)));
  }

  @Test
  public void testExcludedSensitiveCategory() {
    BidResponse.Builder response = BidResponse.newBuilder().addAd(testBid()
        .addAllCategory(asList(10, 11, 12, 4)));
    validator.validate(request, response);
    assertTrue(Iterables.isEmpty(bids(response)));
  }

  @Test
  public void testNotAllowedVendor() {
    BidResponse.Builder response = BidResponse.newBuilder().addAd(testBid()
        .addAllVendorType(asList(2, 3)));
    validator.validate(request, response);
    assertTrue(Iterables.isEmpty(bids(response)));
    BidRequest gdnRequest = request.toBuilder().setSellerNetworkId(1 /* GDN */).build();
    response = BidResponse.newBuilder().addAd(testBid()
        .addAllVendorType(asList(2, 3)));
    validator.validate(gdnRequest, response);
    assertTrue(Iterables.isEmpty(bids(response)));
  }

  @Test
  public void testNotAllowedRestrictedCategory() {
    BidResponse.Builder response = BidResponse.newBuilder().addAd(testBid()
        .addAllRestrictedCategory(asList(2, 3)));
    validator.validate(request, response);
    assertTrue(Iterables.isEmpty(bids(response)));
  }

  @Test
  public void testFlashless() {
    BidRequest request = BidRequest.newBuilder()
        .setId(ByteString.copyFromUtf8("0"))
        .addAdslot(BidRequest.AdSlot.newBuilder()
            .setId(1)
            .addWidth(200)
            .addHeight(50)
            .addMatchingAdData(MatchingAdData.newBuilder().setAdgroupId(10))
            .addExcludedAttribute(DoubleClickValidator.CREATIVE_FLASH))
        .build();

    BidResponse.Builder goodResp = BidResponse.newBuilder().addAd(testBid()
        .addAttribute(DoubleClickValidator.CREATIVE_NON_FLASH));
    validator.validate(request, goodResp);
    assertFalse(Iterables.isEmpty(bids(goodResp)));

    BidResponse.Builder badResp1 = BidResponse.newBuilder().addAd(testBid());
    validator.validate(request, badResp1);
    assertTrue(Iterables.isEmpty(bids(badResp1)));
  }

  @Test
  public void testNoImp() {
    BidResponse.Builder response = BidResponse.newBuilder().addAd(BidResponse.Ad.newBuilder()
        .addAdslot(BidResponse.Ad.AdSlot.newBuilder()
            .setId(3)
            .setMaxCpmMicros(100000000L)));
    validator.validate(request, response);
    assertTrue(Iterables.isEmpty(bids(response)));
  }

  @Test
  public void testDeals() {
    BidResponse.Builder response = BidResponse.newBuilder()
        .addAd(BidResponse.Ad.newBuilder()
            .addAdslot(BidResponse.Ad.AdSlot.newBuilder()
                .setId(1)
                .setDealId(1)
                .setMaxCpmMicros(100000000L))
            .addAdslot(BidResponse.Ad.AdSlot.newBuilder()
                .setId(1)
                .setDealId(3)
                .setMaxCpmMicros(100000000L)));
    validator.validate(request, response);
    assertEquals(1, Iterables.size(bids(response)));
  }

  @Test
  public void testNoSlots() {
    BidResponse.Builder response = BidResponse.newBuilder().addAd(BidResponse.Ad.newBuilder());
    validator.validate(request, response);
    assertTrue(Iterables.isEmpty(bids(response)));
  }

  private static ImmutableList<BidResponse.Ad.AdSlot.Builder> bids(BidResponse.Builder response) {
    ImmutableList.Builder<BidResponse.Ad.AdSlot.Builder> list = ImmutableList.builder();
    for (BidResponse.Ad.Builder ad : response.getAdBuilderList()) {
      list.addAll(ad.getAdslotBuilderList());
    }
    return list.build();
  }

  private static BidResponse.Ad.Builder testBid() {
    return BidResponse.Ad.newBuilder()
        .addAdslot(BidResponse.Ad.AdSlot.newBuilder()
            .setId(1)
            .setMaxCpmMicros(100000000L));
  }
}
