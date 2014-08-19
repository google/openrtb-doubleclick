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

import com.google.common.collect.ImmutableMap;

import java.util.List;

import javax.annotation.Nullable;

/**
 * Maps between AdX's {@code detected_content_label} and OpenRTB's {@code contentrating}.
 */
public class ContentRatingMapper {
  private static ImmutableMap<String, Integer> openrtbToDc =
      ImmutableMap.<String, Integer>builder()
          .put("DV-G", 39)
          .put("DV-PG", 40)
          .put("DV-T", 41)
          .put("DV-MA", 42)
          .put("DV-UNRATED", 43)
          .build();
  private static ImmutableMap<Integer, String> dcToOpenrtb =
      ImmutableMap.<Integer, String>builder()
          .put(39, "DV-G")
          .put(40, "DV-PG")
          .put(41, "DV-T")
          .put(42, "DV-MA")
          .put(43, "DV-UNRATED")
          .build();

  public static @Nullable String toOpenRtb(int dc) {
    return dcToOpenrtb.get(dc);
  }

  public static @Nullable Integer toDoubleClick(String openrtb) {
    return openrtbToDc.get(openrtb);
  }

  public static @Nullable String toOpenRtb(List<Integer> dcList) {
    int dcMax = -1;
    for (int dc : dcList) {
      if (dc > dcMax) {
        dcMax = dc;
      }
    }
    return dcMax == -1 ? null : toOpenRtb(dcMax);
  }
}
