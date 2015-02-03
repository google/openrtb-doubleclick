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

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableList;
import com.google.common.io.BaseEncoding;
import com.google.doubleclick.DcExt;
import com.google.doubleclick.crypto.DoubleClickCrypto;
import com.google.openrtb.OpenRtb;
import com.google.openrtb.OpenRtb.BidRequest;
import com.google.openrtb.OpenRtb.BidRequest.Impression;
import com.google.openrtb.OpenRtb.BidRequest.Impression.Banner;
import com.google.openrtb.OpenRtb.BidRequest.Impression.PMP;
import com.google.openrtb.OpenRtb.BidResponse;
import com.google.openrtb.OpenRtb.BidResponse.SeatBid;
import com.google.openrtb.OpenRtb.BidResponse.SeatBid.Bid;
import com.google.protobuf.ByteString;
import com.google.protos.adx.NetworkBid;
import com.google.protos.adx.NetworkBid.BidRequest.AdSlot;
import com.google.protos.adx.NetworkBid.BidResponse.Ad;

import com.codahale.metrics.MetricRegistry;

import org.junit.Test;

/**
 * Tests for {@link DoubleClickOpenRtbMapper}.
 */
public class DoubleClickOpenRtbMapperTest {
  public static final String DEFAULT_CALLBACK_URL = "http://localhost";
  private DoubleClickOpenRtbMapper mapper = new DoubleClickOpenRtbMapper(
      new MetricRegistry(),
      TestUtil.getMetadata(),
      new DoubleClickCrypto.Hyperlocal(TestUtil.KEYS),
      ImmutableList.of(DoubleClickLinkMapper.INSTANCE));

  @Test
  public void testResponse() {
    NetworkBid.BidResponse.Builder dcResponse = mapper.toNativeBidResponse(
        TestUtil.newBidRequest("1", 1, 1, 1.0).build(),
        BidResponse.newBuilder()
            .setBidid("bidid")
            .addSeatbid(SeatBid.newBuilder()
                .addBid(TestData.newBid(false)))
            .build());
    assertNotNull(dcResponse);
    assertEquals("bidid", dcResponse.getDebugString());
    assertEquals(1, dcResponse.getAdCount());
    NetworkBid.BidResponse.Ad ad = dcResponse.getAd(0);
    assertEquals(1, ad.getAdslotCount());
    assertEquals(1, ad.getAdslot(0).getId());
    assertFalse(ad.getAdslot(0).hasAdgroupId());
    assertEquals(1200000, ad.getAdslot(0).getMaxCpmMicros());
  }

  @Test(expected = MapperException.class)
  public void testResponse_nonExistingImp() {
    OpenRtb.BidRequest request = TestUtil.newBidRequest(NetworkBid.BidRequest.newBuilder()
        .setId(TestUtil.REQUEST_ID)
        .addAdslot(AdSlot.newBuilder().setId(5))
        .addAdslot(AdSlot.newBuilder().setId(10)));
    mapper.toNativeBidResponse(request, TestUtil.newBidResponse(TestData.newBid(false)));
  }

  @Test(expected = MapperException.class)
  public void testResponse_impWithoutAd() {
    OpenRtb.BidRequest request = OpenRtb.BidRequest.newBuilder()
        .setId("1")
        .addImp(Impression.newBuilder()
            .setId("1"))
        .build();
    Bid bid = TestData.newBid(false)
        .setAdm("snippet")
        .build();
    NetworkBid.BidResponse.Ad.Builder ad = mapper.buildResponseAd(
        request, TestUtil.newBidResponse(bid), bid);
    assertNotNull(ad);
  }

  @Test
  public void testResponse_attributeCheck() {
    OpenRtb.BidRequest request = TestUtil.newBidRequest(TestData.newRequest());
    Bid bid = TestData.newBid(false)
        .setAdid("adId")
        .setAdm("snippet")
        .build();

    NetworkBid.BidResponse.Ad.Builder ad = mapper.buildResponseAd(
        request, TestUtil.newBidResponse(bid), bid);
    assertNotNull(ad);
  }

