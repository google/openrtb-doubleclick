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

import com.google.common.collect.ImmutableList;
import com.google.openrtb.OpenRtb.BidRequest.Impression;
import com.google.openrtb.OpenRtb.BidResponse.SeatBid.Bid;
import com.google.openrtb.OpenRtbNative.NativeRequest;
import com.google.openrtb.OpenRtbNative.NativeResponse;
import com.google.openrtb.json.OpenRtbJsonFactory;
import com.google.openrtb.json.OpenRtbNativeJsonReader;
import com.google.protobuf.TextFormat;
import com.google.protos.adx.NetworkBid;

import com.codahale.metrics.Counter;
import com.codahale.metrics.MetricRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Mapping between the DoubleClick and OpenRTB Native models.
 */
@Singleton
public class DoubleClickOpenRtbNativeMapper {
  private static final Logger logger =
      LoggerFactory.getLogger(DoubleClickOpenRtbNativeMapper.class);

  private final ImmutableList<ExtMapper> extMappers;
  private final OpenRtbNativeJsonReader jsonReader;
  private final Counter invalid = new Counter();
  private final Counter incomplete = new Counter();

  @Inject
  public DoubleClickOpenRtbNativeMapper(
      MetricRegistry metricRegistry,
      @Nullable OpenRtbJsonFactory jsonFactory,
      List<ExtMapper> extMappers) {
    this.jsonReader = jsonFactory == null ? null : jsonFactory.newNativeReader();
    this.extMappers = ImmutableList.copyOf(extMappers);
    Class<? extends DoubleClickOpenRtbNativeMapper> cls = getClass();
    metricRegistry.register(MetricRegistry.name(cls, "invalid"), invalid);
    metricRegistry.register(MetricRegistry.name(cls, "incomplete"), incomplete);
  }

  public NetworkBid.BidResponse.Ad.NativeAd.Builder buildNativeResponse(
      Bid bid, Impression matchingImp) {
    if (bid.hasAdmNative() == bid.hasAdm()) {
      throw new MapperException("Must provide only one of adm/admNative");
    }
    NativeResponse natResp;
    if (bid.hasAdmNative()) {
      natResp = bid.getAdmNative();
    } else if (jsonReader != null) {
      try {
        natResp = jsonReader.readNativeResponse(bid.getAdm());
      } catch (IOException e) {
        throw new MapperException("Failed parsing adm as a Native response: " + e.getMessage());
      }
    } else {
      throw new MapperException("Not configured for OpenRTB/JSON native ads");
    }
    return buildNativeAd(matchingImp.getNative().getRequest(), natResp);
  }

