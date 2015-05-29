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

import com.google.openrtb.OpenRtb.BidRequest.Device.DeviceType;
import com.google.protos.adx.NetworkBid.BidRequest.Mobile.MobileDeviceType;

import javax.annotation.Nullable;

/**
 * Maps between AdX's {@link MobileDeviceType} and OpenRTB's {@link DeviceType}.
 */
public class DeviceTypeMapper {
  public static @Nullable DeviceType toOpenRtb(MobileDeviceType dc) {
    switch (dc) {
      case HIGHEND_PHONE:
        return DeviceType.PHONE;
      case TABLET:
        return DeviceType.TABLET;
      case UNKNOWN:
      default:
        return null;
    }
  }

  public static @Nullable MobileDeviceType toDoubleClick(DeviceType openrtb) {
    switch (openrtb) {
      case CONNECTED_TV:
      case MOBILE:
      case PHONE:
        return MobileDeviceType.HIGHEND_PHONE;
      case TABLET:
        return MobileDeviceType.TABLET;
      case PERSONAL_COMPUTER:
      case SET_TOP_BOX:
        // Mapping is UNKNOWN => AdX's default
      default:
        return null;
    }
  }
}
