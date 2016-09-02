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

import static java.lang.Math.min;
import static java.util.Arrays.asList;

import com.google.common.collect.ImmutableList;
import com.google.doubleclick.crypto.DoubleClickCrypto;
import com.google.openrtb.OpenRtb.BidResponse.SeatBid.Bid;
import com.google.openrtb.OpenRtb.NativeResponse;
import com.google.protobuf.ByteString;
import com.google.protos.adx.NetworkBid;
import com.google.protos.adx.NetworkBid.BidRequest.AdSlot;
import com.google.protos.adx.NetworkBid.BidRequest.AdSlot.IFramingDepth;
import com.google.protos.adx.NetworkBid.BidRequest.AdSlot.IFramingState;
import com.google.protos.adx.NetworkBid.BidRequest.AdSlot.MatchingAdData;
import com.google.protos.adx.NetworkBid.BidRequest.AdSlot.MatchingAdData.BuyerPricingRule;
import com.google.protos.adx.NetworkBid.BidRequest.AdSlot.NativeAdTemplate;
import com.google.protos.adx.NetworkBid.BidRequest.AdSlot.SlotVisibility;
import com.google.protos.adx.NetworkBid.BidRequest.Device;
import com.google.protos.adx.NetworkBid.BidRequest.Device.DeviceType;
import com.google.protos.adx.NetworkBid.BidRequest.Hyperlocal;
import com.google.protos.adx.NetworkBid.BidRequest.HyperlocalSet;
import com.google.protos.adx.NetworkBid.BidRequest.Mobile;
import com.google.protos.adx.NetworkBid.BidRequest.UserDataTreatment;
import com.google.protos.adx.NetworkBid.BidRequest.UserDemographic;
import com.google.protos.adx.NetworkBid.BidRequest.Vertical;
import com.google.protos.adx.NetworkBid.BidRequest.Video;
import com.google.protos.adx.NetworkBid.BidRequest.Video.CompanionSlot;
import com.google.protos.adx.NetworkBid.BidRequest.Video.CompanionSlot.CreativeFormat;
import com.google.protos.adx.NetworkBid.BidRequest.Video.ContentAttributes;
import com.google.protos.adx.NetworkBid.BidRequest.Video.VideoFormat;
import java.util.List;

public class TestData {
  static final int NO_SLOT = -1;

  public static Bid.Builder newBid(boolean full) {
    Bid.Builder bid = Bid.newBuilder()
        .setId("0")
        .setImpid("1")
        .setAdid("2")
        .setCrid("4")
        .setPrice(1.2)
        .setAdm("<blink>hello world</blink>");
    if (full) {
      bid
          .setCid("3")
          .setDealid("5")
          .setW(200)
          .setH(220)
          .addAllAdomain(asList("myads1.com", "https://myads2.com"))
          .addCat("IAB1");
    }
    return bid;
  }

  public static NativeResponse.Builder newNativeResponse(int size) {
    NativeResponse.Builder nativ = NativeResponse.newBuilder().setVer("1.0")
        .setLink(NativeResponse.Link.newBuilder()
            .setUrl("http://clickthrough")
            .addClicktrackers("http://clicktracker1"))
        .addAllImptrackers(asList("http://imptracker1", "http://imptracker2"));
    if (size != NO_SLOT) {
      nativ
          .addAssets(newRespAssetTitle(1, "title"))
          .addAssets(newRespAssetData(2, "body"))
          .addAssets(newRespAssetData(3, "ctatext"))
          .addAssets(newRespAssetData(4, "advertiser"))
          .addAssets(newRespAssetImage(5, size, "http://image"))
          .addAssets(newRespAssetImage(6, size, "http://logo"))
          .addAssets(newRespAssetImage(7, size, "http://appicon"))
          .addAssets(newRespAssetData(8, "4.5"))
          .addAssets(newRespAssetData(9, "$9.99"))
          .addAssets(newRespAssetData(10, "Play Store")
              .setLink(NativeResponse.Link.newBuilder().setUrl("http://store")));
    }
    return nativ;
  }