  private NetworkBid.BidResponse.Ad.NativeAd.Builder buildNativeAd(
      NativeRequest natReq, NativeResponse natResp) {
    NetworkBid.BidResponse.Ad.NativeAd.Builder dcNatAd =
        NetworkBid.BidResponse.Ad.NativeAd.newBuilder()
            .addAllImpressionTrackingUrl(natResp.getImptrackersList());

    if (natResp.hasLink()) {
      dcNatAd.setClickTrackingUrl(natResp.getLink().getUrl());
    }

    for (NativeResponse.Asset asset : natResp.getAssetsList()) {
      NativeRequest.Asset matchingReqAsset = null;
      for (NativeRequest.Asset reqAsset : natReq.getAssetsList()) {
        if (reqAsset.getId() == asset.getId()) {
          matchingReqAsset = reqAsset;
          break;
        }
      }
      if (matchingReqAsset == null) {
        invalid.inc();
        throw new MapperException(
            "Asset.id doesn't match any request native asset: %s", asset.getId());
      }

      if (asset.hasLink()) {
        dcNatAd.setStore(asset.getLink().getUrl());
      }

      if (asset.hasTitle()) {
        if (!matchingReqAsset.hasTitle()) {
          invalid.inc();
          throw new MapperException(
              "Asset.id doesn't match request asset, should be Title: %s", asset.getId());
        }
        dcNatAd.setHeadline(asset.getTitle().getText());
      }

      if (asset.hasImg()) {
        if (!matchingReqAsset.hasImg()) {
          invalid.inc();
          throw new MapperException(
              "Asset.id doesn't match request asset, should be Image: %s", asset.getId());
        }
        NativeResponse.Asset.Image img = asset.getImg();
        NetworkBid.BidResponse.Ad.NativeAd.Image.Builder dcImg =
            NetworkBid.BidResponse.Ad.NativeAd.Image.newBuilder()
                .setUrl(img.getUrl())
                .setWidth(img.getW())
                .setHeight(img.getH());
        switch (matchingReqAsset.getImg().getType()) {
          case MAIN:
            dcNatAd.setImage(dcImg);
            break;
          case ICON:
            dcNatAd.setAppIcon(dcImg);
            break;
          case LOGO:
            dcNatAd.setLogo(dcImg);
            break;
          default:
            invalid.inc();
            throw new MapperException(
                "Asset %s has unsupported Image type: %s",
                asset.getId(), matchingReqAsset.getImg().getType());
        }
      }

      if (asset.hasData()) {
        if (!matchingReqAsset.hasData()) {
          invalid.inc();
          throw new MapperException(
              "Asset.id doesn't match request asset, should be Data: %s", asset.getId());
        }
        NativeResponse.Asset.Data data = asset.getData();
        switch (matchingReqAsset.getData().getType()) {
          case CTATEXT:
            dcNatAd.setCallToAction(data.getValue());
            break;
          case DESC:
            dcNatAd.setBody(data.getValue());
            break;
          case SPONSORED:
            dcNatAd.setAdvertiser(data.getValue());
            break;
          case PRICE:
            dcNatAd.setPrice(data.getValue());
            break;
          case ADDRESS:
            dcNatAd.setStore(data.getValue());
            break;
          case RATING:
            dcNatAd.setStarRating(Double.parseDouble(data.getValue()));
            break;
          default:
            invalid.inc();
            throw new MapperException(
                "Asset %s has unsupported Image type: %s",
                asset.getId(), matchingReqAsset.getImg().getType());
        }
      }
    }

    return dcNatAd;
  }

