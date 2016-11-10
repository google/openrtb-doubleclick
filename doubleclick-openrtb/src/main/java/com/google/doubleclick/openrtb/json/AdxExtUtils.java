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
import com.google.doubleclick.AdxExt.BidExt;
import com.google.doubleclick.AdxExt.BidResponseExt;
import com.google.doubleclick.AdxExt.ImpExt;
import com.google.doubleclick.AdxExt.NativeRequestExt;
import com.google.openrtb.OpenRtb.BidRequest.Imp;
import com.google.openrtb.OpenRtb.BidResponse;
import com.google.openrtb.OpenRtb.BidResponse.SeatBid.Bid;
import com.google.openrtb.OpenRtb.NativeRequest;
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
        .register(new ImpExtReader(), Imp.Builder.class)
        .register(new ImpExtWriter(), ImpExt.class, Imp.class)
        .register(new BidExtReader(), Bid.Builder.class)
        .register(new BidExtWriter(), BidExt.class, Bid.class)
        .register(new BidResponseExtReader(), BidResponse.Builder.class)
        .register(new BidResponseExtWriter(), BidResponseExt.class, BidResponse.class)
        .register(new NativeRequestExtReader(), NativeRequest.Builder.class)
        .register(new NativeRequestExtWriter(), NativeRequestExt.class, NativeRequest.class);
  }
}
