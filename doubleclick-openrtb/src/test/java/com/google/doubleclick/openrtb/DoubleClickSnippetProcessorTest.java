/*
 * Copyright 2015 Google Inc. All Rights Reserved.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import com.google.doubleclick.util.DoubleClickMacros;
import com.google.openrtb.OpenRtb.BidRequest;
import com.google.openrtb.OpenRtb.BidRequest.Imp;
import com.google.openrtb.OpenRtb.BidResponse;
import com.google.openrtb.OpenRtb.BidResponse.SeatBid;
import com.google.openrtb.OpenRtb.BidResponse.SeatBid.Bid;
import com.google.openrtb.snippet.OpenRtbMacros;
import com.google.openrtb.snippet.SnippetProcessorContext;

import org.junit.Test;

/**
 * Tests for {@link DoubleClickSnippetProcessor}.
 */
public class DoubleClickSnippetProcessorTest {

  @Test
  public void testProcessor() {
    assertEquals(
        "1", // Not overridden
        process(new DoubleClickSnippetProcessor(), OpenRtbMacros.AUCTION_ID.key()));
    assertEquals(
        DoubleClickMacros.WINNING_PRICE.key(), // Overridden
        process(new DoubleClickSnippetProcessor(), OpenRtbMacros.AUCTION_PRICE.key()));
  }

  @Test
  public void testNullProcessor() {
    assertSame(
        OpenRtbMacros.AUCTION_PRICE.key(),
        process(DoubleClickSnippetProcessor.DC_NULL, OpenRtbMacros.AUCTION_PRICE.key()));
  }

  private String process(DoubleClickSnippetProcessor processor, String snippet) {
    BidRequest request = BidRequest.newBuilder()
        .setId("1")
        .addImp(Imp.newBuilder()
            .setId("1")).build();
    BidResponse.Builder response = createBidResponse(snippet);
    SnippetProcessorContext ctx = new SnippetProcessorContext(request, response);
    ctx.setBid(response.getSeatbidBuilder(0).getBidBuilder(0));
    return processor.process(ctx, snippet);
  }

  private static BidResponse.Builder createBidResponse(String snippet) {
    return BidResponse.newBuilder()
        .setId("1")
        .addSeatbid(SeatBid.newBuilder()
            .setSeat("seat1")
            .addBid(Bid.newBuilder()
                .setId("1")
                .setImpid("1")
                .setPrice(1000)
                .setAdm(snippet)));
  }
}
