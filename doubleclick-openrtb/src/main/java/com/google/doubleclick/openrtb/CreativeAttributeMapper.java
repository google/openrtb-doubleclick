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

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.openrtb.OpenRtb.CreativeAttribute;

import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Maps between AdX creative attributes and OpenRTB's {@link CreativeAttribute}.
 */
public class CreativeAttributeMapper {
  private static ImmutableSetMultimap<CreativeAttribute, Integer> openrtbToDc =
      ImmutableSetMultimap.<CreativeAttribute, Integer>builder()
          // Empty mappings listed only for documentation
          .putAll(CreativeAttribute.AD_CAN_BE_SKIPPED, 44)
          .putAll(CreativeAttribute.ANNOYING)
          .putAll(CreativeAttribute.AUDIO_AUTO_PLAY)
          .putAll(CreativeAttribute.AUDIO_USER_INITIATED)
          .putAll(CreativeAttribute.EXPANDABLE_AUTOMATIC)
          .putAll(CreativeAttribute.EXPANDABLE_CLICK_INITIATED)
          .putAll(CreativeAttribute.EXPANDABLE_ROLLOVER_INITIATED, 28)
          .putAll(CreativeAttribute.HAS_AUDIO_ON_OFF_BUTTON)
          .putAll(CreativeAttribute.POP)
          .putAll(CreativeAttribute.PROVOCATIVE_OR_SUGGESTIVE)
          .putAll(CreativeAttribute.SURVEYS)
          .putAll(CreativeAttribute.TEXT_ONLY, 1)
          .putAll(CreativeAttribute.USER_INTERACTIVE)
          .putAll(CreativeAttribute.VIDEO_IN_BANNER_AUTO_PLAY, 2, 22)
          .putAll(CreativeAttribute.VIDEO_IN_BANNER_USER_INITIATED, 2, 22)
          .putAll(CreativeAttribute.WINDOWS_DIALOG_OR_ALERT_STYLE)
      .build();
  private static ImmutableSet<CreativeAttribute>[] dcToOpenrtb = MapperUtil.multimapIntToEnumSets(
      ImmutableMultimap.<Integer, CreativeAttribute>builder()
          .putAll(1, CreativeAttribute.TEXT_ONLY)
          .putAll(28, CreativeAttribute.EXPANDABLE_ROLLOVER_INITIATED)
          .putAll(32, CreativeAttribute.USER_INTERACTIVE)
          .putAll(44, CreativeAttribute.AD_CAN_BE_SKIPPED)
          .build());

  public static ImmutableSet<CreativeAttribute> toOpenRtb(int dc) {
    return MapperUtil.get(dcToOpenrtb, dc);
  }

  public static ImmutableSet<Integer> toDoubleClick(CreativeAttribute openrtb) {
    return openrtbToDc.get(openrtb);
  }

  public static EnumSet<CreativeAttribute> toOpenRtb(
      Collection<Integer> dcList, EnumSet<CreativeAttribute> openrtbSet) {
    EnumSet<CreativeAttribute> ret = openrtbSet == null
        ? EnumSet.noneOf(CreativeAttribute.class)
        : openrtbSet;
    for (int dc : dcList) {
      ret.addAll(toOpenRtb(dc));
    }
    return ret;
  }

  public static Set<Integer> toDoubleClick(
      Collection<CreativeAttribute> openrtbList, Set<Integer> dcSet) {
    Set<Integer> ret = dcSet == null ? new LinkedHashSet<Integer>() : dcSet;
    for (CreativeAttribute openrtb : openrtbList) {
      ret.addAll(toDoubleClick(openrtb));
    }
    return ret;
  }
}
