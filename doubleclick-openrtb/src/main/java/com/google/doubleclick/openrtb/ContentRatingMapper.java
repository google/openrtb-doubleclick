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
          .put("DV-G",       39 /* DV_G */)
          .put("DV-PG",      40 /* DV_PG */)
          .put("DV-T",       41 /* DV_T */)
          .put("DV-MA",      42 /* DV_MA */)
          .put("DV-UNRATED", 43 /* UNRATED */)
          .build();
  private static ImmutableMap<Integer, String> dcToOpenrtb =
      ImmutableMap.<Integer, String>builder()
          .put(39 /* DV_G */,    "DV-G")
          .put(40 /* DV_PG */,   "DV-PG")
          .put(41 /* DV_T */,    "DV-T")
          .put(42 /* DV_MA */,   "DV-MA")
          .put(43 /* UNRATED */, "DV-UNRATED")
          .build();

  @Nullable public static String toOpenRtb(int dc) {
    return dcToOpenrtb.get(dc);
  }

  @Nullable public static String toOpenRtb(List<Integer> dcList) {
    int dcMax = -1;
    for (int dc : dcList) {
      if (dc > dcMax) {
        dcMax = dc;
      }
    }
    return dcMax == -1 ? null : toOpenRtb(dcMax);
  }

  @Nullable public static Integer toDoubleClick(String openrtb) {
    return openrtbToDc.get(openrtb);
  }
}
