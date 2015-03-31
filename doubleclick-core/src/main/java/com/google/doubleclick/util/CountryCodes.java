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

package com.google.doubleclick.util;

import com.google.common.base.MoreObjects;

/**
 * Stores ISO 3166-1 country codes.
 */
public class CountryCodes {
  private final int numeric;
  private final String alpha2;
  private final String alpha3;

  public CountryCodes(int numeric, String alpha2, String alpha3) {
    this.numeric = numeric;
    this.alpha2 = alpha2;
    this.alpha3 = alpha3;
  }

  /**
   * The numeric code. Example: United States = 840.
   */
  public final int getNumeric() {
    return numeric;
  }

  /**
   * The alpha-2 code. Example: United States = "US".
   */
  public final String getAlpha2() {
    return alpha2;
  }

  /**
   * The alpha-3 code. Example: United States = "USA".
   */
  public final String getAlpha3() {
    return alpha3;
  }

  @Override
  public int hashCode() {
    return numeric;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    } else if (!(obj instanceof CountryCodes)) {
      return false;
    }

    CountryCodes other = (CountryCodes) obj;
    return numeric == other.numeric
        && alpha2.equals(other.alpha2)
        && alpha3.equals(other.alpha3);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).omitNullValues()
        .add("numeric", numeric)
        .add("alpha2", alpha2)
        .add("alpha3", alpha3)
        .toString();
  }
}