  static NativeResponse.Asset.Builder newRespAssetImage(int id, int size, String url) {
    NativeResponse.Asset.Image.Builder img = NativeResponse.Asset.Image.newBuilder().setUrl(url);
    if (size >= 1) {
      img.setW(100).setH(200);
    }
    return NativeResponse.Asset.newBuilder().setId(id).setImg(img);
  }

  static NativeResponse.Asset.Builder newRespAssetData(int id, String value) {
    return NativeResponse.Asset.newBuilder()
        .setId(id)
        .setData(NativeResponse.Asset.Data.newBuilder().setValue(value));
  }

  static NativeResponse.Asset.Builder newRespAssetTitle(int id, String text) {
    return NativeResponse.Asset.newBuilder()
        .setId(id)
        .setTitle(NativeResponse.Asset.Title.newBuilder().setText(text));
  }

  public static NetworkBid.BidRequest newRequest() {
    return newRequest(0, false, false).build();
  }

  public static NetworkBid.BidRequest.Builder newRequest(int size, boolean coppa, boolean nativ) {
    NetworkBid.BidRequest.Builder req = NetworkBid.BidRequest.newBuilder()
        .setId(TestUtil.REQUEST_ID)
        .setIsTest(false)
        .addAllDetectedContentLabel(sublist(size, 40, 41, 999))
        .addAllDetectedLanguage(sublist(size, "en", "en_US", "pt", "pt_BR"))
        .addAllDetectedVertical(sublist(size,
            Vertical.newBuilder().setId(1).setWeight(0.25f).build(),
            Vertical.newBuilder().setId(99).setWeight(0.33f).build(),
            Vertical.newBuilder().setId(2).setWeight(0.75f).build(),
            Vertical.newBuilder().setId(99).setWeight(0.99f).build()));
    if (size == 1) {
      req
          .setIp(ByteString.copyFrom(new byte[] { (byte) 192, (byte) 168, (byte) 1 } ))
          .setUserAgent("Chrome")
          .setGeoCriteriaId(1023191)
          .setTimezoneOffset(3600)
          .setAnonymousId("mysite.com")
          .setSellerNetworkId(1);
    } else if (size == 2) {
      req
          .setIp(ByteString.copyFrom(new byte[] {
              0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x1F, 0x2F, 0x3F, 0x4F, 0x5F, 0x6F } ))
          .setUrl("mysite.com/newsfeed")
          .setPostalCode("10011")
          .setUserDemographic(UserDemographic.newBuilder()
              .setGender(UserDemographic.Gender.FEMALE)
              .setAgeLow(18)
              .setAgeHigh(24))
          .setEncryptedHyperlocalSet(ByteString.copyFrom(new byte[]{1,2,3} /* bad */));
    } else if (size == 3) {
      req
          .setUserDemographic(UserDemographic.newBuilder())
          .setPostalCodePrefix("100")
          .setEncryptedHyperlocalSet(ByteString.copyFrom(
              new DoubleClickCrypto.Hyperlocal(TestUtil.KEYS).encryptHyperlocal(
                  HyperlocalSet.newBuilder()
                      .setCenterPoint(Hyperlocal.Point.newBuilder()
                          .setLatitude(45)
                          .setLongitude(90))
                      .build().toByteArray(), new byte[16])));
    } else if (size >= 4) {
      req
          .setGeoCriteriaId(0 /* bad */)
          .setEncryptedHyperlocalSet(ByteString.copyFrom(
              new DoubleClickCrypto.Hyperlocal(TestUtil.KEYS).encryptHyperlocal(
                  HyperlocalSet.newBuilder().build().toByteArray(), new byte[16])));
    }
    if (size != NO_SLOT) {
      req.setDevice(newDevice());
      AdSlot.Builder adSlot = AdSlot.newBuilder()
          .setId(1)
          .addAllWidth(createSizes(size, 100))
          .addAllHeight(createSizes(size, 200))
          .addAllAllowedVendorType(sublist(size, 10, 94, 97))
          .addAllExcludedSensitiveCategory(sublist(size, 0, 3, 4))
          .addAllExcludedAttribute(sublist(size, 1, 2, 3, 32 /* MraidType: Mraid 1.0 */))
          .addAllExcludedProductCategory(sublist(size, 1, 2, 999));
      for (int i = 1; i < size; ++i) {
        adSlot
            .setAdBlockKey(i)
            .setSlotVisibility(SlotVisibility.ABOVE_THE_FOLD)
            .addTargetableChannel(size % 2 == 0 ? "afv_user_id_PewDiePie" : "pack-anon-x::y")
            .setIframingState(i == 1 ? IFramingState.NO_IFRAME : IFramingState.SAME_DOMAIN_IFRAME);
        MatchingAdData.Builder mad = MatchingAdData.newBuilder().addBillingId(100 + i);
        if (i >= 2) {
          adSlot.setIframingDepth( // Only used by video, but keep things simple
              i == 2 ? IFramingDepth.ONE_IFRAME : IFramingDepth.MULTIPLE_IFRAME);
          mad.setMinimumCpmMicros(10000 + i);
          for (int j = 2; j <= i; ++j) {
            MatchingAdData.DirectDeal.Builder deal = MatchingAdData.DirectDeal.newBuilder();
            if (j >= 3) {
              deal.setDirectDealId(10 * i + j);
              deal.setFixedCpmMicros(1200000);
            }
            mad.addDirectDeal(deal);

            BuyerPricingRule.Builder rule = BuyerPricingRule.newBuilder();
            if (j >= 3) {
              rule.setMinimumCpmMicros(1200000);
            }
            mad.addPricingRule(rule);
          }
        }
        adSlot.addMatchingAdData(mad);
      }

      if (nativ) {
        newNative(adSlot, size);
      }
      req.addAdslot(adSlot);
    }
    if (coppa) {
      req.addUserDataTreatment(UserDataTreatment.TAG_FOR_CHILD_DIRECTED_TREATMENT);
      req.setConstrainedUsageGoogleUserId("j");
      req.setConstrainedUsageHostedMatchData(ByteString.EMPTY);
    } else {
      req.setGoogleUserId("john");
      req.setHostedMatchData(ByteString.copyFrom(new byte[]{
          (byte) 0xEC, (byte) 0x22, (byte) 0xE6, (byte) 0x9C,
          (byte) 0xC8, (byte) 0xB0, (byte) 0x4A, (byte) 0xCA,
          (byte) 0xBB, (byte) 0x6C, (byte) 0xD4, (byte) 0xDA,
          (byte) 0x88, (byte) 0xFB, (byte) 0x33, (byte) 0xB6
      }));
    }
    return req;
  }

