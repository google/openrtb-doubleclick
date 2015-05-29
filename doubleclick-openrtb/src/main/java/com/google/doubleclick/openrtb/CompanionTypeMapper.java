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

import com.google.openrtb.OpenRtb.BidRequest.Imp.Video.VASTCompanionType;
import com.google.protos.adx.NetworkBid.BidRequest.Video.CompanionSlot.CreativeFormat;

import java.util.Collection;
import java.util.EnumSet;

import javax.annotation.Nullable;

/**
 * Maps between AdX's {@link CreativeFormat} and OpenRTB's {@link VASTCompanionType}.
 */
public class CompanionTypeMapper {
  public static @Nullable VASTCompanionType toOpenRtb(CreativeFormat dc) {
    switch (dc) {
      case IMAGE_CREATIVE:
        return VASTCompanionType.STATIC;
      case FLASH_CREATIVE:
      case HTML_CREATIVE:
        return VASTCompanionType.HTML;
    }
    return null;
  }

  public static @Nullable CreativeFormat toDoubleClick(VASTCompanionType openrtb) {
    switch (openrtb) {
      case STATIC:
        return CreativeFormat.IMAGE_CREATIVE;
      case COMPANION_IFRAME:
      case HTML:
        return CreativeFormat.HTML_CREATIVE;
    }
    return null;
  }

  public static EnumSet<VASTCompanionType> toOpenRtb(
      Collection<CreativeFormat> dcList, EnumSet<VASTCompanionType> openrtbSet) {
    EnumSet<VASTCompanionType> ret = openrtbSet == null
        ? EnumSet.noneOf(VASTCompanionType.class)
            : openrtbSet;
    for (CreativeFormat dc : dcList) {
      ret.add(toOpenRtb(dc));
    }
    return ret;
  }

  public static EnumSet<CreativeFormat> toDoubleClick(
      Collection<VASTCompanionType> openrtbList, EnumSet<CreativeFormat> dcSet) {
    EnumSet<CreativeFormat> ret = dcSet == null ? EnumSet.noneOf(CreativeFormat.class) : null;
    for (VASTCompanionType openrtb : openrtbList) {
      CreativeFormat dc = toDoubleClick(openrtb);
      if (dc != null) {
        ret.add(dc);
      }
    }
    return ret;
  }
}
