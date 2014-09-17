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

import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableMap;
import com.google.doubleclick.util.DoubleClickMetadata.GeoTarget.TargetType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * DoubleClickMetadata for DoubleClick Ad Exchange dictionaries. Helps code that need to
 * inspect or manipulate several categories of numeric IDs embedded in DoubleClick's protobuf.
 * See <a href="https://developers.google.com/ad-exchange/rtb/downloads">
 * DoubleClick Ad Exchange Real-Time Bidding Protocol</a>, this class mostly encapsulates
 * the .txt and .csv files obtained there (loaded by HTTP at startup).
 * <p>
 * Note: validation is lenient: invalid codes are logged, but no failure is raised here
 * if the bidder is using old metadata. If a code is really invalid, AdX may reject the bid.
 * <p>
 * This class is threadsafe, as well as all nested helper classes. It's recommended to create
 * a single instance because its initialization can be slow (up to a few seconds).
 */
@Singleton
public class DoubleClickMetadata {
  private static final Logger logger = LoggerFactory.getLogger(DoubleClickMetadata.class);
  private static final String BASE_URL = "https://storage.googleapis.com";
  private static final String ADX_DICT = BASE_URL + "/adx-rtb-dictionaries/";

  private final ImmutableMap<Integer, String> vendors;
  private final ImmutableMap<Integer, String> gdnVendors;
  private final ImmutableMap<Integer, String> adSensitiveCategories;
  private final ImmutableMap<Integer, String> adProductCategories;
  private final ImmutableMap<Integer, String> adRestrictedCategories;
  private final ImmutableMap<Integer, String> allAdCategories;
  private final ImmutableMap<Integer, String> pubExcCreativeAttributes;
  private final ImmutableMap<Integer, String> buyDecCreativeAttributes;
  private final ImmutableMap<Integer, String> allCreativeAttributes;
  private final ImmutableMap<Integer, String> sellerNetworks;
  private final ImmutableMap<Integer, String> contentLabels;
  private final ImmutableMap<Integer, String> publisherVerticals;
  private final ImmutableMap<Integer, GeoTarget> targetsByCriteriaId;
  private final ImmutableMap<GeoTarget.CanonicalKey, GeoTarget> targetsByCanonicalKey;
  private final ImmutableMap<Object, CountryCodes> countryCodes;

  @Inject
  public DoubleClickMetadata(Transport transport) {
    vendors = load(transport, ADX_DICT + "vendors.txt");
    gdnVendors = load(transport, ADX_DICT + "gdn-vendors.txt");
    HashMap<Integer, String> cats = new HashMap<>();
    cats.putAll(adSensitiveCategories = load(transport, ADX_DICT + "ad-sensitive-categories.txt"));
    cats.putAll(adProductCategories = load(transport, ADX_DICT + "ad-product-categories.txt"));
    cats.putAll(adRestrictedCategories = load(transport, ADX_DICT + "ad-restricted-categories.txt"));
    allAdCategories = ImmutableMap.copyOf(cats);
    HashMap<Integer, String> attrs = new HashMap<>();
    attrs.putAll(pubExcCreativeAttributes =
        load(transport, ADX_DICT + "publisher-excludable-creative-attributes.txt"));
    attrs.putAll(buyDecCreativeAttributes =
        load(transport, ADX_DICT + "buyer-declarable-creative-attributes.txt"));
    allCreativeAttributes = ImmutableMap.copyOf(attrs);
    sellerNetworks = load(transport, ADX_DICT + "seller-network-ids.txt");
    contentLabels = load(transport, ADX_DICT + "content-labels.txt");
    publisherVerticals = load(transport, ADX_DICT + "publisher-verticals.txt");
    targetsByCriteriaId = loadGeoTargets(transport, ADX_DICT + "geo-table.csv");
    HashMap<GeoTarget.CanonicalKey, GeoTarget> byKey = new HashMap<>();
    for (GeoTarget target : targetsByCriteriaId.values()) {
      byKey.put(target.key, target);
    }
    targetsByCanonicalKey = ImmutableMap.copyOf(byKey);
    countryCodes = loadCountryCodes("/adx-openrtb/countries.txt");
  }

  /**
   * Dictionary used in the excluded_attribute field of BidRequest.
   * This field describes the types of creatives that are not allowed by the publisher.
   * For example, they might specify restrictions on whether cookie usage is allowed,
   * or whether media and/or text ads are allowed.
   */
  public ImmutableMap<Integer, String> getPublisherExcludableCreativeAttributes() {
    return pubExcCreativeAttributes;
  }

  /**
   * Dictionary used for the attribute field in BidResponse. This field describes buyer declarable
   * attributes on creatives which must must not appear in excluded_attribute in BidRequest.
   */
  public ImmutableMap<Integer, String> getBuyerDeclarableCreativeAttributes() {
    return buyDecCreativeAttributes;
  }

