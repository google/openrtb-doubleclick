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

package com.google.doubleclick.openrtb;

import com.google.common.collect.ImmutableList;
import com.google.doubleclick.Doubleclick;
import com.google.doubleclick.crypto.DoubleClickCrypto;
import com.google.doubleclick.util.DoubleClickMetadata;
import com.google.openrtb.OpenRtb.BidRequest;
import com.google.openrtb.OpenRtb.BidRequest.Impression;
import com.google.openrtb.OpenRtb.BidRequest.Impression.Banner;
import com.google.openrtb.OpenRtb.BidResponse;
import com.google.openrtb.OpenRtb.BidResponse.SeatBid;
import com.google.openrtb.OpenRtb.BidResponse.SeatBid.Bid;
import com.google.openrtb.OpenRtb.BidResponse.SeatBid.BidOrBuilder;
import com.google.protobuf.ByteString;

import com.codahale.metrics.MetricRegistry;

import javax.crypto.spec.SecretKeySpec;

class TestUtil {
  public static final String DEFAULT_URI = "http://example.com";
  public static final ByteString REQUEST_ID = ByteString.copyFromUtf8("01234567");
  public static final DoubleClickCrypto.Keys KEYS = new DoubleClickCrypto.Keys(
      new SecretKeySpec(new byte[32], "HmacSHA1"),
      new SecretKeySpec(new byte[32], "HmacSHA1"));
  private static DoubleClickMetadata metadata;

  private TestUtil() {
  }

  public static BidRequest.Builder newBidRequest(String id, Object... adGroupMincpm) {
    BidRequest.Builder request = BidRequest.newBuilder().setId(id);

    for (int i = 0; i < adGroupMincpm.length; i += 3) {
      String adid = String.valueOf(adGroupMincpm[i + 0]);
      request.addImp(Impression.newBuilder()
          .setId(adid)
          .setBidfloor(((Number) adGroupMincpm[i + 2]).floatValue())
          .setBanner(Banner.newBuilder()
              .setId(adid)
              .setW(728)
              .setH(90)));
    }

    return request;
  }

  public static BidResponse newBidResponse(BidOrBuilder... bids) {
    SeatBid.Builder seat = SeatBid.newBuilder();

    for (BidOrBuilder bid : bids) {
      if (bid instanceof Bid) {
        seat.addBid((Bid) bid);
      } else {
        seat.addBid((Bid.Builder) bid);
      }
    }

    return BidResponse.newBuilder().addSeatbid(seat).build();
  }

  public static DoubleClickMetadata getMetadata() {
    if (metadata == null) {
      metadata = new DoubleClickMetadata(new DoubleClickMetadata.ResourceTransport());
    }

    return metadata;
  }

  public static BidRequest newBidRequest(Doubleclick.BidRequestOrBuilder adxRequest) {
    Doubleclick.BidRequest dcRequest = adxRequest instanceof Doubleclick.BidRequest
        ? (Doubleclick.BidRequest) adxRequest
        : ((Doubleclick.BidRequest.Builder) adxRequest).build();
    return new DoubleClickOpenRtbMapper(
        new MetricRegistry(),
        getMetadata(),
        new DoubleClickCrypto.Hyperlocal(KEYS),
        ImmutableList.of(DoubleClickLinkMapper.INSTANCE))
            .toOpenRtb(dcRequest);
  }
}
