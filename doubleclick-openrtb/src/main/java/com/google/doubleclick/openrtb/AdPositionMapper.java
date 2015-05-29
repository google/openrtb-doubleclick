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

import com.google.openrtb.OpenRtb.BidRequest.Imp.AdPosition;
import com.google.protos.adx.NetworkBid.BidRequest.AdSlot.SlotVisibility;

import javax.annotation.Nullable;

/**
 * Maps between AdX's {@link SlotVisibility} and OpenRTB's {@link AdPosition}.
 */
public class AdPositionMapper {
  public static @Nullable AdPosition toOpenRtb(SlotVisibility dc) {
    switch (dc) {
      case ABOVE_THE_FOLD:
        return AdPosition.ABOVE_THE_FOLD;
      case BELOW_THE_FOLD:
        return AdPosition.BELOW_THE_FOLD;
      case NO_DETECTION:
        return null;  // Mapping is UNKNOWN => OpenRTB's default
    }
    return null;
  }

  public static @Nullable SlotVisibility toDoubleClick(AdPosition openrtb) {
    switch (openrtb) {
      case ABOVE_THE_FOLD:
      case AD_POSITION_FULLSCREEN: // Mobile only
      case FOOTER:                 // Mobile only
      case HEADER:                 // Mobile only
      case SIDEBAR:                // Mobile only
        return SlotVisibility.ABOVE_THE_FOLD;
      case BELOW_THE_FOLD:
      case DEPRECATED_LIKELY_BELOW_THE_FOLD:
        return SlotVisibility.BELOW_THE_FOLD;
      case UNKNOWN:
        return null;  // Mapping is NO_DETECTION => AdX's default
    }
    return null;
  }
}
