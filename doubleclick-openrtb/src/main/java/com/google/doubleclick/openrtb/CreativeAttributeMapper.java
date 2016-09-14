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
import com.google.openrtb.OpenRtb.CreativeAttribute;

import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * Maps between AdX creative attributes and OpenRTB's {@link CreativeAttribute}.
 */
public class CreativeAttributeMapper {
  private static ImmutableMap<CreativeAttribute, Integer> openrtbToDc =
      ImmutableMap.<CreativeAttribute, Integer>builder()
          // Note: map only attributes from the publisher-excludables
          // or buyer-declarable lists (that have OpenRTB mapping).
          .put(CreativeAttribute.EXPANDABLE_ROLLOVER_INITIATED, 28 /* ROLLOVER_TO_EXPAND */)
          .put(CreativeAttribute.VIDEO_IN_BANNER_AUTO_PLAY, 22 /*VAST_VIDEO */)
          .put(CreativeAttribute.VIDEO_IN_BANNER_USER_INITIATED, 22 /* VAST_VIDEO */)
      .build();
  private static ImmutableSet<CreativeAttribute>[] dcToOpenrtb = MapperUtil.multimapIntToEnumSets(
      ImmutableMultimap.<Integer, CreativeAttribute>builder()
          .putAll(28 /* ROLLOVER_TO_EXPAND */, CreativeAttribute.EXPANDABLE_ROLLOVER_INITIATED)
          .putAll(95 /* IN_BANNER_VIDEO_PUBLISHER_BLOCKABLE */,
              CreativeAttribute.VIDEO_IN_BANNER_AUTO_PLAY,
              CreativeAttribute.VIDEO_IN_BANNER_USER_INITIATED)
          .build());

  public static ImmutableSet<CreativeAttribute> toOpenRtb(int dc) {
    return MapperUtil.get(dcToOpenrtb, dc);
  }

  public static EnumSet<CreativeAttribute> toOpenRtb(
      Collection<Integer> dcList, @Nullable EnumSet<CreativeAttribute> openrtbSet) {
    EnumSet<CreativeAttribute> ret = openrtbSet == null
        ? EnumSet.noneOf(CreativeAttribute.class)
        : openrtbSet;
    for (int dc : dcList) {
      ret.addAll(toOpenRtb(dc));
    }
    return ret;
  }

  public static Integer toDoubleClick(CreativeAttribute openrtb) {
    return openrtbToDc.get(openrtb);
  }

  public static Set<Integer> toDoubleClick(
      Collection<CreativeAttribute> openrtbList, @Nullable Set<Integer> dcSet) {
    Set<Integer> ret = dcSet == null ? new LinkedHashSet<>() : dcSet;
    for (CreativeAttribute openrtb : openrtbList) {
      Integer o = toDoubleClick(openrtb);
      if (o != null) {
        ret.add(o);
      }
    }
    return ret;
  }
}
