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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static java.lang.Math.max;
import static java.util.Arrays.asList;

import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.ImmutableList;
import com.google.doubleclick.DcExt;
import com.google.openrtb.OpenRtb;
import com.google.openrtb.OpenRtb.BidRequest;
import com.google.openrtb.OpenRtb.BidRequest.Imp;
import com.google.openrtb.OpenRtb.BidRequest.Imp.Banner;
import com.google.openrtb.OpenRtb.BidRequest.Imp.Native;
import com.google.openrtb.OpenRtb.BidRequest.Imp.Pmp;
import com.google.openrtb.OpenRtb.BidResponse;
import com.google.openrtb.OpenRtb.BidResponse.SeatBid;
import com.google.openrtb.OpenRtb.BidResponse.SeatBid.Bid;
import com.google.openrtb.OpenRtb.NativeResponse;
import com.google.openrtb.json.OpenRtbJsonFactory;
import com.google.protos.adx.NetworkBid;
import com.google.protos.adx.NetworkBid.BidRequest.AdSlot;
import com.google.protos.adx.NetworkBid.BidResponse.Ad;
import java.io.IOException;
import java.util.Base64;
import org.junit.Test;

/**
 * Tests for {@link DoubleClickOpenRtbMapper}.
 */
public class DoubleClickOpenRtbMapperTest {
  public static final String DEFAULT_CALLBACK_URL = "http://localhost";
  private DoubleClickOpenRtbMapper mapper = new DoubleClickOpenRtbMapper(
      new MetricRegistry(),
      TestUtil.getMetadata(),
      OpenRtbJsonFactory.create(),
      ImmutableList.of(DoubleClickLinkMapper.INSTANCE, AdxExtMapper.INSTANCE));

  @Test
  public void testResponse() {
    NetworkBid.BidResponse.Builder dcResponse = mapper.toExchangeBidResponse(
        TestUtil.newBidRequest("1", 1, 1, 1.0).build(),
        BidResponse.newBuilder()
            .setId("1")
            .setBidid("bidid")
            .addSeatbid(SeatBid.newBuilder()
                .addBid(TestData.newBid(false)))
            .build());
    assertThat(dcResponse).isNotNull();
    assertThat(dcResponse.getDebugString()).isEqualTo("bidid");
    assertThat(dcResponse.getAdCount()).isEqualTo(1);
    NetworkBid.BidResponse.Ad ad = dcResponse.getAd(0);
    assertThat(ad.getAdslotCount()).isEqualTo(1);
    assertThat(ad.getAdslot(0).getId()).isEqualTo(1);
    assertThat(ad.getAdslot(0).hasBillingId()).isFalse();
    assertThat(ad.getAdslot(0).getMaxCpmMicros()).isEqualTo(1200000);
  }

  @Test(expected = MapperException.class)
  public void testResponse_nonExistingImp() {
    OpenRtb.BidRequest request = TestUtil.newBidRequest(NetworkBid.BidRequest.newBuilder()
        .setId(TestUtil.REQUEST_ID)
        .addAdslot(AdSlot.newBuilder().setId(5))
        .addAdslot(AdSlot.newBuilder().setId(10)));
    mapper.toExchangeBidResponse(request, TestUtil.newBidResponse(TestData.newBid(false)));
  }

  @Test(expected = MapperException.class)
  public void testResponse_impWithoutAd() {
    OpenRtb.BidRequest request = OpenRtb.BidRequest.newBuilder()
        .setId("1")
        .addImp(Imp.newBuilder()
            .setId("1"))
        .build();
    Bid bid = TestData.newBid(false)
        .setAdm("snippet")
        .build();
    NetworkBid.BidResponse.Ad.Builder ad = mapper.mapResponseAd(request, bid);
    assertThat(ad).isNotNull();
  }

  @Test
  public void testResponse_attributeCheck() {
    OpenRtb.BidRequest request = TestUtil.newBidRequest(TestData.newRequest());
    Bid bid = TestData.newBid(false)
        .setAdid("adId")
        .setAdm("snippet")
        .build();

    NetworkBid.BidResponse.Ad.Builder ad = mapper.mapResponseAd(request, bid);
    assertThat(ad).isNotNull();
  }

  @Test
  public void testResponse_htmlSnippetAd() {
    OpenRtb.BidRequest request = TestUtil.newBidRequest(TestData.newRequest());
    Bid bid = TestData.newBid(false)
        .setAdid("adId")
        .setCrid("creativeId")
        .setAdm("<img src=\"foo\">")
        .build();
    NetworkBid.BidResponse.Ad.Builder ad = mapper.mapResponseAd(request, bid);
    assertThat(ad).isNotNull();
    assertThat(ad.getHtmlSnippet()).isEqualTo("<img src=\"foo\">");
    assertThat(ad.getBuyerCreativeId()).isEqualTo("creativeId");
    assertThat(ad.hasVideoUrl()).isFalse();
  }

