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

import javax.annotation.Nullable;

/**
 * Maps between AdX's {@link com.google.protos.adx.NetworkBid.BidRequest.Device.DeviceType}
 * and OpenRTB's {@link DeviceType}.
 */
public class DeviceTypeMapper {
  @Nullable public static DeviceType toOpenRtb(com.google.protos.adx.NetworkBid.BidRequest.Device.DeviceType dc) {
    switch (dc) {
      case HIGHEND_PHONE:
        return DeviceType.PHONE;
      case TABLET:
        return DeviceType.TABLET;
      case PERSONAL_COMPUTER:
        return DeviceType.PERSONAL_COMPUTER;
      case CONNECTED_TV:
        return DeviceType.CONNECTED_TV;
      case GAME_CONSOLE:
        return DeviceType.CONNECTED_DEVICE;
      case UNKNOWN_DEVICE:
      default:
        return null;
    }
  }

  @Nullable public static com.google.protos.adx.NetworkBid.BidRequest.Device.DeviceType toDoubleClick(DeviceType openrtb) {
    switch (openrtb) {
      case CONNECTED_TV:
        return com.google.protos.adx.NetworkBid.BidRequest.Device.DeviceType.CONNECTED_TV;
      case MOBILE:
        return com.google.protos.adx.NetworkBid.BidRequest.Device.DeviceType.TABLET;
      case PHONE:
        return com.google.protos.adx.NetworkBid.BidRequest.Device.DeviceType.HIGHEND_PHONE;
      case TABLET:
        return com.google.protos.adx.NetworkBid.BidRequest.Device.DeviceType.TABLET;
      case PERSONAL_COMPUTER:
        return com.google.protos.adx.NetworkBid.BidRequest.Device.DeviceType.PERSONAL_COMPUTER;
      case SET_TOP_BOX:
        return com.google.protos.adx.NetworkBid.BidRequest.Device.DeviceType.CONNECTED_TV;
      case CONNECTED_DEVICE:
      default:
        // Mapping is UNKNOWN => AdX's default
        return null;
    }
  }
}
