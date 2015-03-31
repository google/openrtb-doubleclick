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

import javax.annotation.Nullable;

/**
 * A record from the <a href="https://developers.google.com/ad-exchange/rtb/geotargeting">
 * Geographical Targeting</a> table.
 */
public class GeoTarget {
  final int criteriaId;
  final String name;
  final GeoTarget.CanonicalKey key;
  final GeoTarget parent;
  final String countryCode;

  public GeoTarget(
      int criteriaId, String name, String canonicalName, GeoTarget parent,
      String countryCode, GeoTarget.Type type) {
    this.criteriaId = criteriaId;
    this.name = name;
    this.key = new CanonicalKey(type, canonicalName);
    this.parent = parent;
    this.countryCode = countryCode;
  }

  /**
   * Unique and persistent assigned ID.
   * <p>
   * Example: 1023191.
   */
  public final int getCriteriaId() {
    return criteriaId;
  }

  /**
   * Best available English name of the geo target.
   * <p>
   * Example: "New York".
   */
  public final String getName() {
    return name;
  }

  /**
   * The constructed fully qualified English name consisting of the target's own name,
   * and that of its parent and country. This field is meant only for disambiguating similar
   * target names—it is not yet supported in LocationCriterionService
   * (use location names or criteria IDs instead).
   * <p>
   * Example: "New York,New York,United States".
   */
  public final String getCanonicalName() {
    return key.canonicalName;
  }

  /**
   * The immediate parent of this target. Computed from the canonical names (as indicated
   * by DoubleClick documentation, you shouldn't trust the "Parent Criteria IDs" column
   * so that's not even mapped to this model class).
   * <p>
   * Example: (New York city target) returns (New York state)
   */
  public final @Nullable GeoTarget getParent() {
    return parent;
  }

  /**
   * The ISO-3166-1 alpha-2 country code that is associated with the target.
   * <p>
   * Example: "US". Notice that OpenRTB uses alpha-3 codes (like "USA"), so you may have
   * to convert that via {@link DoubleClickMetadata#getCountryCodes()}.
   */
  public final String getCountryCode() {
    return countryCode;
  }

  /**
   * The target type.
   * <p>
   * Example: (New York city target) returns {@link Type#CITY}
   */
  public final GeoTarget.Type getTargetType() {
    return key.type;
  }

  /**
   * Finds an ancestor of a specific type, if possible.
   * <p>
   * Example: (New York city target, {@link Type#COUNTRY}) returns (US country target)
   */
  public @Nullable GeoTarget getAncestor(GeoTarget.Type type) {
    for (GeoTarget currTarget = this; currTarget != null; currTarget = currTarget.getParent()) {
      if (currTarget.getTargetType() == type) {
        return currTarget;
      }
    }

    return null;
  }

  @Override
  public int hashCode() {
    return criteriaId;
  }

  @Override
  public boolean equals(Object obj) {
    return obj == this
        || (obj instanceof GeoTarget && criteriaId == ((GeoTarget) obj).criteriaId);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).omitNullValues()
        .add("criteriaId", criteriaId)
        .add("name", name)
        .add("canonicalName", key.canonicalName)
        .add("parent", parent == null ? null : parent.getCriteriaId())
        .add("countryCode", countryCode)
        .add("targetType", key.type)
        .toString();
  }

  /**
   * Type of geo target record.
   */
  public static enum Type {
    UNKNOWN,
    OTHER,
    COUNTRY,
    REGION,
    TERRITORY,
    PROVINCE,
    STATE,
    PREFECTURE,
    GOVERNORATE,
    CANTON,
    UNION_TERRITORY,
    AUTONOMOUS_COMMUNITY,
    DMA_REGION,
    METRO,
    CONGRESSIONAL_DISTRICT,
    COUNTY,
    MUNICIPALITY,
    CITY,
    POSTAL_CODE,
    DEPARTMENT,
    AIRPORT,
    TV_REGION,
    OKRUG,
    BOROUGH,
    CITY_REGION,  // Only to be used for Australia.
    ARRONDISSEMENT,
    NEIGHBORHOOD,
    UNIVERSITY,
    DISTRICT,
  }

  static class CanonicalKey {
    final GeoTarget.Type type;
    final String canonicalName;

    CanonicalKey(GeoTarget.Type type, String canonicalName) {
      this.type = type;
      this.canonicalName = canonicalName;
    }

    @Override
    public int hashCode() {
      return type.hashCode() ^ canonicalName.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == this) {
        return true;
      } else if (!(obj instanceof GeoTarget.CanonicalKey)) {
        return false;
      }

      GeoTarget.CanonicalKey other = (GeoTarget.CanonicalKey) obj;
      return type == other.type
          && canonicalName.equals(other.canonicalName);
    }
  }
}
