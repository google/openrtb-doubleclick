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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.MoreObjects;

import javax.annotation.Nullable;

/**
 * A record from the <a href="https://developers.google.com/ad-exchange/rtb/geotargeting">
 * Geographical Targeting</a> table.
 */
public class GeoTarget {
  private final int criteriaId;
  private final String name;
  private final GeoTarget.CanonicalKey key;
  private final String countryCode;
  private GeoTarget canonParent;
  private GeoTarget idParent;

  public GeoTarget(
      int criteriaId, GeoTarget.Type type, String canonicalName, String name, String countryCode,
      GeoTarget canonParent, GeoTarget idParent) {
    this(criteriaId, new CanonicalKey(type, canonicalName),
        name, countryCode, canonParent, idParent);
  }

  GeoTarget(
      int criteriaId, CanonicalKey key, String name, String countryCode, GeoTarget canonParent,
      GeoTarget idParent) {
    this.criteriaId = criteriaId;
    this.key = key;
    this.name = name;
    this.countryCode = countryCode;
    this.canonParent = canonParent;
    this.idParent = idParent;
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

  final CanonicalKey getKey() {
    return key;
  }

  /**
   * The immediate parent of this target. Computed from the canonical names (as indicated
   * by DoubleClick documentation, you shouldn't trust the "Parent Criteria IDs" column
   * so that's not even mapped to this model class).
   * <p>
   * Example: (New York city target) returns (New York state)
   */
  public final @Nullable GeoTarget getCanonParent() {
    return canonParent;
  }

  final void setCanonParent(GeoTarget canonParent) {
    checkState(this.canonParent == null);
    this.canonParent = checkNotNull(canonParent);
  }

  /**
   * The immediate parent of this target. Computed from the Parent Criteria IDs column,
   * which is not the preferred option but sometimes contains more detailed information
   * than the canonical names.
   * You should prefer {@link #getCanonParent()}, using this method as last resort.
   * <p>
   * Example: (33611 postal code) returns (Tampa city); which is the only way to get
   * that city, since the parent by canonical name is (Florida state), skipping the city.
   */
  public final @Nullable GeoTarget getIdParent() {
    return idParent;
  }

  final void setIdParent(GeoTarget idParent) {
    checkState(this.idParent == null);
    this.idParent = checkNotNull(idParent);
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
  public final GeoTarget.Type getType() {
    return key.type;
  }

  /**
   * Finds an ancestor of a specific type, if possible.
   * <p>
   * Example: (New York city target, {@link Type#COUNTRY}) returns (US country target)
   */
  public @Nullable GeoTarget getCanonAncestor(GeoTarget.Type type) {
    for (GeoTarget target = this; target != null; target = target.getCanonParent()) {
      if (target.getType() == type) {
        return target;
      }
    }

    return null;
  }

  /**
   * Finds an ancestor of a specific type, if possible, using the parent-ID chain.
   * You should prefer {@link #getCanonAncestor(Type)}, using this method as last resort.
   */
  public @Nullable GeoTarget getIdAncestor(GeoTarget.Type type) {
    for (GeoTarget target = this; target != null; target = target.getIdParent()) {
      if (target.getType() == type) {
        return target;
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
        .add("canonParent", canonParent == null ? null : canonParent.getCriteriaId())
        .add("idParent",
            idParent == null || idParent == canonParent ? null : idParent.getCriteriaId())
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

    @Override
    public String toString() {
      return type.name() + ' ' + canonicalName;
    }
  }

  /**
   * Parent IDs are legacy, and some records are inconsistent, so we following AdX's docs
   * and build hierarchy by the canonical names. Notice canonical names are not always
   * unique for leaf records, e.g. "New York,New York,United States" can be either the
   * City (1023191) or the County (9058761); that's why we need the TargetType to compose
   * a unique CanonicalKey. But parent records are unique, e.g. "New York,United States"
   * = State (21167), which is parent for both NY/City and NY/County. The hierarchy by
   * parent IDs can be different, eg: NY/City < Queens/County < NY/State <- US/Country.
   * To make this even more interesting, we can have records like this:
   * <pre>
   * name          = "Champaign & Springfield-Decatur,IL"
   * canonicalName = "Champaign & Springfield-Decatur,IL,Illinois,United States"
   * </pre>
   * Simply using the first comma to split the parent's canonical name will not work,
   * so we need a special case: if the name contains a comma, use its length as a prefix
   * for splitting. (Cannot use this rule for every record either, because that would fail
   * in a few records like "Burgos" / "Province of Burgos,Castile and Leon,Spain").
   * <p>
   * Some records fail to match the parent by canonical name, for example
   * "Zalau,Salaj County,Romania", the parent record is "Salaj,Romania".
   */
  String findCanonParentName() {
    int pos = name.indexOf(',');
    if (pos == -1) {
      int canonPos = key.canonicalName.indexOf(',');
      return canonPos == -1 ? null : key.canonicalName.substring(canonPos + 1);
    } else {
      int commas = 1;
      for (int i = pos + 1; i < name.length(); ++i) {
        if (name.charAt(i) == ',') {
          ++commas;
        }
      }
      int canonPos;
      for (canonPos = 0; canonPos < key.canonicalName.length() && commas >= 0; ++canonPos) {
        if (key.canonicalName.charAt(canonPos) == ',') {
          --commas;
        }
      }
      if (commas == 0) {
        return null;
      } else if (commas != -1 || canonPos == key.canonicalName.length()) {
        return null;
      } else {
        return key.canonicalName.substring(canonPos + 1);
      }
    }
  }
}
