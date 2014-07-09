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

import com.google.doubleclick.Doubleclick.BidRequest.AdSlot.SlotVisibility;
import com.google.openrtb.OpenRtb.BidRequest.Impression.AdPosition;

/**
 * Maps between AdX's {@link SlotVisibility} and OpenRTB's {@link AdPosition}.
 */
public class AdPositionMapper {
  public static AdPosition toOpenRtb(SlotVisibility dc) {
    switch (dc) {
      case ABOVE_THE_FOLD:
        return AdPosition.ABOVE_THE_FOLD;
      case BELOW_THE_FOLD:
        return AdPosition.BELOW_THE_FOLD;
      case NO_DETECTION:
      default:
        return AdPosition.POSITION_UNKNOWN;
    }
  }

  public static SlotVisibility toDoubleClick(AdPosition openrtb) {
    switch (openrtb) {
      case ABOVE_THE_FOLD:
        return SlotVisibility.ABOVE_THE_FOLD;
      case BELOW_THE_FOLD:
        return SlotVisibility.BELOW_THE_FOLD;
      case HEADER:     // Mobile only
      case FOOTER:     // Mobile only
      case SIDEBAR:    // Mobile only
      case FULLSCREEN: // Mobile only
      case POSITION_UNKNOWN:
      default:
        return SlotVisibility.NO_DETECTION;
    }
  }
}