  static Device.Builder newDevice() {
    return Device.newBuilder()
        .setDeviceType(DeviceType.HIGHEND_PHONE)
        .setOsVersion(Device.OsVersion.newBuilder().setMajor(3).setMinor(2).setMicro(1))
        .setModel("MotoX")
        .setScreenHeight(1024)
        .setScreenWidth(800)
        .setScreenPixelRatioMillis(1500)
        .setCarrierId(10)
        .setPlatform("android");
  }

  static Mobile.Builder newMobile(int size, boolean coppa) {
    Mobile.Builder mobile = Mobile.newBuilder();
    if (size % 2 == 0) {
      mobile
          .setIsApp(true)
          .setAppId("com.mygame")
          .setEncryptedAdvertisingId(ByteString.EMPTY)
          .setEncryptedHashedIdfa(ByteString.EMPTY)
          .setAppName("Tic-Tac-Toe")
          .setAppRating(4.2f)
          .setIsInterstitialRequest(true)
          .setIsMobileWebOptimized(size % 4 == 0);

      if (size % 4 == 0) {
        if (coppa) {
          mobile.setConstrainedUsageEncryptedHashedIdfa(ByteString.EMPTY);
        } else {
          mobile.setEncryptedHashedIdfa(ByteString.EMPTY);
        }
      } else {
        if (coppa) {
          mobile.setConstrainedUsageEncryptedAdvertisingId(ByteString.EMPTY);
        } else {
          mobile.setEncryptedAdvertisingId(ByteString.EMPTY);
        }
      }
    }
    return mobile;
  }

