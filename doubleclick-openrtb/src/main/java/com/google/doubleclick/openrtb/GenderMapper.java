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

import com.google.protos.adx.NetworkBid.BidRequest.UserDemographic.Gender;

/**
 * Maps between AdX's {@link Gender} and OpenRTB's {@code gender}.
 */
public class GenderMapper {
  public static String toOpenRtb(Gender dc) {
    switch (dc) {
      case MALE:
        return "M";
      case FEMALE:
        return "F";
      case UNKNOWN:
      default:
        return "O";
    }
  }

  public static Gender toDoubleClick(String gender) {
    switch (gender) {
      case "M":
        return Gender.MALE;
      case "F":
        return Gender.FEMALE;
      case "O":
      default:
        return Gender.UNKNOWN;
    }
  }
}