  @Test
  public void testResponse_htmlSnippetAd() {
    OpenRtb.BidRequest request = TestUtil.newBidRequest(TestData.newRequest());
    Bid bid = TestData.newBid(false)
        .setAdid("adId")
        .setCrid("creativeId")
        .setAdm("<img src=\"foo\">")
        .build();
    NetworkBid.BidResponse.Ad.Builder ad = mapper.buildResponseAd(
        request, TestUtil.newBidResponse(bid), bid);
    assertNotNull(ad);
    assertEquals("<img src=\"foo\">", ad.getHtmlSnippet());
    assertEquals("creativeId", ad.getBuyerCreativeId());
    assertTrue(!ad.hasVideoUrl());
  }

  @Test
  public void testResponse_videoAd() {
    OpenRtb.BidRequest request = TestUtil.newBidRequest(
        TestData.newRequest(0, false).setVideo(TestData.newVideo(0)));
    Bid bid = TestData.newBid(false)
        .setAdm("http://my-video")
        .setCrid("creativeId")
        .build();
    NetworkBid.BidResponse.Ad.Builder ad = mapper.buildResponseAd(
        request, TestUtil.newBidResponse(bid), bid);
    assertNotNull(ad);
    assertEquals("http://my-video", ad.getVideoUrl());
    assertEquals("creativeId", ad.getBuyerCreativeId());
    assertTrue(!ad.hasHtmlSnippet());
  }

  @Test
  public void testResponse_videoAd_missingSize() {
    OpenRtb.BidRequest request = TestUtil.newBidRequest(
        TestData.newRequest(3, false).setVideo(TestData.newVideo(0)));
    Bid bid = TestData.newBid(false)
        .setAdm("http://my-video")
        .setCrid("creativeId")
        .build();
    assertEquals(3, request.getImp(0).getExtension(DcExt.adSlot).getWidthCount());
    assertFalse(request.getImp(0).getVideo().hasW());
    NetworkBid.BidResponse.Ad.Builder ad = mapper.buildResponseAd(
        request, TestUtil.newBidResponse(bid), bid);
    assertFalse(ad.hasWidth());
  }

  @Test
  public void testResponse_htmlSnippetTemplateAd() {
    OpenRtb.BidRequest request = TestUtil.newBidRequest(TestData.newRequest());
    Bid bid = TestData.newBid(false)
        .setAdid("99")
        .setCrid("creativeId")
        .setAdm("<img src=\"foo-templatized\">")
        .setExtension(DcExt.ad,
            NetworkBid.BidResponse.Ad.newBuilder()
                .addTemplateParameter(NetworkBid.BidResponse.Ad.TemplateParameter.newBuilder())
                .build())
        .build();
    NetworkBid.BidResponse.Ad.Builder ad = mapper.buildResponseAd(
        request, TestUtil.newBidResponse(bid), bid);
    assertNotNull(ad);
    assertFalse(ad.hasHtmlSnippet());
    assertEquals("<img src=\"foo-templatized\">", ad.getSnippetTemplate());
    assertEquals(1, ad.getTemplateParameterCount());
    assertEquals("creativeId", ad.getBuyerCreativeId());
    assertTrue(!ad.hasVideoUrl());
  }

  @Test(expected = MapperException.class)
  public void testResponse_noCrid() {
    Bid bid = TestData.newBid(false)
        .clearCrid()
        .build();
    mapper.buildResponseAd(
        TestUtil.newBidRequest(TestData.newRequest()),
        TestUtil.newBidResponse(bid),
        bid);
  }