  @Test
  public void testResponse_videoAd() {
    OpenRtb.BidRequest request = TestUtil.newBidRequest(
        TestData.newRequest(0, false, false).setVideo(TestData.newVideo(0)));
    Bid bid = TestData.newBid(false)
        .setAdm("https://my-video")
        .setCrid("creativeId")
        .build();
    NetworkBid.BidResponse.Ad.Builder ad = mapper.mapResponseAd(request, bid);
    assertThat(ad).isNotNull();
    assertThat(ad.getVideoUrl()).isEqualTo("https://my-video");
    assertThat(ad.hasHtmlSnippet()).isFalse();
    assertThat(ad.getBuyerCreativeId()).isEqualTo("creativeId");
  }

  @Test
  public void testResponse_mraidAd() {
    OpenRtb.BidRequest request = TestUtil.newBidRequest(
        TestData.newRequest(0, false, false).setVideo(TestData.newVideo(0)));
    Bid bid = TestData.newBid(false)
        .setAdm("<script my mraid content...>")
        .setCrid("creativeId")
        .build();
    NetworkBid.BidResponse.Ad.Builder ad = mapper.mapResponseAd(request, bid);
    assertThat(ad).isNotNull();
    assertThat(ad.getHtmlSnippet()).isEqualTo("<script my mraid content...>");
    assertThat(ad.hasVideoUrl()).isFalse();
    assertThat(ad.getBuyerCreativeId()).isEqualTo("creativeId");
  }

  @Test(expected = MapperException.class)
  public void testResponse_noCrid() {
    Bid bid = TestData.newBid(false)
        .clearCrid()
        .build();
    mapper.mapResponseAd(TestUtil.newBidRequest(TestData.newRequest()), bid);
  }

  @Test
  public void testResponse_multisizeBannerGood() {
    Imp.Builder imp = Imp.newBuilder()
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
    OpenRtb.BidRequest request = OpenRtb.BidRequest.newBuilder().setId("1").addImp(imp).build();
    NetworkBid.BidResponse.Ad.Builder ad1 = mapper.mapResponseAd(request, bid1);
    assertThat(!ad1.hasWidth() && !ad1.hasHeight()).isTrue();
    NetworkBid.BidResponse.Ad.Builder ad2 = mapper.mapResponseAd(request, bid2);
    assertThat(ad2.getWidth()).isEqualTo(bid2.getW());
    assertThat(ad2.getHeight()).isEqualTo(bid2.getH());
  }