  static Video.Builder newVideo(int size) {
    Video.Builder video = Video.newBuilder()
        .addAllAllowedVideoFormats(sublist(size, VideoFormat.VIDEO_FLV, VideoFormat.VIDEO_MP4))
        .setMinAdDuration(15)
        .setMaxAdDuration(60);
    if (size != NO_SLOT) {
      CompanionSlot.Builder compSlot = CompanionSlot.newBuilder()
          .addAllWidth(createSizes(size, 100))
          .addAllHeight(createSizes(size, 200));
      if (size >= 2) {
        video.setVideoadStartDelay(5);
        video.setPlaybackMethod(Video.VideoPlaybackMethod.AUTO_PLAY_SOUND_OFF);
        compSlot.addCreativeFormat(CreativeFormat.IMAGE_CREATIVE);
        ContentAttributes.Builder vcont = ContentAttributes.newBuilder();
        if (size >= 3) {
          vcont.setTitle("Gone with the Wind");
          vcont.setDurationSeconds(14280);
          vcont.addAllKeywords(asList("civil war", "carrots"));
        }
        video.setContentAttributes(vcont);
      }
      video.addCompanionSlot(compSlot);
    }
    return video;
  }

  static void newNative(AdSlot.Builder adSlot, int size) {
    NativeAdTemplate.Builder asset = NativeAdTemplate.newBuilder()
        .setRecommendedFields(0
            | NativeAdTemplate.Fields.HEADLINE_VALUE
            | NativeAdTemplate.Fields.BODY_VALUE
            | NativeAdTemplate.Fields.CALL_TO_ACTION_VALUE
            | NativeAdTemplate.Fields.ADVERTISER_VALUE
            | NativeAdTemplate.Fields.IMAGE_VALUE
            | NativeAdTemplate.Fields.LOGO_VALUE
            | NativeAdTemplate.Fields.APP_ICON_VALUE
            | NativeAdTemplate.Fields.STAR_RATING_VALUE
            | NativeAdTemplate.Fields.PRICE_VALUE
            | NativeAdTemplate.Fields.STORE_VALUE);

    if (size >= 1) {
      asset.setHeadlineMaxSafeLength(10);
      asset.setBodyMaxSafeLength(10);
      asset.setCallToActionMaxSafeLength(10);
      asset.setAdvertiserMaxSafeLength(10);
      asset.setImageWidth(100).setImageHeight(200);
      asset.setLogoWidth(100).setLogoHeight(200);
      asset.setAppIconWidth(100).setAppIconHeight(200);
      asset.setPriceMaxSafeLength(10);
      asset.setStoreMaxSafeLength(10);

      if (size >= 2) {
        asset.setRequiredFields(asset.getRecommendedFields());
        asset.clearRequiredFields();
      }
    }

    adSlot.addNativeAdTemplate(asset);

    if (size >= 1) {
      adSlot.addNativeAdTemplate(NativeAdTemplate.newBuilder()
          .setRecommendedFields(NativeAdTemplate.Fields.HEADLINE_VALUE)
          .setRequiredFields(NativeAdTemplate.Fields.HEADLINE_VALUE)
          .setHeadlineMaxSafeLength(99));
    }
  }

  static List<Integer> createSizes(int size, int base) {
    ImmutableList.Builder<Integer> sizes = ImmutableList.builder();
    for (int i = 0; i < size; ++i) {
      sizes.add(base + i);
    }
    return sizes.build();
  }

  @SafeVarargs
  static <T> List<T> sublist(int size, T... items) {
    ImmutableList.Builder<T> sizes = ImmutableList.builder();
    for (int i = 0; i < min(size, items.length); ++i) {
      sizes.add(items[i]);
    }
    return sizes.build();
  }
}
