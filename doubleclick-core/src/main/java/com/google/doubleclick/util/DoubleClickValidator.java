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
import com.google.common.collect.Iterables;
import com.google.protos.adx.NetworkBid.BidRequest;
import com.google.protos.adx.NetworkBid.BidRequest.AdSlot.MatchingAdData;
import com.google.protos.adx.NetworkBid.BidRequest.AdSlot.MatchingAdData.DirectDeal;
import com.google.protos.adx.NetworkBid.BidResponse;

import com.codahale.metrics.Counter;
import com.codahale.metrics.MetricRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Validates a pair of {@link BidRequest} and its corresponding {@link BidResponse}.
 * Bids with any validation problems will cause debug logs and metric updates.
 * Fatal validation errors (that would cause the bid to be rejected by DoubleClick Ad Exchange)
 * will also be removed from the response.
 * <p>
 * This class is threadsafe. Recommended use is as a singleton, but you may also want to create
 * multiple instances if you need to keep track of metrics separately for different uses
 * (for that to make sense, provide a different {@link MetricRegistry} to each instance).
 */
@Singleton
public class DoubleClickValidator {
  private static final Logger logger =
      LoggerFactory.getLogger(DoubleClickValidator.class);
  private static final int GDN = 1;
  static final int CREATIVE_FLASH = 34;
  static final int CREATIVE_NON_FLASH = 50;
  static final int CREATIVE_SSL = 47;
  static final int CREATIVE_NON_SSL = 48;

  private final DoubleClickMetadata metadata;
  private final Counter unmatchedImp = new Counter();
  private final Counter unmatchedDeal = new Counter();
  private final Counter needsNonflashAttr = new Counter();
  private final Counter needsSslAttr = new Counter();
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
    metricRegistry.register(MetricRegistry.name(getClass(), "unmatched-deal"),
        unmatchedDeal);
    metricRegistry.register(MetricRegistry.name(getClass(), "needs-nonflash-attr"),
        needsNonflashAttr);
    metricRegistry.register(MetricRegistry.name(getClass(), "needs-ssl-attr"),
        needsSslAttr);
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
    boolean hasBad = false;
    boolean hasEmpty = false;
    List<BidResponse.Ad.Builder> ads = response.getAdBuilderList();

    for (int iAd = 0; iAd < ads.size(); ++iAd) {
      final BidResponse.Ad.Builder ad = ads.get(iAd);
      List<BidResponse.Ad.AdSlot.Builder> adslots = ad.getAdslotBuilderList();

      if (adslots.isEmpty()) {
        hasEmpty = true;
        if (logger.isDebugEnabled()) {
          logger.debug("Ad #{} removed, clean but empty adslot", iAd);
        }
      } else {
        Iterable<BidResponse.Ad.AdSlot.Builder> filteredAdslots = ProtoUtils.filter(adslots,
            new Predicate<BidResponse.Ad.AdSlot.Builder>() {
          @Override public boolean apply(BidResponse.Ad.AdSlot.Builder adslot) {
            return validate(request, ad, adslot);
          }});

        if (filteredAdslots != adslots) {
          hasBad = true;
          ad.clearAdslot();
          if (Iterables.isEmpty(filteredAdslots)) {
            if (logger.isDebugEnabled()) {
              logger.debug("Ad #{} removed, all adslot values rejected", iAd);
            }
          } else {
            for (BidResponse.Ad.AdSlot.Builder filteredAdslot : filteredAdslots) {
              ad.addAdslot(filteredAdslot);
            }
          }
        }
      }
    }

