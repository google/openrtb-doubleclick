/*
 * Copyright 2015 Google Inc. All Rights Reserved.
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

package com.google.doubleclick.util;

import com.google.common.base.MoreObjects;

/**
 * Value for the DMA Region mapping.
 */
public class CityDMARegionValue {
  private final int regionCode;
  private final String city;
  private final String state;

  public CityDMARegionValue(int regionCode, String city, String state) {
    this.regionCode = regionCode;
    this.city = city;
    this.state = state;
  }

  public final int regionCode() {
    return regionCode;
  }

  public final String city() {
    return city;
  }

  public final String state() {
    return state;
  }

  @Override public int hashCode() {
    return regionCode ^ state.hashCode();
  }

  @Override public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    } else if (!(obj instanceof CityDMARegionValue)) {
      return false;
    }

    CityDMARegionValue other = (CityDMARegionValue) obj;
    return regionCode == other.regionCode && city.equals(other.city) && state.equals(other.state);
  }

  @Override public String toString() {
    return MoreObjects.toStringHelper(this).omitNullValues()
        .add("regionCode", regionCode)
        .add("city", city)
        .add("state", state)
        .toString();
  }
}