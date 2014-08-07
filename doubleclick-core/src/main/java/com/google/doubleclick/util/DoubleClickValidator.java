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

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.doubleclick.Doubleclick.BidRequest;
import com.google.doubleclick.Doubleclick.BidResponse;
import com.google.protobuf.MessageLiteOrBuilder;

import com.codahale.metrics.Counter;
import com.codahale.metrics.MetricRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import javax.inject.Inject;

/**
 * Validates a pair of {@link BidRequest} and its corresponding {@link BidResponse}.
 * Bids with any validation problems will cause debug logs and metric updates.
 * Fatal validation errors (that would cause the bid to be rejected by DoubleClick Ad Exchange)
 * will also be removed from the response.
 */
public class DoubleClickValidator {
  private static final Logger logger =
      LoggerFactory.getLogger(DoubleClickValidator.class);
  private static final int GDN = 1;
  public static final int CREATIVE_FLASH = 34;
  public static final int CREATIVE_NON_FLASH = 50;

  private final DoubleClickMetadata metadata;
  private final Counter unmatchedImp = new Counter();
  private final Counter needsNonflashAttr = new Counter();
  private final Counter invalidCreatAttr = new Counter();
  private final Counter invalidVendor = new Counter();
  private final Counter invalidProdCat = new Counter();
  private final Counter invalidRestrCat = new Counter();
  private final Counter invalidSensCat = new Counter();
  private final Counter invalidAttrTotal = new Counter();
  private final Counter unknownAttrTotal = new Counter();

  @Inject
  public DoubleClickValidator(
      MetricRegistry metricRegistry, DoubleClickMetadata metadata) {
    this.metadata = metadata;
    metricRegistry.register(MetricRegistry.name(getClass(), "unmatched-imp"),
        unmatchedImp);
    metricRegistry.register(MetricRegistry.name(getClass(), "needs-nonflash-attr"),
        needsNonflashAttr);
    metricRegistry.register(MetricRegistry.name(getClass(), "invalid-creative-attr"),
        invalidCreatAttr);
    metricRegistry.register(MetricRegistry.name(getClass(), "invalid-vendor"),
        invalidVendor);
    metricRegistry.register(MetricRegistry.name(getClass(), "invalid-ad-product-cat"),
        invalidProdCat);
    metricRegistry.register(MetricRegistry.name(getClass(), "invalid-ad-restricted-cat"),
        invalidRestrCat);
    metricRegistry.register(MetricRegistry.name(getClass(), "invalid-ad-sensitive-cat"),
        invalidSensCat);
    metricRegistry.register(MetricRegistry.name(getClass(), "invalid-attr-total"),
        invalidAttrTotal);
    metricRegistry.register(MetricRegistry.name(getClass(), "unknown-attr-total"),
        unknownAttrTotal);
  }

  public boolean validate(final BidRequest request, final BidResponse.Builder response) {
    boolean ok = true;
    for (final BidResponse.Ad.Builder ad : response.getAdBuilderList()) {
      List<BidResponse.Ad.AdSlot.Builder> adslots = ad.getAdslotBuilderList();
      List<BidResponse.Ad.AdSlot.Builder> filteredAdslots = filter(adslots,
          new Predicate<BidResponse.Ad.AdSlot.Builder>() {
            @Override public boolean apply(BidResponse.Ad.AdSlot.Builder adslot) {
              return validate(request, ad, adslot);
            }});
      if (filteredAdslots != adslots) {
        ok = false;
        ad.clearAdslot();
        for (BidResponse.Ad.AdSlot.Builder filteredAdslot : filteredAdslots) {
          ad.addAdslot(filteredAdslot);
        }
      }
    }
    if (!ok) {
      List<BidResponse.Ad.Builder> ads = response.getAdBuilderList();
      response.clearAd();
      for (BidResponse.Ad.Builder ad : ads) {
        if (ad.getAdslotCount() != 0) {
          response.addAd(ad);
        }
      }
    }
    return ok;
  }

