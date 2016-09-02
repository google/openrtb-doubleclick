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

import com.google.openrtb.Gender;
import com.google.protos.adx.NetworkBid.BidRequest.UserDemographic;
import javax.annotation.Nullable;

/**
 * Maps between AdX's
 * {@link com.google.protos.adx.NetworkBid.BidRequest.UserDemographic.Gender}
 * and OpenRTB's {@link com.google.openrtb.Gender}.
 */
public class GenderMapper {
  @Nullable public static Gender toOpenRtb(UserDemographic.Gender dc) {
    switch (dc) {
      case FEMALE:
        return Gender.FEMALE;
      case MALE:
        return Gender.MALE;
      case UNKNOWN:
        return null;
    }
    return null;
  }

  @Nullable public static UserDemographic.Gender toDoubleClick(Gender openrtb) {
    switch (openrtb) {
      case FEMALE:
        return UserDemographic.Gender.FEMALE;
      case MALE:
        return UserDemographic.Gender.MALE;
      case OTHER:
        return null;  // Maping is UNKNOWN => AdX's default
    }
    return null;
  }
}
