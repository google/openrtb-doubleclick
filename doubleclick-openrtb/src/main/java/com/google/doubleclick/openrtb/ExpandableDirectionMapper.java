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

import com.google.common.collect.ImmutableSet;
import com.google.openrtb.OpenRtb.BidRequest.Impression.Banner.ExpandableDirection;

import java.util.List;

/**
 * Maps between AdX's {@code excluded_attribute} and OpenRTB's {@link ExpandableDirection}.
 */
public class ExpandableDirectionMapper {
  public static ImmutableSet<ExpandableDirection> toOpenRtb(List<Integer> dcList) {
    boolean left = true, right = true, up = true, down = true;
    for (int dc : dcList) {
      switch (dc) {
        case 13 /* ExpandingDirection: ExpandingUp */:
          up = false;
          break;
        case 14 /* ExpandingDirection: ExpandingDown */:
          down = false;
          break;
        case 15 /* ExpandingDirection: ExpandingLeft */:
          left = false;
          break;
        case 16 /* ExpandingDirection: ExpandingRight */:
          right = false;
          break;
        default:
      }
    }
    ImmutableSet.Builder<ExpandableDirection> openrtbSet = ImmutableSet.builder();
    if (left) {
      openrtbSet.add(ExpandableDirection.LEFT);
    }
    if (right) {
      openrtbSet.add(ExpandableDirection.RIGHT);
    }
    if (up) {
      openrtbSet.add(ExpandableDirection.UP);
    }
    if (down) {
      openrtbSet.add(ExpandableDirection.DOWN);
    }
    return openrtbSet.build();
  }

  public static ImmutableSet<Integer> toDoubleClick(List<ExpandableDirection> openrtbList) {
    boolean left = false, right = false, up = false, down = false;
    for (ExpandableDirection openrtb : openrtbList) {
      switch (openrtb) {
        case LEFT:
          left = true;
          break;
        case RIGHT:
          right = true;
          break;
        case UP:
          up = true;
          break;
        case DOWN:
          down = true;
          break;
        default:
      }
    }
    ImmutableSet.Builder<Integer> dcSet = ImmutableSet.builder();
    if (!left) {
      dcSet.add(15 /* ExpandingDirection: ExpandingLeft */);
    }
    if (!right) {
      dcSet.add(16 /* ExpandingDirection: ExpandingRight */);
    }
    if (!up) {
      dcSet.add(13 /* ExpandingDirection: ExpandingUp */);
    }
    if (!down) {
      dcSet.add(14 /* ExpandingDirection: ExpandingDown */);
    }
    return dcSet.build();
  }
}
