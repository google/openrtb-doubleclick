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

import com.google.openrtb.OpenRtb.Flag;
import com.google.protos.adx.NetworkBid.BidRequest.AdSlot.IFramingState;

import javax.annotation.Nullable;

/**
 * Maps between AdX's IFramingState and OpenRTB's topframe.
 */
public class IFramingStateMapper {
  public static @Nullable Flag toOpenRtb(IFramingState dc) {
    switch (dc) {
      case NO_IFRAME:
        return Flag.NO;
      case SAME_DOMAIN_IFRAME:
      case CROSS_DOMAIN_IFRAME:
        return Flag.YES;
      case UNKNOWN_IFRAME_STATE:
      default:
        return null;
    }
  }

  public static @Nullable IFramingState toDoubleClick(@Nullable Flag openrtb) {
    if (openrtb == null) {
      return IFramingState.UNKNOWN_IFRAME_STATE;
    }
    switch (openrtb) {
      case NO:
        return IFramingState.NO_IFRAME;
      case YES:
        return IFramingState.CROSS_DOMAIN_IFRAME;
      default:
        return null;
    }
  }
}
