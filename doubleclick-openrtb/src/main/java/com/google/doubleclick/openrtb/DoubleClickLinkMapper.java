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

import com.google.doubleclick.DcExt;
import com.google.doubleclick.Doubleclick;
import com.google.doubleclick.Doubleclick.BidRequest.AdSlot;
import com.google.openrtb.OpenRtb;
import com.google.openrtb.OpenRtb.BidRequest.Impression.Builder;

/**
 * Extension mapper for DoubleClick "Link"extensions: each OpenRTB object will have an
 * extension that's just a reference for the corresponding node in the native message
 * (which also happens to be protobuf-based, so we can do this).
 */
public class DoubleClickLinkMapper extends ExtMapper {
  public static final ExtMapper INSTANCE = new DoubleClickLinkMapper();

  private DoubleClickLinkMapper() {
  }

  @Override public void toOpenRtb(Doubleclick.BidRequest dcRequest,
      OpenRtb.BidRequest.Builder request) {
    request.setExtension(DcExt.bidRequest, dcRequest);
  }

  @Override public void toOpenRtb(AdSlot dcSlot, Builder imp) {
    imp.setExtension(DcExt.adSlot, dcSlot);
  }

  @Override public void toOpenRtb(Doubleclick.BidRequest.Video dcVideo,
      OpenRtb.BidRequest.Impression.Video.Builder video) {
    video.setExtension(DcExt.video, dcVideo);
  }

  @Override public void toOpenRtb(Doubleclick.BidRequest dcRequest,
      OpenRtb.BidRequest.Device.Builder device) {
    if (dcRequest.hasMobile()) {
      device.setExtension(DcExt.mobile, dcRequest.getMobile());
    }
  }

  @Override public void toOpenRtb(Doubleclick.BidRequest.AdSlot.MatchingAdData dcAdData,
      OpenRtb.BidRequest.Impression.PMP.Builder pmp) {
    pmp.setExtension(DcExt.adData, dcAdData);
  }
}
