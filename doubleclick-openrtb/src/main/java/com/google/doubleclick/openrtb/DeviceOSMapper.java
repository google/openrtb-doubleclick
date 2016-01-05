/*
 * Copyright 2015 Google Inc. All Rights Reserved.
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

import javax.annotation.Nullable;

/**
 * Maps between AdX's {@code platform} and OpenRTB's {@code os}.
 */
public class DeviceOSMapper {
  @Nullable public static String toOpenRtb(String dc) {
    switch (dc) {
      case "iphone":
      case "ipad":
        return "iOS";
      default:
        return dc;
    }
  }

  @Nullable public static String toDoubleClick(String openrtb, boolean isTablet) {
    switch (openrtb) {
      case "iOS":
        return isTablet ? "ipad" : "iphone";
      default:
        return openrtb;
    }
  }
}
