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

import static org.junit.Assert.assertSame;

import com.google.doubleclick.Doubleclick.BidRequest.Mobile.MobileDeviceType;
import com.google.openrtb.OpenRtb.BidRequest.Device.DeviceType;

import org.junit.Test;

public class DeviceTypeMapperTest {
  @Test
  public void testMapper() {
    assertSame(
        DeviceType.MOBILE,
        DeviceTypeMapper.toOpenRtb(MobileDeviceType.HIGHEND_PHONE));
    assertSame(
        MobileDeviceType.HIGHEND_PHONE,
        DeviceTypeMapper.toDoubleClick(DeviceType.MOBILE));

    for (DeviceType openrtb : DeviceType.values()) {
      DeviceTypeMapper.toDoubleClick(openrtb);
    }
    for (MobileDeviceType dc : MobileDeviceType.values()) {
      DeviceTypeMapper.toOpenRtb(dc);
    }
  }
}
