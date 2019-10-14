/*
 * Copyright 2016 Google Inc. All Rights Reserved.
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

package com.google.doubleclick.openrtb.json;

import com.google.doubleclick.AdxExt;
import com.google.doubleclick.AdxExt.AppExt;
import com.google.doubleclick.AdxExt.BidExt;
import com.google.doubleclick.AdxExt.BidRequestExt;
import com.google.doubleclick.AdxExt.BidResponseExt;
import com.google.doubleclick.AdxExt.DealExt;
import com.google.doubleclick.AdxExt.EventTrackerExt;
import com.google.doubleclick.AdxExt.ImpExt;
import com.google.doubleclick.AdxExt.NativeRequestExt;
import com.google.doubleclick.AdxExt.RegsExt;
import com.google.doubleclick.AdxExt.SiteExt;
import com.google.doubleclick.AdxExt.UserExt;
import com.google.openrtb.OpenRtb.BidRequest;
import com.google.openrtb.OpenRtb.BidRequest.App;
import com.google.openrtb.OpenRtb.BidRequest.Imp;
import com.google.openrtb.OpenRtb.BidRequest.Imp.Pmp.Deal;
import com.google.openrtb.OpenRtb.BidRequest.Regs;
import com.google.openrtb.OpenRtb.BidRequest.Site;
import com.google.openrtb.OpenRtb.BidRequest.User;
import com.google.openrtb.OpenRtb.BidResponse;
import com.google.openrtb.OpenRtb.BidResponse.SeatBid.Bid;
import com.google.openrtb.OpenRtb.NativeRequest;
import com.google.openrtb.OpenRtb.NativeResponse.EventTracker;
import com.google.openrtb.json.OpenRtbJsonFactory;

/**
 * Utilities to support the AdX/OpenRTB extensions.
 */
public class AdxExtUtils {

  /**
   * Configures a JSON factory with support for the {@link AdxExt} extensions.
   */
  public static OpenRtbJsonFactory registerAdxExt(OpenRtbJsonFactory factory) {
    return factory
        .register(new BidRequestExtReader(), BidRequest.Builder.class)
        .register(new BidRequestExtWriter(), BidRequestExt.class, BidRequest.class)
        .register(new ImpExtReader(), Imp.Builder.class)
        .register(new ImpExtWriter(), ImpExt.class, Imp.class)
        .register(new BidExtReader(), Bid.Builder.class)
        .register(new BidExtWriter(), BidExt.class, Bid.class)
        .register(new BidResponseExtReader(), BidResponse.Builder.class)
        .register(new BidResponseExtWriter(), BidResponseExt.class, BidResponse.class)
        .register(new SiteExtReader(), Site.Builder.class)
        .register(new SiteExtWriter(), SiteExt.class, Site.class)
        .register(new AppExtReader(), App.Builder.class)
        .register(new AppExtWriter(), AppExt.class, App.class)
        .register(new NativeRequestExtReader(), NativeRequest.Builder.class)
        .register(new NativeRequestExtWriter(), NativeRequestExt.class, NativeRequest.class)
        .register(new UserExtReader(), User.Builder.class)
        .register(new UserExtWriter(), UserExt.class, User.class)
        .register(new RegsExtReader(), Regs.Builder.class)
        .register(new RegsExtWriter(), RegsExt.class, Regs.class)
        .register(new EventTrackerExtReader(), EventTracker.Builder.class)
        .register(new EventTrackerExtWriter(), EventTrackerExt.class, EventTracker.class)
        .register(new DealExtReader(), Deal.Builder.class)
        .register(new DealExtWriter(), DealExt.class, Deal.class);
  }
}