  @Test
  public void testResponse_multisizeBannerGood() {
    Impression.Builder imp = Impression.newBuilder()
        .setId("1")
        .setBanner(Banner.newBuilder().setId("1")
            .setWmin(100).setWmax(200)
            .setHmin(300).setHmax(400))
        .setExtension(DcExt.adSlot, NetworkBid.BidRequest.AdSlot.newBuilder()
            .setId(0)
            .addAllWidth(asList(100, 200))
            .addAllHeight(asList(300, 400)).build());
    Bid bid1 = TestData.newBid(false).build();
    Bid bid2 = TestData.newBid(true).build();
    BidResponse response = TestUtil.newBidResponse(bid1, bid1);
    OpenRtb.BidRequest request = OpenRtb.BidRequest.newBuilder().setId("1").addImp(imp).build();
    NetworkBid.BidResponse.Ad.Builder ad1 = mapper.buildResponseAd(request, response, bid1);
    assertTrue(!ad1.hasWidth() && !ad1.hasHeight());
    NetworkBid.BidResponse.Ad.Builder ad2 = mapper.buildResponseAd(request, response, bid2);
    assertEquals(bid2.getW(), ad2.getWidth());
    assertEquals(bid2.getH(), ad2.getHeight());
  }

  @Test
  public void testRequest() {
    // -1=NO_SLOT, 0=no size, 1, 2=multisize/1 MAD, 3=2 MAD/1 deal, 4..8=3 MAD/2 deals
    for (int size = -1; size <= 8; ++size) {
      for (int flags = 0; flags < 0b10000; ++flags) {
        boolean coppa = (flags & 0b1) != 0;
        boolean mobile = (flags & 0b1) != 0;
        boolean video = (flags & 0b10) != 0;
        boolean multiBid = (flags & 0b100) != 0;
        boolean linkExt = (flags & 0b100) != 0;
        String testDesc = String.format("mobile=%s, video=%s, coppa=%s, link=%s, size=%s%s",
            mobile, video, coppa, linkExt, size, multiBid ? "/full" : "");
        ImmutableList.Builder<ExtMapper> extMappers = ImmutableList.builder();
        if (linkExt) {
          extMappers.add(DoubleClickLinkMapper.INSTANCE);
        }
        DoubleClickOpenRtbMapper mapper = new DoubleClickOpenRtbMapper(
            new MetricRegistry(),
            TestUtil.getMetadata(),
            new DoubleClickCrypto.Hyperlocal(TestUtil.KEYS),
            extMappers.build());

        NetworkBid.BidRequest.Builder dcRequest = TestData.newRequest(size, coppa);
        if (mobile) {
          dcRequest.setMobile(TestData.newMobile(size, coppa));
        }
        if (video) {
          dcRequest.setVideo(TestData.newVideo(size));
        }
        BidRequest request = mapper.toOpenRtbBidRequest(dcRequest.build()).build();

        assertEquals(testDesc, coppa, request.getRegs().hasCoppa());
        assertEquals(testDesc, coppa, request.getRegs().getCoppa());
        assertEquals(
            coppa ? "" : "EC22E69CC8B04ACABB6CD4DA88FB33B6",
            request.getUser().getBuyeruid());

        if (size == TestData.NO_SLOT) {
          assertEquals(0, request.getImpCount());
          BidResponse response = TestUtil.newBidResponse();
          NetworkBid.BidResponse dcResponse = mapper.toNativeBidResponse(request, response).build();
          assertEquals(0, dcResponse.getAdCount());
        } else {
          Impression imp = request.getImp(0);
          assertEquals(testDesc, video, imp.hasVideo());
          assertNotEquals(testDesc, video, imp.hasBanner());
          if (video) {
            if (imp.getVideo().getCompanionadCount() != 0) {
              Banner compAd = imp.getVideo().getCompanionad(0);
              assertEquals(testDesc, size > 1,
                  compAd.hasWmin() && compAd.hasWmax() && compAd.hasHmin() && compAd.hasHmax());
              assertNotEquals(testDesc, size != 1, compAd.hasW() && compAd.hasH());
            }
          } else {
            Banner banner = imp.getBanner();
            assertEquals(testDesc, size > 1,
                banner.hasWmin() && banner.hasWmax() && banner.hasHmin() && banner.hasHmax());
            assertNotEquals(testDesc, size != 1, banner.hasW() && banner.hasH());
            assertEquals(size >= 2, banner.hasTopframe());
          }

          Bid.Builder bid = TestData.newBid(multiBid || imp.getInstl());
          BidResponse response = TestUtil.newBidResponse(bid);
          NetworkBid.BidResponse dcResponse = mapper.toNativeBidResponse(request, response).build();
          if (linkExt) {
            Ad ad = dcResponse.getAd(0);
            assertEquals(testDesc, (size > 1 && multiBid) || imp.getInstl(), ad.hasWidth());
            assertEquals(testDesc, size > 2 && multiBid, ad.getAdslot(0).hasAdgroupId());
          }
        }
      }
    }
  }