  @Test
  public void testRequest() throws IOException {
    // -1=NO_SLOT, 0=no size, 1, 2=multisize/1 MAD, 3=2 MAD/1 deal, 4..8=3 MAD/2 deals
    for (int size = -1; size <= 8; ++size) {
      for (int flags = 0; flags < 0b10000; ++flags) {
        boolean coppa = (flags & 0b1) != 0;
        boolean mobile = (flags & 0b1) != 0;
        boolean impVideo = (flags & 0b10) != 0;
        boolean impNativ = !impVideo && (flags & 0b100) != 0;
        boolean impBanner = !impVideo && !impNativ;
        boolean fullBid = (flags & 0b1000) != 0;
        boolean linkExt = (flags & 0b1000) != 0;
        String testDesc = String.format(
            "imp=%s, mobile=%s, coppa=%s, link=%s, size=%s%s",
            (impNativ ? "native" : impVideo ? "video" : "banner"),
            mobile, coppa, linkExt, size, fullBid ? "/full" : "");
        ImmutableList.Builder<ExtMapper> extMappers = ImmutableList.builder();
        if (linkExt) {
          extMappers.add(DoubleClickLinkMapper.INSTANCE);
        }
        DoubleClickOpenRtbMapper mapper = new DoubleClickOpenRtbMapper(
            new MetricRegistry(),
            TestUtil.getMetadata(),
            OpenRtbJsonFactory.create(),
            extMappers.build());

        NetworkBid.BidRequest.Builder dcRequest = TestData.newRequest(size, coppa, impNativ);
        if (mobile) {
          dcRequest.setMobile(TestData.newMobile(size, coppa));
        }
        if (impVideo) {
          dcRequest.setVideo(TestData.newVideo(size));
        }
        BidRequest request = mapper.toOpenRtbBidRequest(dcRequest.build()).build();

        assertWithMessage(testDesc).that(request.getRegs().hasCoppa()).isEqualTo(coppa);
        assertWithMessage(testDesc).that(request.getRegs().getCoppa()).isEqualTo(coppa);
        assertWithMessage(testDesc).that(request.getUser().getCustomdata())
            .isEqualTo(coppa ? "" : "7CLmnMiwSsq7bNTaiPsztg");

        if (request.getImpCount() == 0) {
          BidResponse response = TestUtil.newBidResponse();
          NetworkBid.BidResponse dcResponse =
              mapper.toExchangeBidResponse(request, response).build();
          assertWithMessage(testDesc).that(dcResponse.getAdCount()).isEqualTo(0);
        } else {
          Imp imp = request.getImp(0);
          assertWithMessage(testDesc).that(imp.hasVideo()).isEqualTo(impVideo);
          assertWithMessage(testDesc).that(imp.hasBanner()).isEqualTo(impBanner);
          assertWithMessage(testDesc).that(imp.hasNative()).isEqualTo(impNativ);
          if (impVideo) {
            if (imp.getVideo().getCompanionadCount() != 0) {
              Banner compAd = imp.getVideo().getCompanionad(0);
              assertWithMessage(testDesc).that(compAd.hasW() && compAd.hasH()).isEqualTo(size >= 1);
              assertWithMessage(testDesc).that(compAd.getFormatCount()).isEqualTo(max(0, size));
            }
          } else if (impBanner) {
            Banner banner = imp.getBanner();
            assertWithMessage(testDesc).that(banner.hasW() && banner.hasH()).isEqualTo(size >= 1);
            assertWithMessage(testDesc).that(banner.getFormatCount()).isEqualTo(max(0, size));
            assertWithMessage(testDesc).that(banner.hasTopframe()).isEqualTo(size >= 2);
          } else if (impNativ) {
            Native nativ = imp.getNative();
            assertWithMessage(testDesc).that(nativ.getRequestNative().getAssetsCount())
                .isEqualTo(size == 0 ? 6 : 11);
          }

          Bid.Builder bid = TestData.newBid((fullBid || imp.getInstl()) && !impNativ);
          if (impNativ) {
            NativeResponse nativResp = TestData.newNativeResponse(size - 1).build();
            if (size % 2 == 0) {
              bid.clearAdm().setAdmNative(nativResp);
            } else {
              bid.setAdm(OpenRtbJsonFactory.create().newNativeWriter()
                  .writeNativeResponse(nativResp));
            }
          }
          BidResponse response = TestUtil.newBidResponse(bid);
          NetworkBid.BidResponse dcResponse =
              mapper.toExchangeBidResponse(request, response).build();
          if (linkExt) {
            Ad ad = dcResponse.getAd(0);
            assertWithMessage(testDesc).that(ad.hasWidth()).isEqualTo(bid.hasW());
            assertWithMessage(testDesc).that(ad.getAdslot(0).hasBillingId())
                .isEqualTo(bid.hasCid());
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
    assertThat(request).isEqualTo(OpenRtb.BidRequest.newBuilder()
        .setId(Base64.getEncoder().withoutPadding().encodeToString(dcRequest.getId().toByteArray()))
        .build());
  }

  @Test
  public void testRequest_deals() {
    NetworkBid.BidRequest dcRequest = TestData.newRequest(5, false, false).build();
    OpenRtb.BidRequest request = mapper.toOpenRtbBidRequest(dcRequest).build();
    Pmp pmp = request.getImp(0).getPmp();
    assertThat(pmp.getExtension(DcExt.adData).getDirectDealCount()).isEqualTo(3);
    assertThat(pmp.getDealsCount()).isEqualTo(2);
    Pmp.Deal deal = pmp.getDeals(1);
    assertThat(deal.getId()).isEqualTo("44");
    assertThat(deal.getBidfloor()).isWithin(1e-9).of(1.2);
  }

  @Test
  public void testReqResp_NullMapper() {
    NullDoubleClickOpenRtbMapper mapper = NullDoubleClickOpenRtbMapper.INSTANCE;
    assertThat(mapper.toExchangeBidResponse(
            OpenRtb.BidRequest.getDefaultInstance(), OpenRtb.BidResponse.getDefaultInstance()))
        .isNull();
    assertThat(mapper.toOpenRtbBidRequest(TestData.newRequest())).isNull();
    assertThat(mapper.toExchangeBidRequest(OpenRtb.BidRequest.getDefaultInstance())).isNull();
    assertThat(mapper.toOpenRtbBidResponse(
        TestData.newRequest(), NetworkBid.BidResponse.getDefaultInstance())).isNull();
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
    extMapper.toOpenRtbImp(
        NetworkBid.BidRequest.AdSlot.getDefaultInstance(),
        OpenRtb.BidRequest.Imp.newBuilder());
    extMapper.toOpenRtbBanner(
        NetworkBid.BidRequest.AdSlot.getDefaultInstance(),
        OpenRtb.BidRequest.Imp.Banner.newBuilder());
    extMapper.toOpenRtbVideo(
        NetworkBid.BidRequest.Video.getDefaultInstance(),
        OpenRtb.BidRequest.Imp.Video.newBuilder());
    extMapper.toOpenRtbPmp(
        NetworkBid.BidRequest.AdSlot.MatchingAdData.getDefaultInstance(),
        OpenRtb.BidRequest.Imp.Pmp.newBuilder());
    extMapper.toDoubleClickAd(
        OpenRtb.BidRequest.getDefaultInstance(),
        OpenRtb.BidResponse.SeatBid.Bid.getDefaultInstance(),
        NetworkBid.BidResponse.Ad.newBuilder());
  }
}
