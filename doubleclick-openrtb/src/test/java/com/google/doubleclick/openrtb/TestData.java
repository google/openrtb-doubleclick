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

import com.google.doubleclick.Doubleclick;
import com.google.doubleclick.Doubleclick.BidRequest.AdSlot;
import com.google.doubleclick.Doubleclick.BidRequest.AdSlot.MatchingAdData;
import com.google.doubleclick.Doubleclick.BidRequest.AdSlot.SlotVisibility;
import com.google.doubleclick.Doubleclick.BidRequest.Mobile;
import com.google.doubleclick.Doubleclick.BidRequest.Mobile.DeviceOsVersion;
import com.google.doubleclick.Doubleclick.BidRequest.Mobile.MobileDeviceType;
import com.google.doubleclick.Doubleclick.BidRequest.UserDataTreatment;
import com.google.doubleclick.Doubleclick.BidRequest.Video;
import com.google.doubleclick.Doubleclick.BidRequest.Video.CompanionSlot;
import com.google.doubleclick.Doubleclick.BidRequest.Video.CompanionSlot.CreativeFormat;
import com.google.openrtb.OpenRtb.BidResponse.SeatBid.Bid;
import com.google.protobuf.ByteString;

public class TestData {

  public static Bid.Builder newBid(boolean multi) {
    Bid.Builder bid = Bid.newBuilder()
        .setId("0")
        .setImpid("1")
        .setAdid("2")
        .setCrid("4")
        .setDealid("5")
        .setPrice(1000)
        .setAdm("<blink>hello world</blink>");
    if (multi) {
      bid.setCid("3");
      bid.setW(200);
      bid.setH(220);
    }
    return bid;
  }

  public static Doubleclick.BidRequest newRequest() {
    return newRequest(false, false).build();
  }

  public static Doubleclick.BidRequest.Builder newRequest(boolean multi, boolean coppa) {
    AdSlot.Builder adSlot = AdSlot.newBuilder()
        .setId(1)
        .setSlotVisibility(SlotVisibility.ABOVE_THE_FOLD)
        .addAllWidth(multi ? asList(100, 110) : asList(100))
        .addAllHeight(multi ? asList(120, 130) : asList(120))
        .addAllAllowedVendorType(asList(10, 94, 97))
        .addAllExcludedSensitiveCategory(asList(0, 3, 4))
        .addAllExcludedAttribute(asList(1, 2, 3))
        .addAllExcludedProductCategory(asList(13, 14))
        .addTargetableChannel(coppa ? "afv_user_id_PewDiePie" : "pack-anon-x::y")
        .addMatchingAdData(MatchingAdData.newBuilder()
            .setAdgroupId(3)
            .addDirectDeal(MatchingAdData.DirectDeal.newBuilder()
                .setDirectDealId(5)
                .setFixedCpmMicros(200)));
    if (multi) {
      adSlot.addMatchingAdData(MatchingAdData.newBuilder()
          .setAdgroupId(7));
    }
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
        .addDetectedLanguage("en")
        .addDetectedLanguage("pt_BR")
        .addAdslot(adSlot);
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

  static Video.Builder newVideo(boolean multi) {
    return Video.newBuilder()
        .setMinAdDuration(15)
        .setMaxAdDuration(60)
        .setVideoadStartDelay(5)
        .addCompanionSlot(CompanionSlot.newBuilder()
            .addCreativeFormat(CreativeFormat.IMAGE_CREATIVE)
            .addAllWidth(multi ? asList(200, 210) : asList(200))
            .addAllHeight(multi ? asList(220, 230) : asList(220)));
  }
}