  public boolean validate(
      BidRequest request, BidResponse.Ad.Builder ad, BidResponse.Ad.AdSlot.Builder adslot) {
    BidRequest.AdSlot reqSlot = findRequestSlot(request, adslot.getId());
    if (reqSlot == null) {
      unmatchedImp.inc();
      if (logger.isDebugEnabled()) {
        logger.warn("Response AdSlot id (impression) doesn't match request: {}", adslot.getId());
      }
      return false;
    }
    boolean valid = true;

    if (reqSlot.getExcludedAttributeList().contains(CREATIVE_FLASH)) {
      if (!ad.getAttributeList().contains(CREATIVE_NON_FLASH)) {
        needsNonflashAttr.inc();
        if (logger.isDebugEnabled()) {
          logger.debug("Request Ad (impression) needs attribute {}\n{}",
              DoubleClickMetadata.toString(
                  metadata.getBuyerDeclarableCreativeAttributes(),
                  CREATIVE_NON_FLASH),
                  ad);
        }
        valid = false;
      }
    }

    List<Integer> bad;
    ImmutableMap<Integer, String> metaVendors = request.getSellerNetworkId() == GDN
        ? metadata.getGdnVendors()
        : metadata.getVendors();

    if (!(bad = checkAttributes(
        reqSlot.getAllowedVendorTypeList(), ad.getVendorTypeList(),
        metaVendors, true)).isEmpty()) {
      logger.debug("Ad rejected, contains not-allowed {} types {}:\n{}",
          request.getSellerNetworkId() == GDN ? "GDN vendors" : "vendors", bad, ad);
      invalidVendor.inc();
      valid = false;
    }

    if (!(bad = checkAttributes(
        reqSlot.getAllowedRestrictedCategoryList(), ad.getRestrictedCategoryList(),
        metadata.getRestrictedCategories(), true)).isEmpty()) {
      logger.debug("Ad rejected, contains invalid restricted categories {}:\n{}", bad, ad);
      invalidRestrCat.inc();
      valid = false;
    }

    if (!(bad = checkAttributes(
        reqSlot.getExcludedProductCategoryList(), ad.getCategoryList(),
        metadata.getAllCategories(), false)).isEmpty()) {
      logger.debug("Ad rejected, contains excluded product categories {}:\n{}", bad, ad);
      invalidProdCat.inc();
      valid = false;
    }

    if (!(bad = checkAttributes(
        reqSlot.getExcludedSensitiveCategoryList(), ad.getCategoryList(),
        metadata.getAllCategories(), false)).isEmpty()) {
      logger.debug("Ad rejected, contains excluded sensitive categories {}:\n{}", bad, ad);
      invalidSensCat.inc();
      valid = false;
    }

    if (!(bad = checkAttributes(
        reqSlot.getExcludedAttributeList(), ad.getAttributeList(),
        metadata.getBuyerDeclarableCreativeAttributes(), false)).isEmpty()) {
      logger.debug("Ad rejected, contains excluded creative attributes {}:\n{}", bad, ad);
      invalidCreatAttr.inc();
      valid = false;
    }

    return valid;
  }

  // Identical to ProtoUtils.filter(), avoiding dependency from openrtb-core
  protected static <M extends MessageLiteOrBuilder>
  List<M> filter(List<M> objs, Predicate<M> filter) {
    for (ListIterator<M> iter = objs.listIterator(); iter.hasNext(); ) {
      if (!filter.apply(iter.next())) {
        List<M> filtered = new ArrayList<>(objs.size() - 1);
        filtered.addAll(objs.subList(0, iter.previousIndex()));

        while (iter.hasNext()) {
          M obj = iter.next();

          if (filter.apply(obj)) {
            filtered.add(obj);
          }
        }

        return filtered;
      }
    }

    return objs;
  }

  protected static BidRequest.AdSlot findRequestSlot(BidRequest request, int adslotId) {
    for (BidRequest.AdSlot adslot : request.getAdslotList()) {
      if (adslot.getId() == adslotId) {
        return adslot;
      }
    }
    return null;
  }

  protected <T> List<T> checkAttributes(List<T> reqAttrs,
      List<T> respAttrs, Map<T, String> metadata, boolean allowed) {
    List<T> bad = null;

    if (!respAttrs.isEmpty()) {
      Collection<T> reqIndex = reqAttrs.size() > 4 ? ImmutableSet.copyOf(reqAttrs) : reqAttrs;

      for (T respValue : respAttrs) {
        if (!metadata.containsKey(respValue)) {
          bad = (bad == null) ? new ArrayList<T>() : bad;
          bad.add(respValue);
          unknownAttrTotal.inc();
        } else if (reqIndex.contains(respValue) != allowed) {
          bad = (bad == null) ? new ArrayList<T>() : bad;
          bad.add(respValue);
          invalidAttrTotal.inc();
        }
      }
    }

    return bad == null ? ImmutableList.<T>of() : bad;
  }
}
