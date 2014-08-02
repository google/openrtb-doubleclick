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
import com.google.doubleclick.Doubleclick;
import com.google.doubleclick.Doubleclick.BidRequest.AdSlot;
import com.google.doubleclick.Doubleclick.BidRequest.AdSlot.MatchingAdData;
import com.google.doubleclick.Doubleclick.BidRequest.AdSlot.SlotVisibility;
import com.google.doubleclick.Doubleclick.BidRequest.Hyperlocal;
import com.google.doubleclick.Doubleclick.BidRequest.HyperlocalSet;
import com.google.doubleclick.Doubleclick.BidRequest.Mobile;
import com.google.doubleclick.Doubleclick.BidRequest.Mobile.DeviceOsVersion;
import com.google.doubleclick.Doubleclick.BidRequest.Mobile.MobileDeviceType;
import com.google.doubleclick.Doubleclick.BidRequest.UserDataTreatment;
import com.google.doubleclick.Doubleclick.BidRequest.UserDemographic;
import com.google.doubleclick.Doubleclick.BidRequest.Video;
import com.google.doubleclick.Doubleclick.BidRequest.Video.CompanionSlot;
import com.google.doubleclick.Doubleclick.BidRequest.Video.CompanionSlot.CreativeFormat;
import com.google.doubleclick.Doubleclick.BidRequest.Video.VideoFormat;
import com.google.doubleclick.crypto.DoubleClickCrypto;
import com.google.openrtb.OpenRtb.BidResponse.SeatBid.Bid;
import com.google.protobuf.ByteString;

import java.util.List;

public class TestData {
  static final int NO_SLOT = -1;

  public static Bid.Builder newBid(boolean size) {
    Bid.Builder bid = Bid.newBuilder()
        .setId("0")
        .setImpid("1")
        .setAdid("2")
        .setCrid("4")
        .setPrice(1.2)
        .setAdm("<blink>hello world</blink>");
    if (size) {
      bid.setCid("3");
      bid.setDealid("5");
      bid.setW(200);
      bid.setH(220);
    }
    return bid;
  }

  public static Doubleclick.BidRequest newRequest() {
    return newRequest(0, false).build();
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

  public static Doubleclick.BidRequest.Builder newRequest(int size, boolean coppa) {
    Doubleclick.BidRequest.Builder req = Doubleclick.BidRequest.newBuilder()
        .setId(TestUtil.REQUEST_ID)
        .setIp(ByteString.copyFrom(new byte[] { (byte) 192, (byte) 168, (byte) 1 } ))
        .setGoogleUserId("john")
        .setConstrainedUsageGoogleUserId("j")
        .setHostedMatchData(ByteString.EMPTY)
        .setConstrainedUsageHostedMatchData(ByteString.EMPTY)
        .setGeoCriteriaId(9058770)
        .setAnonymousId("mysite.com")
        .setUrl("mysite.com/newsfeed")
        .addAllDetectedLanguage(sublist(size, "en", "en_US", "pt", "pt_BR"))
        .setEncryptedHyperlocalSet(ByteString.copyFrom(
            new DoubleClickCrypto.Hyperlocal(TestUtil.KEYS).encryptHyperlocal(
                HyperlocalSet.newBuilder()
                    .setCenterPoint(Hyperlocal.Point.newBuilder()
                        .setLatitude(45)
                        .setLongitude(90))
                    .build().toByteArray(), new byte[16])))
        .setUserDemographic(UserDemographic.newBuilder()
            .setGender(UserDemographic.Gender.FEMALE)
            .setAgeLow(18)
            .setAgeHigh(24));
    if (size != NO_SLOT) {
      AdSlot.Builder adSlot = AdSlot.newBuilder()
          .setId(1)
          .setSlotVisibility(SlotVisibility.ABOVE_THE_FOLD)
          .addAllWidth(createSizes(size, 100))
          .addAllHeight(createSizes(size, 200))
          .addAllAllowedVendorType(sublist(size, 10, 94, 97))
          .addAllExcludedSensitiveCategory(sublist(size, 0, 3, 4))
          .addAllExcludedAttribute(sublist(size, 1, 2, 3))
          .addAllExcludedProductCategory(sublist(size, 13, 14))
          .addAllTargetableChannel(sublist(size, "afv_user_id_PewDiePie", "pack-anon-x::y"));
      for (int i = 1; i < size; ++i) {
        MatchingAdData.Builder mad = MatchingAdData.newBuilder()
            .setAdgroupId(100 + i);
        if (i >= 2) {
          mad.setMinimumCpmMicros(10000 + i);
          for (int j = 2; j <= i; ++j) {
            MatchingAdData.DirectDeal.Builder deal = MatchingAdData.DirectDeal.newBuilder()
                .setDirectDealId(10 * i + j);
            if (j >= 3) {
              deal.setFixedCpmMicros(1200000);
            }
            mad.addDirectDeal(deal);
          }
        }
        adSlot.addMatchingAdData(mad);
      }
      req.addAdslot(adSlot);
    }
    if (coppa) {
      req.addUserDataTreatment(UserDataTreatment.TAG_FOR_CHILD_DIRECTED_TREATMENT);
    }
    return req;
  }

  static Mobile.Builder newMobile() {
    return Mobile.newBuilder()
        .setAppId("com.mygame")
        .setCarrierId(77777)
        .setPlatform("Android")
        .setMobileDeviceType(MobileDeviceType.HIGHEND_PHONE)
        .setOsVersion(DeviceOsVersion.newBuilder()
            .setOsVersionMajor(3).setOsVersionMinor(2).setOsVersionMicro(1))
        .setModel("MotoX")
        .setEncryptedHashedIdfa(ByteString.EMPTY)
        .setConstrainedUsageEncryptedHashedIdfa(ByteString.EMPTY)
        .setAppName("Tic-Tac-Toe")
        .setAppRating(4.2f);
  }

  static Video.Builder newVideo(int size) {
    Video.Builder video = Video.newBuilder()
        .addAllAllowedVideoFormats(asList(VideoFormat.VIDEO_FLASH, VideoFormat.VIDEO_HTML5))
        .setMinAdDuration(15)
        .setMaxAdDuration(60)
        .setVideoadStartDelay(5);
    if (size != NO_SLOT) {
      CompanionSlot.Builder compSlot = CompanionSlot.newBuilder()
          .addAllWidth(createSizes(size, 100))
          .addAllHeight(createSizes(size, 200));
      if (size >= 2) {
        compSlot.addCreativeFormat(CreativeFormat.IMAGE_CREATIVE);
      }
      video.addCompanionSlot(compSlot);
    }
    return video;
  }
}