  /**
   * @return Union of {@link #getPublisherExcludableCreativeAttributes()},
   * {@link #getBuyerDeclarableCreativeAttributes()}
   */
  public ImmutableMap<Integer, String> getAllCreativeAttributes() {
    return allCreativeAttributes;
  }

  /**
   * Dictionary used in the excluded_sensitive_category field of BidRequest
   * and the category field of BidResponse. This field describes sensitive categories
   * that are not allowed by publisher.
   * For example, the publisher does not want to host ads related to Politics.
   */
  public ImmutableMap<Integer, String> getSensitiveCategories() {
    return adSensitiveCategories;
  }

  /**
   * Dictionary used in the excluded_product_category field of BidRequest.
   * This field describes categories of products and services that are not allowed by the publisher.
   * For example, the publisher does not want to host ads related to Online Banking.
   */
  public ImmutableMap<Integer, String> getProductCategories() {
    return adProductCategories;
  }

  /**
   * Dictionary used in the allowed_restricted_category field of BidRequest and
   * the restricted_category field of BidResponse. This field describes restricted categories
   * that may be allowed by the publisher, and must be declared to use.
   * For example, ads containing Alcohol related content.
   */
  public ImmutableMap<Integer, String> getRestrictedCategories() {
    return adRestrictedCategories;
  }

  /**
   * @return Union of {@link #getProductCategories()}, {@link #getSensitiveCategories()},
   * {@link #getRestrictedCategories()}
   */
  public ImmutableMap<Integer, String> getAllCategories() {
    return allAdCategories;
  }

  /**
   * Dictionary used in the allowed_vendor_type field of BidRequest. This field lists which
   * Rich Media vendors such as Eyeblaster and Pointroll are allowed for the creative being
   * served as specified by the publisher.
   */
  public ImmutableMap<Integer, String> getVendors() {
    return vendors;
  }

  /**
   * Dictionary which lists all the allowed_vendor_type entries for any request
   * from a GDN publisher. These vendor types are all allowed on GDN but must be declared if used.
   * This is a subset of the entries in {@link #getVendors()}.
   */
  public ImmutableMap<Integer, String> getGdnVendors() {
    return gdnVendors;
  }

  /**
   * Dictionary file used in the seller_network_id field of BidRequest. This field specifies
   * the seller network to which the publisher belongs.
   */
  public ImmutableMap<Integer, String> getSellerNetworks() {
    return sellerNetworks;
  }

  /**
   * Dictionary file used in the detected_content_labels field of BidRequest.
   */
  public ImmutableMap<Integer, String> getContentLabels() {
    return contentLabels;
  }

  /**
   * Dictionary file used in the detected_vertical field of BidRequest.
   * This field specifies the verticals (similar to keywords) of the page on which
   * the ad will be shown. Google generates this field by crawling the page and
   * determining which verticals are used.
   */
  public ImmutableMap<Integer, String> getPublisherVerticals() {
    return publisherVerticals;
  }

  /**
   * Formats a code to the corresponding description from its domain.
   */
  public static String toString(Map<Integer, String> metadata, int code) {
    StringBuilder sb = new StringBuilder();
    sb.append(code).append(": ");
    String description = metadata.get(code);
    sb.append(description == null ? "<invalid>" : description);
    return sb.toString();
  }

  public ImmutableMap<Integer, GeoTarget> getTargetsByCriteriaId() {
    return targetsByCriteriaId;
  }

  public GeoTarget getGeoTarget(int criteriaId) {
    return targetsByCriteriaId.get(criteriaId);
  }

  public GeoTarget getGeoTarget(TargetType targetType, String canonicalName) {
    return targetsByCanonicalKey.get(new GeoTarget.CanonicalKey(targetType, canonicalName));
  }

