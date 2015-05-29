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
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.protos.adx.NetworkBid.BidRequest.Video.CompanionSlot.CreativeFormat;

import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * Maps between AdX's {@link CreativeFormat} and OpenRTB's mime types for banners.
 */
public class BannerMimeMapper {
  private static ImmutableMap<String, CreativeFormat> openrtbToDc =
      ImmutableMap.<String, CreativeFormat>builder()
          .put("application/javascript", CreativeFormat.HTML_CREATIVE)
          .put("image/gif", CreativeFormat.IMAGE_CREATIVE)
          .put("image/jpeg", CreativeFormat.IMAGE_CREATIVE)
          .put("image/png", CreativeFormat.IMAGE_CREATIVE)
          .put("text/css", CreativeFormat.HTML_CREATIVE)
          .put("text/html", CreativeFormat.HTML_CREATIVE)
          .put("video/x-flv", CreativeFormat.FLASH_CREATIVE)
          .build();
  private static ImmutableSet<String>[] dcToOpenrtb = MapperUtil.multimapEnumToSets(
      ImmutableMultimap.<CreativeFormat, String>builder()
          .putAll(CreativeFormat.FLASH_CREATIVE, "video/x-flv")
          .putAll(CreativeFormat.HTML_CREATIVE, "text/html", "text/css", "application/javascript")
          .putAll(CreativeFormat.IMAGE_CREATIVE, "image/gif", "image/jpeg", "image/png")
          .build());

  public static ImmutableSet<String> toOpenRtb(CreativeFormat dc) {
    return MapperUtil.get(dcToOpenrtb, dc);
  }

  public static Set<String> toOpenRtb(
      Collection<CreativeFormat> dcList, Set<String> openrtbSet) {
    Set<String> ret = openrtbSet == null ? new LinkedHashSet<String>() : openrtbSet;
    for (CreativeFormat dc : dcList) {
      ret.addAll(toOpenRtb(dc));
    }
    return ret;
  }

  public static @Nullable CreativeFormat toDoubleClick(String openrtb) {
    return openrtbToDc.get(openrtb);
  }

  public static EnumSet<CreativeFormat> toDoubleClick(
      Collection<String> openrtbList, EnumSet<CreativeFormat> dcSet) {
    EnumSet<CreativeFormat> ret = dcSet == null ? EnumSet.noneOf(CreativeFormat.class) : dcSet;
    for (String openrtb : openrtbList) {
      CreativeFormat dc = toDoubleClick(openrtb);
      if (dc != null) {
        ret.add(dc);
      }
    }
    return ret;
  }
}