    if (hasBad || hasEmpty) {
      List<BidResponse.Ad.Builder> adsNew = new ArrayList<>(ads.size());
      for (BidResponse.Ad.Builder ad : ads) {
        if (ad.getAdslotCount() != 0) {
          adsNew.add(ad);
        }
      }
      response.clearAd();
      for (BidResponse.Ad.Builder ad : adsNew) {
        response.addAd(ad);
      }
    }
    return hasBad;
  }

  public boolean validate(
      BidRequest request, BidResponse.Ad.Builder ad, BidResponse.Ad.AdSlot.Builder adslot) {
    BidRequest.AdSlot reqSlot = findRequestSlot(request, adslot.getId());
    if (reqSlot == null) {
      unmatchedImp.inc();
      if (logger.isDebugEnabled()) {
        logger.debug("AdSlot {} rejected, unmatched id", logId(adslot));
      }
      return false;
    }
    boolean valid = true;

    if (adslot.hasDealId()) {
      DirectDeal deal = findDeal(reqSlot, adslot.getDealId());
      if (deal == null) {
        unmatchedDeal.inc();
        if (logger.isDebugEnabled()) {
          logger.debug("AdSlot {} rejected, unmatched dealid: {}",
              logId(adslot), adslot.getDealId());
        }
        return false;
      }
    }

    if (reqSlot.getExcludedAttributeList().contains(CREATIVE_FLASH)) {
      if (!ad.getAttributeList().contains(CREATIVE_NON_FLASH)) {
        needsNonflashAttr.inc();
        if (logger.isDebugEnabled()) {
          logger.debug("{} rejected, ad.attribute needs value: {}",
              logId(adslot), DoubleClickMetadata.toString(
                  metadata.getBuyerDeclarableCreativeAttributes(), CREATIVE_NON_FLASH));
        }
        valid = false;
      }
    }

    if (reqSlot.getExcludedAttributeList().contains(CREATIVE_NON_SSL)) {
      if (!ad.getAttributeList().contains(CREATIVE_SSL)) {
        needsSslAttr.inc();
        if (logger.isDebugEnabled()) {
          logger.debug("{} rejected, ad.attribute needs value: {}",
              logId(adslot), DoubleClickMetadata.toString(
                  metadata.getBuyerDeclarableCreativeAttributes(), CREATIVE_SSL));
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
      if (logger.isDebugEnabled()) {
        logger.debug("{} rejected, unknown or not-allowed ad.vendor_type values: {}",
            logId(adslot), request.getSellerNetworkId() == GDN ? "GDN " : "", bad);
      }
      invalidVendor.inc();
      valid = false;
    }

    if (!(bad = checkAttributes(
        reqSlot.getAllowedRestrictedCategoryList(), ad.getRestrictedCategoryList(),
        metadata.getRestrictedCategories(), true)).isEmpty()) {
      if (logger.isDebugEnabled()) {
        logger.debug("{} rejected, unknown or not-allowed ad.restricted_category values: {}",
            logId(adslot), bad);
      }
      invalidRestrCat.inc();
      valid = false;
    }

    if (!(bad = checkAttributes(
        reqSlot.getExcludedProductCategoryList(), ad.getCategoryList(),
        metadata.getAllCategories(), false)).isEmpty()) {
      if (logger.isDebugEnabled()) {
        logger.debug("{} rejected, unknown or excluded product ad.category values: {}",
            logId(adslot), bad);
      }
      invalidProdCat.inc();
      valid = false;
    }

    if (!(bad = checkAttributes(
        reqSlot.getExcludedSensitiveCategoryList(), ad.getCategoryList(),
        metadata.getAllCategories(), false)).isEmpty()) {
      if (logger.isDebugEnabled()) {
        logger.debug("{} rejected, unknown or excluded sensitive ad.category values: {}",
            logId(adslot), bad);
      }
      invalidSensCat.inc();
      valid = false;
    }

    if (!(bad = checkAttributes(
        reqSlot.getExcludedAttributeList(), ad.getAttributeList(),
        metadata.getBuyerDeclarableCreativeAttributes(), false)).isEmpty()) {
      if (logger.isDebugEnabled()) {
        logger.debug("{} rejected, unknown or excluded ad.attribute values: {}",
            logId(adslot), bad);
      }
      invalidCreatAttr.inc();
      valid = false;
    }

    return valid;
  }

  protected static @Nullable DirectDeal findDeal(BidRequest.AdSlot reqSlot, long dealId) {
    for (MatchingAdData reqMad : reqSlot.getMatchingAdDataList()) {
      for (DirectDeal reqDeal : reqMad.getDirectDealList()) {
        if (reqDeal.hasDirectDealId() && reqDeal.getDirectDealId() == dealId) {
          return reqDeal;
        }
      }
    }
    return null;
  }

  protected static String logId(BidResponse.Ad.AdSlot.Builder adslot) {
    return "AdSlot " + adslot.getId();
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