  /**
   * Maps ISO 3166-1 codes.
   */
  public ImmutableMap<Object, CountryCodes> getCountryCodes() {
    return countryCodes;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).omitNullValues()
        .add("vendors#", vendors.size())
        .add("gdnVendorTypes#", gdnVendors.size())
        .add("sensitiveCategories#", adSensitiveCategories.size())
        .add("productCategories#", adProductCategories.size())
        .add("restrictedCategories#", adRestrictedCategories.size())
        .add("pubExcCreativeAttributes#", pubExcCreativeAttributes.size())
        .add("buyDecCreativeAttributes#", buyDecCreativeAttributes.size())
        .add("sellerNetworks#", sellerNetworks.size())
        .add("contentLabels#", contentLabels.size())
        .add("publisherVerticals#", publisherVerticals.size())
        .add("targetsByCriteriaId#", targetsByCriteriaId.size())
        .add("countryCodes#", countryCodes.size())
        .toString();
  }

  private static ImmutableMap<Integer, String> load(
      Transport transport, String resourceName) {
    Pattern pattern = Pattern.compile("(\\d+)\\s+(.*)");

    try (InputStream isMetadata = transport.open(resourceName)) {
      ImmutableMap.Builder<Integer, String> builder = ImmutableMap.builder();
      BufferedReader rd  = new BufferedReader(new InputStreamReader(isMetadata));
      String line;

      while ((line = rd.readLine()) != null) {
        Matcher matcher = pattern.matcher(line);

        if (matcher.matches()) {
          builder.put(Integer.parseInt(matcher.group(1)), matcher.group(2));
        }
      }

      return builder.build();
    } catch (IOException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  private static ImmutableMap<Integer, GeoTarget> loadGeoTargets(
      Transport transport, String resourceName) {
    Pattern pattern = Pattern.compile("(\\d+),(.*)");
    List<String> data = new ArrayList<>();
    List<String> nextData = new ArrayList<>();

    try (InputStream isMetadata = transport.open(resourceName)) {
      BufferedReader rd  = new BufferedReader(new InputStreamReader(isMetadata));
      String line;

      while ((line = rd.readLine()) != null) {
        Matcher matcher = pattern.matcher(line);

        if (matcher.matches()) {
          data.add(line);
        }
      }

      Map<Integer, GeoTarget> map = new LinkedHashMap<>();
      Map<String, GeoTarget> parentMap = new LinkedHashMap<>();
      CSVParser csvParser = new CSVParser(',');

      // Some records fail to match the parent by canonical name, for example
      // "Zalau,Salaj County,Romania", the parent record is "Salaj,Romania".
      // Cycle through all data three times (one per hierarchy level), anything left is discarded.
      for (int cycle = 1; cycle <= 3; ++cycle, data = nextData, nextData = new ArrayList<>()) {
        for (String record : data) {
          List<String> fields = csvParser.parseCsv(record);
          if (fields.size() != 7) {
            continue;
          }
          Integer criteriaId = Integer.valueOf(fields.get(0));
          String name = fields.get(1);
          String canonicalName = fields.get(2);
          String countryCode = fields.get(5);
          String targetType = fields.get(6);

          // Parent IDs are legacy, and some records are inconsistent, so we following AdX's docs
          // and build hierarchy by the canonical names. Notice that canonical names are not always
          // unique for leaf records, e.g. "New York,New York,United States" can be either the
          // City (1023191) or the County (9058761); that's why we need the TargetType to compose
          // a unique CanonicalKey. But parent records are unique, e.g. "New York,United States"
          // = State (21167), which is parent for both NY/City and NY/County. The hierarchy by
          // parent IDs can be different, eg: NY/City < Queens/County < NY/State <- US/Country.

          // To make this even more interesting, we can have records like this:
          // name          = "Champaign & Springfield-Decatur,IL"
          // canonicalName = "Champaign & Springfield-Decatur,IL,Illinois,United States"
          // Simply using the first comma to split the parent's canonical name will not work,
          // so we need a special case: if the name contains a comma, use its length as a prefix
          // for splitting. (Cannot use this rule for every record either, because that would fail
          // in a few records like "Burgos" / "Province of Burgos,Castile and Leon,Spain").

          String parentName;
          int pos = name.indexOf(',');
          if (pos == -1) {
            int canonPos = canonicalName.indexOf(',');
            parentName = canonPos == -1 ? null : canonicalName.substring(canonPos + 1);
          } else {
            int commas = 1;
            for (int i = pos + 1; i < name.length(); ++i) {
              if (name.charAt(i) == ',') {
                ++commas;
              }
            }
            int canonPos;
            for (canonPos = 0; canonPos < canonicalName.length() && commas >= 0; ++canonPos) {
              if (canonicalName.charAt(canonPos) == ',') {
                --commas;
              }
            }
            if (commas == 0) {
              parentName = null;
            } else if (commas != -1 || canonPos == canonicalName.length()) {
              logger.warn("Impossible to resolve parent, ignoring: {}", record);
              continue;
            } else {
              parentName = canonicalName.substring(canonPos + 1);
            }
          }
          GeoTarget parent;
          if (parentName == null) {
            parent = null;
          } else {
            parent = parentMap.get(parentName);
            if (parent == null) {
              nextData.add(record);
              continue;
            }
          }

          GeoTarget geoTarget = new GeoTarget(
              criteriaId,
              name,
              canonicalName,
              parent,
              countryCode,
              TargetType.valueOf(toEnumName(targetType)));
          map.put(criteriaId, geoTarget);
          // May overwrite duplicates for leaf targets, but only non-leafs will have lookups
          parentMap.put(canonicalName, geoTarget);
        }
      }

      logger.debug("Records without parent, ignoring:\n{}", Joiner.on('\n').join(data));
      return ImmutableMap.copyOf(map);
    } catch (IOException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  private ImmutableMap<Object, CountryCodes> loadCountryCodes(String resourceName) {
    ImmutableMap.Builder<Object, CountryCodes> map = ImmutableMap.builder();

    try (InputStream is = DoubleClickMetadata.class.getResourceAsStream(resourceName)) {
      BufferedReader rd = new BufferedReader(new InputStreamReader(is));
      CSVParser csvParser = new CSVParser('\t');
      Pattern pattern = Pattern.compile("(\\d+)\\s+(.*)");
      String line;

      while ((line = rd.readLine()) != null) {

        if (pattern.matcher(line).matches()) {
          List<String> fields = csvParser.parseCsv(line);
          CountryCodes codes = new CountryCodes(
              Integer.parseInt(fields.get(0)), fields.get(2), fields.get(3));
          map.put(codes.getNumeric(), codes);
          map.put(codes.getAlpha2(), codes);
          map.put(codes.getAlpha3(), codes);
        }
      }
    } catch (IOException e) {
      throw new ExceptionInInitializerError(e);
    }
    return map.build();
  }

  private static String toEnumName(String csvName) {
    return csvName.replace(' ', '_').toUpperCase();
  }

  /**
   * A record from the <a href="https://developers.google.com/ad-exchange/rtb/geotargeting">
   * Geographical Targeting</a> table.
   */
  public static class GeoTarget {
    final int criteriaId;
    final String name;
    final CanonicalKey key;
    final GeoTarget parent;
    final String countryCode;

    public GeoTarget(
        int criteriaId, String name, String canonicalName, GeoTarget parent,
        String countryCode, TargetType targetType) {
      this.criteriaId = criteriaId;
      this.name = name;
      this.key = new CanonicalKey(targetType, canonicalName);
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
     * Example: (New York city target) => (New York state)
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
     * Example: (New York city target) => {@link TargetType#CITY}
     */
    public final TargetType getTargetType() {
      return key.targetType;
    }

    /**
     * Finds an ancestor of a specific type, if possible.
     * <p>
     * Example: (New York city target, {@link TargetType#COUNTRY}) => (US country target)
     */
    public @Nullable GeoTarget getAncestor(TargetType targetType) {
      for (GeoTarget currTarget = this; currTarget != null; currTarget = currTarget.getParent()) {
        if (currTarget.getTargetType() == targetType) {
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
          .add("targetType", key.targetType)
          .toString();
    }

    static class CanonicalKey {
      final TargetType targetType;
      final String canonicalName;

      CanonicalKey(TargetType targetType, String canonicalName) {
        this.targetType = targetType;
        this.canonicalName = canonicalName;
      }

      @Override
      public int hashCode() {
        return targetType.hashCode() ^ canonicalName.hashCode();
      }

      @Override
      public boolean equals(Object obj) {
        if (obj == this) {
          return true;
        } else if (!(obj instanceof CanonicalKey)) {
          return false;
        }

        CanonicalKey other = (CanonicalKey) obj;
        return targetType == other.targetType
            && canonicalName.equals(other.canonicalName);
      }
    }

    /**
     * Type of geo target record.
     */
    public static enum TargetType {
      AIRPORT,
      AUTONOMOUS_COMMUNITY,
      BOROUGH,
      CANTON,
      CITY,
      CITY_REGION,
      CONGRESSIONAL_DISTRICT,
      COUNTRY,
      COUNTY,
      DEPARTMENT,
      DMA_REGION,
      GOVERNORATE,
      MUNICIPALITY,
      NEIGHBORHOOD,
      OKRUG,
      POSTAL_CODE,
      PREFECTURE,
      PROVINCE,
      REGION,
      STATE,
      TERRITORY,
      TV_REGION,
      UNION_TERRITORY,
      UNIVERSITY
    }
  }

  /**
   * Object that can load the content of an URL as a stream.
   */
  public static interface Transport {
    InputStream open(String url) throws IOException;
  }

  /**
   * Implementation of {@link Transport} using the java.net APIs.
   */
  public static class URLConnectionTransport implements Transport {
    @Override public InputStream open(String url) throws IOException {
      return new URL(url).openStream();
    }
  }

  /**
   * Implementation of {@link Transport} that loads a local resource.
   */
  public static class ResourceTransport implements Transport {
    private String resourceName;

    public String getResourceName() {
      return resourceName;
    }

    public ResourceTransport setResourceName(String resourceName) {
      this.resourceName = resourceName;
      return this;
    }

    @Override
    public InputStream open(String url) throws IOException {
      String resourceName = this.resourceName == null ? new URL(url).getPath() : this.resourceName;
      return ResourceTransport.class.getResourceAsStream(resourceName);
    }
  }

  /**
   * Stores ISO 3166-1 country codes.
   */
  public static class CountryCodes {
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
}
