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
import com.google.doubleclick.DcExt;
import com.google.doubleclick.Doubleclick;
import com.google.openrtb.OpenRtb;
import com.google.openrtb.OpenRtb.BidRequest;
import com.google.openrtb.OpenRtb.BidRequest.Impression;
import com.google.openrtb.OpenRtb.BidRequest.Publisher;
import com.google.openrtb.OpenRtb.BidResponse.SeatBid.Bid;
import com.google.openrtb.util.OpenRtbUtils;

import com.codahale.metrics.Counter;
import com.codahale.metrics.MetricRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

/**
 * Validates a pair of {@link BidRequest} and its corresponding
 * {@link com.google.openrtb.OpenRtb.BidResponse.Builder}.
 * Bids with any validation problems will cause debug logs and metric updates.
 * Fatal validation errors (that would cause the bid to be rejected by DoubleClick Ad Exchange)
 * will also be removed from the response.
 */
public class DoubleClickValidator {
  private static final Logger logger =
      LoggerFactory.getLogger(DoubleClickValidator.class);
  private static final String GDN = "1";
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
  private final Counter fragmentUsesReserved = new Counter();

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
    metricRegistry.register(MetricRegistry.name(getClass(), "fragment-uses-reserved-field"),
        fragmentUsesReserved);
  }

  public void validate(final OpenRtb.BidRequest request, final OpenRtb.BidResponse.Builder response) {
    OpenRtbUtils.filterBids(response, new Predicate<Bid>() {
      @Override public boolean apply(Bid bid) {
        return validate(request, bid);
      }});
  }

  public boolean validate(OpenRtb.BidRequest request, Bid bid) {
    Doubleclick.BidResponse.Ad adExt = bid.hasExtension(DcExt.ad)
        ? bid.getExtension(DcExt.ad)
        : null;
    Impression imp = OpenRtbUtils.impWithId(request, bid.getImpid());
    if (imp == null) {
      unmatchedImp.inc();
      if (logger.isDebugEnabled()) {
        logger.warn("Impresson id doesn't match any AdSlot (impression): {}", bid.getImpid());
      }
      return false;
    }
    Doubleclick.BidRequest.AdSlot adSlot = imp.getExtension(DcExt.adSlot);
    boolean valid = true;

    if (adSlot.getExcludedAttributeList().contains(CREATIVE_FLASH)) {
      if (adExt == null
          || !adExt.getAttributeList().contains(CREATIVE_NON_FLASH)) {
        needsNonflashAttr.inc();
        if (logger.isDebugEnabled()) {
          logger.debug("Ad needs attribute {}\n{}",
              DoubleClickMetadata.toString(
                  metadata.getBuyerDeclarableCreativeAttributes(),
                  CREATIVE_NON_FLASH),
                  bid);
        }
        valid = false;
      }
    }

    if (adExt == null) {
      return valid;
    }

    List<Integer> bad;
    Publisher publisher = request.hasSite()
        ? request.getSite().getPublisher()
        : request.getApp().getPublisher();
    ImmutableMap<Integer, String> metaVendors = GDN.equals(publisher.getId())
        ? metadata.getGdnVendors()
        : metadata.getVendors();

    if (!(bad = checkAttributes(
        adSlot.getAllowedVendorTypeList(), adExt.getVendorTypeList(),
        metaVendors, true)).isEmpty()) {
      logger.debug("Bid rejected, contains not-allowed {} types {}:\n{}",
          GDN.equals(publisher.getId()) ? "GDN vendors" : "vendors", bad, bid);
      invalidVendor.inc();
      valid = false;
    }

    if (!(bad = checkAttributes(
        adSlot.getAllowedRestrictedCategoryList(), adExt.getRestrictedCategoryList(),
        metadata.getRestrictedCategories(), true)).isEmpty()) {
      logger.debug("Bid rejected, contains invalid restricted categories {}:\n{}", bad, bid);
      invalidRestrCat.inc();
      valid = false;
    }

    if (!(bad = checkAttributes(
        adSlot.getExcludedProductCategoryList(), adExt.getCategoryList(),
        metadata.getAllCategories(), false)).isEmpty()) {
      logger.debug("Bid rejected, contains excluded product categories {}:\n{}", bad, bid);
      invalidProdCat.inc();
      valid = false;
    }

    if (!(bad = checkAttributes(
        adSlot.getExcludedSensitiveCategoryList(), adExt.getCategoryList(),
        metadata.getAllCategories(), false)).isEmpty()) {
      logger.debug("Bid rejected, contains excluded sensitive categories {}:\n{}", bad, bid);
      invalidSensCat.inc();
      valid = false;
    }

    if (!(bad = checkAttributes(
        adSlot.getExcludedAttributeList(), adExt.getAttributeList(),
        metadata.getBuyerDeclarableCreativeAttributes(), false)).isEmpty()) {
      logger.debug("Bid rejected, contains excluded creative attributes {}:\n{}", bad, bid);
      invalidCreatAttr.inc();
      valid = false;
    }

    return valid;
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
