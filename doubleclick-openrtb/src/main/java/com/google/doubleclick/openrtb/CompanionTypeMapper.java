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

import com.google.openrtb.OpenRtb.BidRequest.Impression.Video.CompanionType;
import com.google.protos.adx.NetworkBid.BidRequest.Video.CompanionSlot.CreativeFormat;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Maps between AdX's {@link CreativeFormat} and OpenRTB's {@link CompanionType}.
 */
public class CompanionTypeMapper {
  public static CompanionType toOpenRtb(CreativeFormat dc) {
    switch (dc) {
      case IMAGE_CREATIVE:
        return CompanionType.STATIC;
      case FLASH_CREATIVE:
      case HTML_CREATIVE:
        default:
        return CompanionType.HTML;
    }
  }

  public static CreativeFormat toDoubleClick(CompanionType openrtb) {
    switch (openrtb) {
      case STATIC:
        return CreativeFormat.IMAGE_CREATIVE;
      case HTML:
      case IFRAME:
        default:
        return CreativeFormat.HTML_CREATIVE;
    }
  }

  public static Set<CompanionType> toOpenRtb(
      Collection<CreativeFormat> dcList, Set<CompanionType> openrtbSet) {
    Set<CompanionType> ret = openrtbSet == null ? new LinkedHashSet<CompanionType>() : openrtbSet;
    for (CreativeFormat dc : dcList) {
      ret.add(toOpenRtb(dc));
    }
    return ret;
  }

  public static Set<CreativeFormat> toDoubleClick(
      Collection<CompanionType> openrtbList, Set<CreativeFormat> dcSet) {
    Set<CreativeFormat> ret = dcSet == null ? new LinkedHashSet<CreativeFormat>() : null;
    for (CompanionType openrtb : openrtbList) {
      CreativeFormat dc = toDoubleClick(openrtb);
      if (dc != null) {
        ret.add(dc);
      }
    }
    return ret;
  }
}