  @Test
  public void testRequest_ping() {
    NetworkBid.BidRequest dcRequest = NetworkBid.BidRequest.newBuilder()
        .setId(TestUtil.REQUEST_ID)
        .setIsPing(true)
        .build();
    OpenRtb.BidRequest request = mapper.toOpenRtbBidRequest(dcRequest).build();
    assertEquals(
        OpenRtb.BidRequest.newBuilder().setId(BaseEncoding.base16().encode(dcRequest.getId().toByteArray())).build(),
        request);
  }

  @Test
  public void testRequest_deals() {
    NetworkBid.BidRequest dcRequest = TestData.newRequest(5, false).build();
    OpenRtb.BidRequest request = mapper.toOpenRtbBidRequest(dcRequest).build();
    PMP pmp = request.getImp(0).getPmp();
    assertEquals(3, pmp.getExtension(DcExt.adData).getDirectDealCount());
    assertEquals(2, pmp.getDealsCount());
    PMP.Deal deal = pmp.getDeals(1);
    assertEquals("44", deal.getId());
    assertEquals(1.2, deal.getBidfloor(), 1e-9);
  }

  @Test
  public void testReqResp_NullMapper() {
    NullDoubleClickOpenRtbMapper mapper = NullDoubleClickOpenRtbMapper.INSTANCE;
    assertNull(mapper.toNativeBidResponse(
        OpenRtb.BidRequest.getDefaultInstance(), OpenRtb.BidResponse.getDefaultInstance()));
    assertNull(mapper.toOpenRtbBidRequest(TestData.newRequest()));
    assertNull(mapper.toNativeBidRequest(OpenRtb.BidRequest.getDefaultInstance()));
    assertNull(mapper.toOpenRtbBidResponse(
        TestData.newRequest(), NetworkBid.BidResponse.getDefaultInstance()));
  }

  @Test
  public void testExtMapper() {
    ExtMapper extMapper = new ExtMapper() {};
    extMapper.toOpenRtbBidRequest(
        NetworkBid.BidRequest.getDefaultInstance(),
        OpenRtb.BidRequest.newBuilder());
    extMapper.toOpenRtbDevice(
        NetworkBid.BidRequest.getDefaultInstance(),
        OpenRtb.BidRequest.Device.newBuilder());
    extMapper.toOpenRtbImpression(
        NetworkBid.BidRequest.AdSlot.getDefaultInstance(),
        OpenRtb.BidRequest.Impression.newBuilder());
    extMapper.toOpenRtbBanner(
        NetworkBid.BidRequest.AdSlot.getDefaultInstance(),
        OpenRtb.BidRequest.Impression.Banner.newBuilder());
    extMapper.toOpenRtbVideo(
        NetworkBid.BidRequest.Video.getDefaultInstance(),
        OpenRtb.BidRequest.Impression.Video.newBuilder());
    extMapper.toOpenRtbPMP(
        NetworkBid.BidRequest.AdSlot.MatchingAdData.getDefaultInstance(),
        OpenRtb.BidRequest.Impression.PMP.newBuilder());
    extMapper.toNativeAd(
        OpenRtb.BidRequest.getDefaultInstance(),
        OpenRtb.BidResponse.getDefaultInstance(),
        OpenRtb.BidResponse.SeatBid.Bid.getDefaultInstance(),
        NetworkBid.BidResponse.Ad.newBuilder());
  }
}
