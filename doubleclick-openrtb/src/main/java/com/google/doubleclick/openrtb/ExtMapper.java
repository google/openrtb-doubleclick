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
import com.google.protos.adx.NetworkBid;

/**
 * Extension mapper for {@link DoubleClickOpenRtbMapper}. The core mapper only handles the
 * properties that can be mapped to standard OpenRTB fields; you must provide one or
 * more extension mappers to perform other mappings (usually from/to OpenRTB extensions).
 * All methods return {@code true} if they add some extension field, {@code false} if not.
 */
public abstract class ExtMapper {
  protected ExtMapper() {
  }

  public boolean toOpenRtbBidRequest(
      NetworkBid.BidRequest dcRequest, OpenRtb.BidRequest.Builder request) {
    return false;
  }

  public boolean toOpenRtbUser(
      NetworkBid.BidRequest dcRequest, OpenRtb.BidRequest.User.Builder user) {
    return false;
  }

  public boolean toOpenRtbSite(
      NetworkBid.BidRequest dcRequest, OpenRtb.BidRequest.Site.Builder site) {
    return false;
  }

  public boolean toOpenRtbApp(
      NetworkBid.BidRequest dcRequest, OpenRtb.BidRequest.App.Builder app) {
    return false;
  }

  public boolean toOpenRtbDevice(NetworkBid.BidRequest dcRequest,
      OpenRtb.BidRequest.Device.Builder device) {
    return false;
  }

  public boolean toOpenRtbImp(NetworkBid.BidRequest.AdSlot dcSlot,
      OpenRtb.BidRequest.Imp.Builder imp) {
    return false;
  }

  public boolean toOpenRtbBanner(NetworkBid.BidRequest.AdSlot dcSlot,
      OpenRtb.BidRequest.Imp.Banner.Builder banner) {
    return false;
  }

  public boolean toOpenRtbPmp(NetworkBid.BidRequest.AdSlot.MatchingAdData dcAdData,
      OpenRtb.BidRequest.Imp.Pmp.Builder pmp) {
    return false;
  }

  public boolean toOpenRtbNative(
      NetworkBid.BidRequest.AdSlot.NativeAdTemplate dcNativ,
      OpenRtb.NativeRequest.Asset.Builder asset) {
    return false;
  }

  public boolean toOpenRtbVideo(NetworkBid.BidRequest.Video dcVideo,
      OpenRtb.BidRequest.Imp.Video.Builder video) {
    return false;
  }

  public boolean toOpenRtbGeo(
      NetworkBid.BidRequest dcRequest, OpenRtb.BidRequest.Geo.Builder geo,
      NetworkBid.BidRequest.HyperlocalSet hyperlocalSet) {
    return false;
  }

  public boolean toDoubleClickAd(OpenRtb.BidRequest request,
      OpenRtb.BidResponse.SeatBid.Bid bid, NetworkBid.BidResponse.Ad.Builder dcAd) {
    return false;
  }
}
