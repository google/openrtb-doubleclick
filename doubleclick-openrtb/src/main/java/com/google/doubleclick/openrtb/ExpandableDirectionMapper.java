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

import com.google.openrtb.OpenRtb.BidRequest.Imp.Banner.ExpandableDirection;

import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Maps between AdX's {@code excluded_attribute} and OpenRTB's {@link ExpandableDirection}.
 */
public class ExpandableDirectionMapper {

  /**
   * Note: Mapping is inverse, from excluded to included.
   */
  public static EnumSet<ExpandableDirection> toOpenRtb(Collection<Integer> dcList) {
    EnumSet<ExpandableDirection> openrtbSet = EnumSet.allOf(ExpandableDirection.class);
    openrtbSet.remove(ExpandableDirection.EXPANDABLE_FULLSCREEN);
    for (int dc : dcList) {
      switch (dc) {
        case 13 /* ExpandingDirection: ExpandingUp */:
          openrtbSet.remove(ExpandableDirection.UP);
          break;
        case 14 /* ExpandingDirection: ExpandingDown */:
          openrtbSet.remove(ExpandableDirection.DOWN);
          break;
        case 15 /* ExpandingDirection: ExpandingLeft */:
          openrtbSet.remove(ExpandableDirection.LEFT);
          break;
        case 16 /* ExpandingDirection: ExpandingRight */:
          openrtbSet.remove(ExpandableDirection.RIGHT);
          break;
        case 17 /* ExpandingDirection: ExpandingUpLeft */:
          openrtbSet.remove(ExpandableDirection.UP);
          openrtbSet.remove(ExpandableDirection.LEFT);
          break;
        case 18 /* ExpandingDirection: ExpandingUpRight */:
          openrtbSet.remove(ExpandableDirection.UP);
          openrtbSet.remove(ExpandableDirection.RIGHT);
          break;
        case 19 /* ExpandingDirection: ExpandingDownLeft */:
          openrtbSet.remove(ExpandableDirection.DOWN);
          openrtbSet.remove(ExpandableDirection.LEFT);
          break;
        case 20 /* ExpandingDirection: ExpandingDownRight */:
          openrtbSet.remove(ExpandableDirection.DOWN);
          openrtbSet.remove(ExpandableDirection.RIGHT);
          break;
        case 25 /* ExpandingDirection: ExpandingUpOrDown */:
          openrtbSet.remove(ExpandableDirection.UP);
          openrtbSet.remove(ExpandableDirection.DOWN);
          break;
        case 26 /* ExpandingDirection: ExpandingLeftOrRight */:
          openrtbSet.remove(ExpandableDirection.LEFT);
          openrtbSet.remove(ExpandableDirection.RIGHT);
          break;
        case 27 /* ExpandingDirection: ExpandingAnyDiagonal */:
          openrtbSet.remove(ExpandableDirection.UP);
          openrtbSet.remove(ExpandableDirection.DOWN);
          openrtbSet.remove(ExpandableDirection.LEFT);
          openrtbSet.remove(ExpandableDirection.RIGHT);
          break;
      }
    }
    return openrtbSet;
  }

  /**
   * Note: Mapping is inverse, from included to excluded.
   */
  public static Set<Integer> toDoubleClick(Collection<ExpandableDirection> openrtbList) {
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
        case EXPANDABLE_FULLSCREEN:
          break;
      }
    }
    LinkedHashSet<Integer> dcSet = new LinkedHashSet<>();
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
    if (!up || !left) {
      dcSet.add(17 /* ExpandingDirection: ExpandingUpLeft */);
    }
    if (!up || !right) {
      dcSet.add(18 /* ExpandingDirection: ExpandingUpRight */);
    }
    if (!down || !left) {
      dcSet.add(19 /* ExpandingDirection: ExpandingDownLeft */);
    }
    if (!down || !right) {
      dcSet.add(20 /* ExpandingDirection: ExpandingDownRight */);
    }
    if (!up || !down) {
      dcSet.add(25 /* ExpandingDirection: ExpandingUpOrDown */);
    }
    if (!left || !right) {
      dcSet.add(26 /* ExpandingDirection: ExpandingLeftOrRight */);
    }
    if (!up || !down || !left || !right) {
      dcSet.add(27 /* ExpandingDirection: ExpandingAnyDiagonal */);
    }
    return dcSet;
  }
}