  public Impression.Native.Builder buildNativeRequest(NetworkBid.BidRequest.AdSlot dcSlot) {
    Impression.Native.Builder impNativ = Impression.Native.newBuilder().setVer("1.0");
    NativeRequest.Builder nativReq = NativeRequest.newBuilder().setVer("1.0");
    int id = 0;

    for (NetworkBid.BidRequest.AdSlot.NativeAdTemplate dcNativ : dcSlot.getNativeAdTemplateList()) {
      long reqBits = dcNativ.getRequiredFields();
      long bits = dcNativ.getRecommendedFields() | reqBits;

      if ((bits & NetworkBid.BidRequest.AdSlot.NativeAdTemplate.Fields.HEADLINE_VALUE) != 0) {
        NativeRequest.Asset.Builder asset = newAsset(++id, reqBits,
            NetworkBid.BidRequest.AdSlot.NativeAdTemplate.Fields.HEADLINE_VALUE);
        NativeRequest.Asset.Title.Builder title = NativeRequest.Asset.Title.newBuilder();
        if (dcNativ.hasHeadlineMaxSafeLength()) {
          title.setLen(dcNativ.getHeadlineMaxSafeLength());
          nativReq.addAssets(extMapNative(dcNativ, asset.setTitle(title)));
        } else {
          incomplete.inc();
          if (logger.isDebugEnabled()) {
            logger.debug("Headline ignored, missing value: {}",
                TextFormat.shortDebugString(dcNativ));
          }
        }
      }

      if ((bits & NetworkBid.BidRequest.AdSlot.NativeAdTemplate.Fields.BODY_VALUE) != 0) {
        NativeRequest.Asset.Builder asset = newAsset(++id, reqBits,
            NetworkBid.BidRequest.AdSlot.NativeAdTemplate.Fields.BODY_VALUE);
        NativeRequest.Asset.Data.Builder data = NativeRequest.Asset.Data.newBuilder()
            .setType(NativeRequest.Asset.Data.DataAssetType.DESC);
        if (dcNativ.hasBodyMaxSafeLength()) {
          data.setLen(dcNativ.getBodyMaxSafeLength());
          nativReq.addAssets(extMapNative(dcNativ, asset.setData(data)));
        } else {
          incomplete.inc();
          if (logger.isDebugEnabled()) {
            logger.debug("Body ignored, missing value: {}",
                TextFormat.shortDebugString(dcNativ));
          }
        }
      }

      if ((bits & NetworkBid.BidRequest.AdSlot.NativeAdTemplate.Fields.CALL_TO_ACTION_VALUE) != 0) {
        NativeRequest.Asset.Builder asset = newAsset(++id, reqBits,
            NetworkBid.BidRequest.AdSlot.NativeAdTemplate.Fields.CALL_TO_ACTION_VALUE);
        NativeRequest.Asset.Data.Builder data = NativeRequest.Asset.Data.newBuilder()
            .setType(NativeRequest.Asset.Data.DataAssetType.CTATEXT);
        if (dcNativ.hasCallToActionMaxSafeLength()) {
          data.setLen(dcNativ.getCallToActionMaxSafeLength());
          nativReq.addAssets(extMapNative(dcNativ, asset.setData(data)));
        } else {
          incomplete.inc();
          if (logger.isDebugEnabled()) {
            logger.debug("Call to action ignored, missing value: {}",
                TextFormat.shortDebugString(dcNativ));
          }
        }
      }

      if ((bits & NetworkBid.BidRequest.AdSlot.NativeAdTemplate.Fields.ADVERTISER_VALUE) != 0) {
        NativeRequest.Asset.Builder asset = newAsset(++id, reqBits,
            NetworkBid.BidRequest.AdSlot.NativeAdTemplate.Fields.ADVERTISER_VALUE);
        NativeRequest.Asset.Data.Builder data = NativeRequest.Asset.Data.newBuilder()
            .setType(NativeRequest.Asset.Data.DataAssetType.SPONSORED);
        nativReq.addAssets(extMapNative(dcNativ, asset.setData(data)));
      }

      if ((bits & NetworkBid.BidRequest.AdSlot.NativeAdTemplate.Fields.IMAGE_VALUE) != 0) {
        NativeRequest.Asset.Builder asset = newAsset(++id, reqBits,
            NetworkBid.BidRequest.AdSlot.NativeAdTemplate.Fields.IMAGE_VALUE);
        NativeRequest.Asset.Image.Builder image = NativeRequest.Asset.Image.newBuilder()
            .setType(NativeRequest.Asset.Image.ImageAssetType.MAIN);
        if (dcNativ.hasImageWidth()) {
          image.setWmin(dcNativ.getImageWidth());
        }
        if (dcNativ.hasImageHeight()) {
          image.setHmin(dcNativ.getImageHeight());
        }
        nativReq.addAssets(extMapNative(dcNativ, asset.setImg(image)));
      }

      if ((bits & NetworkBid.BidRequest.AdSlot.NativeAdTemplate.Fields.LOGO_VALUE) != 0) {
        NativeRequest.Asset.Builder asset = newAsset(++id, reqBits,
            NetworkBid.BidRequest.AdSlot.NativeAdTemplate.Fields.LOGO_VALUE);
        NativeRequest.Asset.Image.Builder image = NativeRequest.Asset.Image.newBuilder()
            .setType(NativeRequest.Asset.Image.ImageAssetType.LOGO);
        if (dcNativ.hasLogoWidth()) {
          image.setWmin(dcNativ.getLogoWidth());
        }
        if (dcNativ.hasLogoHeight()) {
          image.setHmin(dcNativ.getLogoHeight());
        }
        nativReq.addAssets(extMapNative(dcNativ, asset.setImg(image)));
      }

      if ((bits & NetworkBid.BidRequest.AdSlot.NativeAdTemplate.Fields.APP_ICON_VALUE) != 0) {
        NativeRequest.Asset.Builder asset = newAsset(++id, reqBits,
            NetworkBid.BidRequest.AdSlot.NativeAdTemplate.Fields.APP_ICON_VALUE);
        NativeRequest.Asset.Image.Builder image = NativeRequest.Asset.Image.newBuilder()
            .setType(NativeRequest.Asset.Image.ImageAssetType.ICON);
        if (dcNativ.hasAppIconWidth()) {
          image.setWmin(dcNativ.getAppIconWidth());
        }
        if (dcNativ.hasAppIconHeight()) {
          image.setHmin(dcNativ.getAppIconHeight());
        }
        nativReq.addAssets(extMapNative(dcNativ, asset.setImg(image)));
      }

      if ((bits & NetworkBid.BidRequest.AdSlot.NativeAdTemplate.Fields.STAR_RATING_VALUE) != 0) {
        NativeRequest.Asset.Builder asset = newAsset(++id, reqBits,
            NetworkBid.BidRequest.AdSlot.NativeAdTemplate.Fields.STAR_RATING_VALUE);
        NativeRequest.Asset.Data.Builder data = NativeRequest.Asset.Data.newBuilder()
            .setType(NativeRequest.Asset.Data.DataAssetType.RATING);
        nativReq.addAssets(extMapNative(dcNativ, asset.setData(data)));
      }

      if ((bits & NetworkBid.BidRequest.AdSlot.NativeAdTemplate.Fields.PRICE_VALUE) != 0) {
        NativeRequest.Asset.Builder asset = newAsset(++id, reqBits,
            NetworkBid.BidRequest.AdSlot.NativeAdTemplate.Fields.PRICE_VALUE);
        NativeRequest.Asset.Data.Builder data = NativeRequest.Asset.Data.newBuilder()
            .setType(NativeRequest.Asset.Data.DataAssetType.PRICE);
        if (dcNativ.hasPriceMaxSafeLength()) {
          data.setLen(dcNativ.getPriceMaxSafeLength());
          nativReq.addAssets(extMapNative(dcNativ, asset.setData(data)));
        } else {
          incomplete.inc();
          if (logger.isDebugEnabled()) {
            logger.debug("Price ignored, missing value: {}",
                TextFormat.shortDebugString(dcNativ));
          }
        }
      }

      if ((bits & NetworkBid.BidRequest.AdSlot.NativeAdTemplate.Fields.STORE_VALUE) != 0) {
        NativeRequest.Asset.Builder asset = newAsset(++id, reqBits,
            NetworkBid.BidRequest.AdSlot.NativeAdTemplate.Fields.STORE_VALUE);
        NativeRequest.Asset.Data.Builder data = NativeRequest.Asset.Data.newBuilder()
            .setType(NativeRequest.Asset.Data.DataAssetType.ADDRESS);
        if (dcNativ.hasStoreMaxSafeLength()) {
          data.setLen(dcNativ.getStoreMaxSafeLength());
          nativReq.addAssets(extMapNative(dcNativ, asset.setData(data)));
        } else {
          incomplete.inc();
          if (logger.isDebugEnabled()) {
            logger.debug("Store ignored, missing value: {}",
                TextFormat.shortDebugString(dcNativ));
          }
        }
      }
    }

    return impNativ.setRequest(nativReq);
  }

  private static NativeRequest.Asset.Builder newAsset(int id, long reqBits, int bit) {
    return NativeRequest.Asset.newBuilder()
        .setId(id)
        .setRequired((reqBits & bit) != 0);
  }

  protected NativeRequest.Asset.Builder extMapNative(
      NetworkBid.BidRequest.AdSlot.NativeAdTemplate dcNativ,
      NativeRequest.Asset.Builder asset) {
    for (ExtMapper extMapper : extMappers) {
      extMapper.toOpenRtbNative(dcNativ, asset);
    }

    return asset;
  }
}
