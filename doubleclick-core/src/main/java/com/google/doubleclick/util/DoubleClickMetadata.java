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

import com.google.common.base.Function;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.doubleclick.util.GeoTarget.Type;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.text.ParseException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
  private final ImmutableMap<Integer, String> agencies;
  private final ImmutableMap<Integer, String> allAdCategories;
  private final ImmutableMap<Integer, String> pubExcCreativeAttributes;
  private final ImmutableMap<Integer, String> buyDecCreativeAttributes;
  private final ImmutableMap<Integer, String> allCreativeAttributes;
  private final ImmutableMap<Integer, String> creativeStatusCodes;
  private final ImmutableMap<Integer, String> sellerNetworks;
  private final ImmutableMap<Integer, String> siteLists;
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
    agencies = load(transport, ADX_DICT + "agencies.txt");
    HashMap<Integer, String> attrs = new HashMap<>();
    attrs.putAll(pubExcCreativeAttributes =
        load(transport, ADX_DICT + "publisher-excludable-creative-attributes.txt"));
    attrs.putAll(buyDecCreativeAttributes =
        load(transport, ADX_DICT + "buyer-declarable-creative-attributes.txt"));
    allCreativeAttributes = ImmutableMap.copyOf(attrs);
    creativeStatusCodes = load(transport, ADX_DICT + "creative-status-codes.txt");
    sellerNetworks = load(transport, ADX_DICT + "seller-network-ids.txt");
    siteLists = load(transport, ADX_DICT + "site-lists.txt");
    contentLabels = load(transport, ADX_DICT + "content-labels.txt");
    publisherVerticals = load(transport, ADX_DICT + "publisher-verticals.txt");
    targetsByCriteriaId = loadGeoTargets(transport, ADX_DICT + "geo-table.csv");
    HashMap<GeoTarget.CanonicalKey, GeoTarget> byKey = new HashMap<>();
    for (GeoTarget target : targetsByCriteriaId.values()) {
      byKey.put(target.getKey(), target);
    }
    targetsByCanonicalKey = ImmutableMap.copyOf(byKey);
    countryCodes = loadCountryCodes(ADX_DICT + "countries.txt");
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
   * Dictionary file used in the creative_status_code field of BidRequest.BidResponseFeedback.
   * This field lists the different reasons that a creative returned
   * in a BidResponse may be rejected.
   */
  public ImmutableMap<Integer, String> getCreativeStatusCodes() {
    return creativeStatusCodes;
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
   * Dictionary file used in the agency_id field of BidResponse.
   * This field is used to declare the agency that is associated with the ad being returned.
   */
  public ImmutableMap<Integer, String> getAgencies() {
    return agencies;
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
   * Dictionary file used in the site_list_id field of BidRequest. This field specifies the
   * site lists to which the publisher belongs. Current options are Ad Planner 1000
   * (a list of 1000 most visited sites on the web), and Brand Select
   * (a list of quality publishers generated based on Google internal ranking).
   */
  public ImmutableMap<Integer, String> getSiteLists() {
    return siteLists;
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

  public GeoTarget getGeoTarget(Type type, String canonicalName) {
    return targetsByCanonicalKey.get(new GeoTarget.CanonicalKey(type, canonicalName));
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
        .add("agencies#", agencies.size())
        .add("buyDecCreativeAttributes#", buyDecCreativeAttributes.size())
        .add("contentLabels#", contentLabels.size())
        .add("countryCodes#", countryCodes.size())
        .add("creativeStatusCodes#", creativeStatusCodes.size())
        .add("gdnVendorTypes#", gdnVendors.size())
        .add("productCategories#", adProductCategories.size())
        .add("pubExcCreativeAttributes#", pubExcCreativeAttributes.size())
        .add("publisherVerticals#", publisherVerticals.size())
        .add("restrictedCategories#", adRestrictedCategories.size())
        .add("sellerNetworks#", sellerNetworks.size())
        .add("sensitiveCategories#", adSensitiveCategories.size())
        .add("siteLists#", siteLists.size())
        .add("targetsByCriteriaId#", targetsByCriteriaId.size())
        .add("vendors#", vendors.size())
        .toString();
  }

  private static ImmutableMap<Integer, String> load(
      Transport transport, String resourceName) {
    try (InputStream isMetadata = transport.open(resourceName)) {
      Pattern pattern = Pattern.compile("(\\d+)\\s+(.*)");
      ImmutableMap.Builder<Integer, String> builder = ImmutableMap.builder();
      BufferedReader rd  = new BufferedReader(new InputStreamReader(isMetadata));
      String record;

      while ((record = rd.readLine()) != null) {
        Matcher matcher = pattern.matcher(record);

        if (matcher.matches()) {
          try {
            builder.put(Integer.parseInt(matcher.group(1)), matcher.group(2));
          } catch (NumberFormatException e) {
            logger.trace("Bad record, ignoring: {} - [{}]", e.toString(), record);
          }
        }
      }

      return builder.build();
    } catch (IOException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  private static ImmutableMap<Integer, GeoTarget> loadGeoTargets(
      Transport transport, String resourceName) {
    final Map<Integer, GeoTarget> targetsById = new LinkedHashMap<>();
    final Map<Integer, List<Integer>> parentIdsById = new LinkedHashMap<>();
    final Map<String, GeoTarget> targetsByCanon = new LinkedHashMap<>();
    final Set<String> duplicateCanon = new LinkedHashSet<>();

    try (InputStream is = transport.open(resourceName)) {
      CSVParser.csvParser().parse(is, "(\\d+),(.*)", new Function<List<String>, Boolean>() {
        @Override public Boolean apply(List<String> fields) {
          try {
            if (fields.size() == 7) {
              GeoTarget target = new GeoTarget(
                  Integer.valueOf(fields.get(0)), Type.valueOf(toEnumName(fields.get(6))),
                  fields.get(2), fields.get(1), fields.get(5), null, null);
              List<Integer> idParent = Lists.transform(
                  CSVParser.csvParser().parse(fields.get(3)), new Function<String, Integer>(){
                @Override public Integer apply(@Nullable String id) {
                  return Integer.valueOf(id);
                }});

              targetsById.put(target.getCriteriaId(), target);
              parentIdsById.put(target.getCriteriaId(), idParent);

              if (targetsByCanon.containsKey(target.getCanonicalName())) {
                duplicateCanon.add(target.getCanonicalName());
                targetsByCanon.remove(target.getCanonicalName());
              } else {
                targetsByCanon.put(target.getCanonicalName(), target);
              }
            }
          } catch (ParseException | IllegalArgumentException e) {
            logger.trace("Bad record [{}]: {}", fields, e.toString());
          }
          return true;
        }
      });

      for (Map.Entry<Integer, GeoTarget> entry : targetsById.entrySet()) {
        GeoTarget target = entry.getValue();
        GeoTarget canonParent = targetsByCanon.get(target.findCanonParentName());
        if (canonParent != null) {
          target.setCanonParent(canonParent);
        }
      }

      for (Map.Entry<Integer, GeoTarget> entry : targetsById.entrySet()) {
        GeoTarget target = entry.getValue();
        List<Integer> parentIds = parentIdsById.get(target.getCriteriaId());
        for (Integer parentId : parentIds) {
          GeoTarget idParent = targetsById.get(parentId);
          if (idParent != null) {
            target.setIdParent(idParent);
            break;
          }
        }
      }

      return ImmutableMap.copyOf(targetsById);
    } catch (IOException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  private ImmutableMap<Object, CountryCodes> loadCountryCodes(String resourceName) {
    final ImmutableMap.Builder<Object, CountryCodes> map = ImmutableMap.builder();

    try (InputStream is = new ResourceTransport().open(resourceName)) {
      CSVParser.tsvParser().parse(is, "(\\d+)\\s+(.*)", new Function<List<String>, Boolean>() {
        @Override @Nullable public Boolean apply(@Nullable List<String> fields) {
          try {
            CountryCodes codes = new CountryCodes(
                Integer.parseInt(fields.get(0)), fields.get(2), fields.get(3));
            map.put(codes.getNumeric(), codes);
            map.put(codes.getAlpha2(), codes);
            map.put(codes.getAlpha3(), codes);
          } catch (IllegalArgumentException e) {
            logger.trace("Bad record: {}: {}", fields, e.toString());
          }
          return true;
        }
      });
    } catch (IOException e) {
      throw new ExceptionInInitializerError(e);
    }
    return map.build();
  }

  private static String toEnumName(String csvName) {
    return csvName.replace(' ', '_').toUpperCase();
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
    @Override public InputStream open(String url) throws IOException {
      String resourceName = url.startsWith("/") ? url : new URL(url).getPath();
      InputStream is = ResourceTransport.class.getResourceAsStream(resourceName);
      if (is == null) {
        throw new IOException("Cannot open local resource: " + resourceName);
      }
      return is;
    }
  }
}
