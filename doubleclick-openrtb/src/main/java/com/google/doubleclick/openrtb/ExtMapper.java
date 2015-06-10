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

import com.google.openrtb.OpenRtb;
import com.google.openrtb.OpenRtbNative;
import com.google.protos.adx.NetworkBid;

/**
 * Extension mapper for {@link DoubleClickOpenRtbMapper}. The core mapper only handles the
 * properties that can be mapped to standard OpenRTB fields; you must provide one or
 * more extension mappers to perform other mappings (usually from/to OpenRTB extensions).
 */
public abstract class ExtMapper {
  protected ExtMapper() {
  }

  public void toOpenRtbBidRequest(
      NetworkBid.BidRequest dcRequest, OpenRtb.BidRequest.Builder request) {
  }

  public void toOpenRtbUser(
      NetworkBid.BidRequest dcRequest, OpenRtb.BidRequest.User.Builder user) {
  }

  public void toOpenRtbSite(NetworkBid.BidRequest dcRequest, OpenRtb.BidRequest.Site.Builder site) {
  }

  public void toOpenRtbApp(NetworkBid.BidRequest dcRequest, OpenRtb.BidRequest.App.Builder app) {
  }

  public void toOpenRtbDevice(NetworkBid.BidRequest dcRequest,
      OpenRtb.BidRequest.Device.Builder device) {
  }

  public void toOpenRtbImp(NetworkBid.BidRequest.AdSlot dcSlot,
      OpenRtb.BidRequest.Imp.Builder imp) {
  }

  public void toOpenRtbBanner(NetworkBid.BidRequest.AdSlot dcSlot,
      OpenRtb.BidRequest.Imp.Banner.Builder banner) {
  }

  public void toOpenRtbPmp(NetworkBid.BidRequest.AdSlot.MatchingAdData dcAdData,
      OpenRtb.BidRequest.Imp.Pmp.Builder pmp) {
  }

  public void toOpenRtbNative(
      NetworkBid.BidRequest.AdSlot.NativeAdTemplate dcNativ,
      OpenRtbNative.NativeRequest.Asset.Builder asset) {
  }

  public void toOpenRtbVideo(NetworkBid.BidRequest.Video dcVideo,
      OpenRtb.BidRequest.Imp.Video.Builder video) {
  }

  public void toOpenRtbGeo(NetworkBid.BidRequest dcRequest, OpenRtb.BidRequest.Geo.Builder geo,
      NetworkBid.BidRequest.HyperlocalSet hyperlocalSet) {
  }

  public void toDoubleClickAd(OpenRtb.BidRequest request,
      OpenRtb.BidResponse.SeatBid.Bid bid, NetworkBid.BidResponse.Ad.Builder dcAd) {
  }
}
