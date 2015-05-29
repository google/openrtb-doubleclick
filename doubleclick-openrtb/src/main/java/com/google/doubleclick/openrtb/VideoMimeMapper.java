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
import com.google.protos.adx.NetworkBid.BidRequest.Video.VideoFormat;

import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * Maps between AdX's {@link VideoFormat} and OpenRTB's mime types for video.
 */
public class VideoMimeMapper {
  private static ImmutableMap<String, VideoFormat> openrtbToDc =
      ImmutableMap.<String, VideoFormat>builder()
          .put("video/mp4", VideoFormat.VIDEO_HTML5)
          .put("video/webm", VideoFormat.VIDEO_HTML5)
          .put("video/x-flv", VideoFormat.VIDEO_FLASH)
          .build();
  private static ImmutableSet<String>[] dcToOpenrtb = MapperUtil.multimapEnumToSets(
      ImmutableMultimap.<VideoFormat, String>builder()
          .putAll(VideoFormat.VIDEO_FLASH, "video/x-flv")
          .putAll(VideoFormat.VIDEO_HTML5, "video/mp4", "video/webm")
          .putAll(VideoFormat.YT_HOSTED, "video/x-flv", "video/mp4", "video/webm")
          .build());

  public static ImmutableSet<String> toOpenRtb(VideoFormat dc) {
    return MapperUtil.get(dcToOpenrtb, dc);
  }

  public static Set<String> toOpenRtb(Collection<VideoFormat> dcList, Set<String> openrtbSet) {
    Set<String> ret = openrtbSet == null ? new LinkedHashSet<String>() : openrtbSet;
    for (VideoFormat dc : dcList) {
      ret.addAll(toOpenRtb(dc));
    }
    return ret;
  }

  public static @Nullable VideoFormat toDoubleClick(String openrtb) {
    return openrtbToDc.get(openrtb);
  }

  public static EnumSet<VideoFormat> toDoubleClick(
      Collection<String> openrtbList, EnumSet<VideoFormat> dcSet) {
    EnumSet<VideoFormat> ret = dcSet == null ? EnumSet.noneOf(VideoFormat.class) : dcSet;
    for (String openrtb : openrtbList) {
      VideoFormat dc = toDoubleClick(openrtb);
      if (dc != null) {
        ret.add(dc);
      }
    }
    return ret;
  }
}
