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
import com.google.openrtb.OpenRtb.BidRequest.Imp;
import com.google.openrtb.OpenRtb.BidRequest.Imp.APIFramework;
import com.google.openrtb.OpenRtb.BidResponse.SeatBid.Bid;
import com.google.openrtb.OpenRtb.NativeRequest;
import com.google.openrtb.OpenRtb.NativeResponse;
import com.google.openrtb.json.OpenRtbJsonFactory;
import com.google.openrtb.json.OpenRtbNativeJsonReader;
import com.google.protobuf.TextFormat;
import com.google.protos.adx.NetworkBid;

import com.codahale.metrics.Counter;
import com.codahale.metrics.MetricRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

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
  private final Counter unsupported = new Counter();

  @Inject
  public DoubleClickOpenRtbNativeMapper(
      MetricRegistry metricRegistry,
      @Nullable OpenRtbJsonFactory jsonFactory,
      Iterable<ExtMapper> extMappers) {
    this.jsonReader = jsonFactory == null ? null : jsonFactory.newNativeReader();
    this.extMappers = ImmutableList.copyOf(extMappers);
    Class<? extends DoubleClickOpenRtbNativeMapper> cls = getClass();
    metricRegistry.register(MetricRegistry.name(cls, "invalid"), invalid);
    metricRegistry.register(MetricRegistry.name(cls, "incomplete"), incomplete);
    metricRegistry.register(MetricRegistry.name(cls, "unsupported"), unsupported);
  }

  public NetworkBid.BidResponse.Ad.NativeAd.Builder buildNativeResponse(Bid bid, Imp matchingImp) {
    NativeRequest natReq;
    NativeResponse natResp;

    switch (matchingImp.getNative().getRequestOneofCase()) {
      case REQUEST_NATIVE:
        natReq = matchingImp.getNative().getRequestNative();
        break;

      case REQUEST:
        if (jsonReader != null) {
          try {
            natReq = jsonReader.readNativeRequest(matchingImp.getNative().getRequest());
          } catch (IOException e) {
            throw new MapperException(
                "Failed parsing the matching Native request: " + e.getMessage());
          }
        } else {
          throw new MapperException("Not configured for OpenRTB/JSON native ads");
        }
        break;

      case REQUESTONEOF_NOT_SET:
      default:
        throw new MapperException("Missing matching Native request or request_native");
    }

    switch (bid.getAdmOneofCase()) {
      case ADM_NATIVE:
        natResp = bid.getAdmNative();
        break;

      case ADM:
        if (jsonReader != null) {
          try {
            natResp = jsonReader.readNativeResponse(bid.getAdm());
          } catch (IOException e) {
            throw new MapperException("Failed parsing adm as a Native response: " + e.getMessage());
          }
        } else {
          throw new MapperException("Not configured for OpenRTB/JSON native ads");
        }
        break;

      case ADMONEOF_NOT_SET:
      default:
        throw new MapperException("Missing adm or adm_native");
    }

    return buildRespAd(natReq, natResp);
  }

  protected NetworkBid.BidResponse.Ad.NativeAd.Builder buildRespAd(
      NativeRequest natReq, NativeResponse natResp) {
    NetworkBid.BidResponse.Ad.NativeAd.Builder dcNatAd =
        NetworkBid.BidResponse.Ad.NativeAd.newBuilder()
            .addAllImpressionTrackingUrl(natResp.getImptrackersList());

    if (natResp.getLink().getClicktrackersCount() != 0) {
      dcNatAd.setClickTrackingUrl(natResp.getLink().getClicktrackers(0));
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
        if (logger.isDebugEnabled()) {
          logger.debug("Asset.id doesn't match any request native asset: {}", asset.getId());
        }
        continue;
      }

      if (asset.hasLink()) {
        dcNatAd.setStore(asset.getLink().getUrl());
      }

      switch (asset.getAssetOneofCase()) {
        case TITLE:
          buildRespTitle(asset, matchingReqAsset, dcNatAd);
          break;

        case IMG:
          buildRespImg(asset, matchingReqAsset, dcNatAd);
          break;

        case DATA:
          buildRespData(asset, matchingReqAsset, dcNatAd);
          break;

        case VIDEO:
          unsupported.inc();
          if (logger.isDebugEnabled()) {
            logger.debug("Video Asset not supported: {}", asset.getId());
          }
          continue;

        case ASSETONEOF_NOT_SET:
          incomplete.inc();
          if (logger.isDebugEnabled()) {
            logger.debug("Asset is empty, need one of title/img/data: {}", asset.getId());
          }
          continue;
      }
    }

    return dcNatAd;
  }

  protected void buildRespTitle(NativeResponse.Asset asset, NativeRequest.Asset matchingReqAsset,
      NetworkBid.BidResponse.Ad.NativeAd.Builder dcNatAd) {
    if (!matchingReqAsset.hasTitle()) {
      failRespAsset(asset);
      return;
    }
    dcNatAd.setHeadline(asset.getTitle().getText());
  }

  protected void buildRespImg(NativeResponse.Asset asset, NativeRequest.Asset matchingReqAsset,
      NetworkBid.BidResponse.Ad.NativeAd.Builder dcNatAd) {
    if (!matchingReqAsset.hasImg()) {
      failRespAsset(asset);
      return;
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
        if (logger.isDebugEnabled()) {
          logger.debug("Asset {} has unsupported Image type: {}",
              asset.getId(), matchingReqAsset.getImg().getType());
        }
    }
  }

  protected void buildRespData(NativeResponse.Asset asset, NativeRequest.Asset matchingReqAsset,
      NetworkBid.BidResponse.Ad.NativeAd.Builder dcNatAd) {
    if (!matchingReqAsset.hasData()) {
      failRespAsset(asset);
      return;
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
        if (logger.isDebugEnabled()) {
          logger.debug("Asset {} has unsupported Image type: {}",
              asset.getId(), matchingReqAsset.getImg().getType());
        }
    }
  }

  protected void failRespAsset(NativeResponse.Asset asset) {
    invalid.inc();
    if (logger.isDebugEnabled()) {
      logger.debug(
          "Asset.id doesn't match request asset: %s", asset.getId());
    }
  }

  public Imp.Native.Builder buildNativeRequest(NetworkBid.BidRequest.AdSlot dcSlot) {
    Imp.Native.Builder impNativ = Imp.Native.newBuilder()
        .setVer("1.0")
        .addAllBattr(CreativeAttributeMapper.toOpenRtb(dcSlot.getExcludedAttributeList(), null));
    NativeRequest.Builder nativReq = NativeRequest.newBuilder().setVer("1.0");

    if (!dcSlot.getExcludedAttributeList().contains(32 /* MraidType: Mraid 1.0 */)) {
      impNativ.addApi(APIFramework.MRAID_1);
    }
    impNativ.addApi(APIFramework.MRAID_2);

    int id = 0;

    for (NetworkBid.BidRequest.AdSlot.NativeAdTemplate dcNativ : dcSlot.getNativeAdTemplateList()) {
      long bits = dcNativ.getRecommendedFields() | dcNativ.getRequiredFields();

      if ((bits & NetworkBid.BidRequest.AdSlot.NativeAdTemplate.Fields.HEADLINE_VALUE) != 0) {
        addAsset(nativReq, ++id, buildReqAssetTitle(dcNativ));
      }
      if ((bits & NetworkBid.BidRequest.AdSlot.NativeAdTemplate.Fields.BODY_VALUE) != 0) {
        addAsset(nativReq, ++id, buildReqAssetBody(dcNativ));
      }
      if ((bits & NetworkBid.BidRequest.AdSlot.NativeAdTemplate.Fields.CALL_TO_ACTION_VALUE) != 0) {
        addAsset(nativReq, ++id, buildReqAssetCTA(dcNativ));
      }
      if ((bits & NetworkBid.BidRequest.AdSlot.NativeAdTemplate.Fields.ADVERTISER_VALUE) != 0) {
        addAsset(nativReq, ++id, buildReqAssetAdvertiser(dcNativ));
      }
      if ((bits & NetworkBid.BidRequest.AdSlot.NativeAdTemplate.Fields.IMAGE_VALUE) != 0) {
        addAsset(nativReq, ++id, buildReqAssetImage(dcNativ));
      }
      if ((bits & NetworkBid.BidRequest.AdSlot.NativeAdTemplate.Fields.LOGO_VALUE) != 0) {
        addAsset(nativReq, ++id, buildReqAssetLogo(dcNativ));
      }
      if ((bits & NetworkBid.BidRequest.AdSlot.NativeAdTemplate.Fields.APP_ICON_VALUE) != 0) {
        addAsset(nativReq, ++id, buildReqAssetIcon(dcNativ));
      }
      if ((bits & NetworkBid.BidRequest.AdSlot.NativeAdTemplate.Fields.STAR_RATING_VALUE) != 0) {
        addAsset(nativReq, ++id, buildReqAssetStarRating(dcNativ));
      }
      if ((bits & NetworkBid.BidRequest.AdSlot.NativeAdTemplate.Fields.PRICE_VALUE) != 0) {
        addAsset(nativReq, ++id, buildReqAssetPrice(dcNativ));
      }
      if ((bits & NetworkBid.BidRequest.AdSlot.NativeAdTemplate.Fields.STORE_VALUE) != 0) {
        addAsset(nativReq, ++id, buildReqAssetStore(dcNativ));
      }
    }

    return impNativ.setRequestNative(nativReq);
  }

  protected static void addAsset(
      NativeRequest.Builder dcNativ, int id, NativeRequest.Asset.Builder asset) {
    if (asset != null) {
      dcNativ.addAssets(asset.setId(id));
    }
  }

  protected NativeRequest.Asset.Builder buildReqAssetStore(
      NetworkBid.BidRequest.AdSlot.NativeAdTemplate dcNativ) {
    if (dcNativ.hasStoreMaxSafeLength()) {
      NativeRequest.Asset.Builder asset = newAsset(dcNativ.getRequiredFields(),
          NetworkBid.BidRequest.AdSlot.NativeAdTemplate.Fields.STORE);
      return extMapNative(dcNativ, asset.setData(NativeRequest.Asset.Data.newBuilder()
          .setType(NativeRequest.Asset.Data.DataAssetType.ADDRESS)
          .setLen(dcNativ.getStoreMaxSafeLength())));
    } else {
      return failReqAsset(dcNativ, NetworkBid.BidRequest.AdSlot.NativeAdTemplate.Fields.STORE);
    }
  }

  protected NativeRequest.Asset.Builder buildReqAssetPrice(
      NetworkBid.BidRequest.AdSlot.NativeAdTemplate dcNativ) {
    if (dcNativ.hasPriceMaxSafeLength()) {
      NativeRequest.Asset.Builder asset = newAsset(dcNativ.getRequiredFields(),
          NetworkBid.BidRequest.AdSlot.NativeAdTemplate.Fields.PRICE);
      return extMapNative(dcNativ, asset.setData(NativeRequest.Asset.Data.newBuilder()
          .setType(NativeRequest.Asset.Data.DataAssetType.PRICE)
          .setLen(dcNativ.getPriceMaxSafeLength())));
    } else {
      return failReqAsset(dcNativ, NetworkBid.BidRequest.AdSlot.NativeAdTemplate.Fields.PRICE);
    }
  }

  protected NativeRequest.Asset.Builder buildReqAssetStarRating(
      NetworkBid.BidRequest.AdSlot.NativeAdTemplate dcNativ) {
    NativeRequest.Asset.Builder asset = newAsset(dcNativ.getRequiredFields(),
        NetworkBid.BidRequest.AdSlot.NativeAdTemplate.Fields.STAR_RATING);
    return extMapNative(dcNativ, asset.setData(NativeRequest.Asset.Data.newBuilder()
        .setType(NativeRequest.Asset.Data.DataAssetType.RATING)));
  }

  protected NativeRequest.Asset.Builder buildReqAssetAdvertiser(
      NetworkBid.BidRequest.AdSlot.NativeAdTemplate dcNativ) {
    NativeRequest.Asset.Builder asset = newAsset(dcNativ.getRequiredFields(),
        NetworkBid.BidRequest.AdSlot.NativeAdTemplate.Fields.ADVERTISER);
    return extMapNative(dcNativ, asset.setData(NativeRequest.Asset.Data.newBuilder()
        .setType(NativeRequest.Asset.Data.DataAssetType.SPONSORED)));
  }

  protected NativeRequest.Asset.Builder buildReqAssetCTA(
      NetworkBid.BidRequest.AdSlot.NativeAdTemplate dcNativ) {
    if (dcNativ.hasCallToActionMaxSafeLength()) {
      NativeRequest.Asset.Builder asset = newAsset(dcNativ.getRequiredFields(),
          NetworkBid.BidRequest.AdSlot.NativeAdTemplate.Fields.CALL_TO_ACTION);
      return extMapNative(dcNativ, asset.setData(NativeRequest.Asset.Data.newBuilder()
          .setType(NativeRequest.Asset.Data.DataAssetType.CTATEXT)
          .setLen(dcNativ.getCallToActionMaxSafeLength())));
    } else {
      return failReqAsset(
          dcNativ, NetworkBid.BidRequest.AdSlot.NativeAdTemplate.Fields.CALL_TO_ACTION);
    }
  }

  protected NativeRequest.Asset.Builder buildReqAssetBody(
      NetworkBid.BidRequest.AdSlot.NativeAdTemplate dcNativ) {
    if (dcNativ.hasBodyMaxSafeLength()) {
      NativeRequest.Asset.Builder asset = newAsset(dcNativ.getRequiredFields(),
          NetworkBid.BidRequest.AdSlot.NativeAdTemplate.Fields.BODY);
      NativeRequest.Asset.Data.Builder data = NativeRequest.Asset.Data.newBuilder()
          .setType(NativeRequest.Asset.Data.DataAssetType.DESC)
          .setLen(dcNativ.getBodyMaxSafeLength());
      return extMapNative(dcNativ, asset.setData(data));
    } else {
      return failReqAsset(dcNativ, NetworkBid.BidRequest.AdSlot.NativeAdTemplate.Fields.BODY);
    }
  }

  protected NativeRequest.Asset.Builder buildReqAssetIcon(
      NetworkBid.BidRequest.AdSlot.NativeAdTemplate dcNativ) {
    NativeRequest.Asset.Builder asset = newAsset(dcNativ.getRequiredFields(),
        NetworkBid.BidRequest.AdSlot.NativeAdTemplate.Fields.APP_ICON);
    NativeRequest.Asset.Image.Builder image = NativeRequest.Asset.Image.newBuilder()
        .setType(NativeRequest.Asset.Image.ImageAssetType.ICON);
    if (dcNativ.hasAppIconWidth()) {
      image.setWmin(dcNativ.getAppIconWidth());
    }
    if (dcNativ.hasAppIconHeight()) {
      image.setHmin(dcNativ.getAppIconHeight());
    }
    return extMapNative(dcNativ, asset.setImg(image));
  }

  protected NativeRequest.Asset.Builder buildReqAssetLogo(
      NetworkBid.BidRequest.AdSlot.NativeAdTemplate dcNativ) {
    NativeRequest.Asset.Builder asset = newAsset(dcNativ.getRequiredFields(),
        NetworkBid.BidRequest.AdSlot.NativeAdTemplate.Fields.LOGO);
    NativeRequest.Asset.Image.Builder image = NativeRequest.Asset.Image.newBuilder()
        .setType(NativeRequest.Asset.Image.ImageAssetType.LOGO);
    if (dcNativ.hasLogoWidth()) {
      image.setWmin(dcNativ.getLogoWidth());
    }
    if (dcNativ.hasLogoHeight()) {
      image.setHmin(dcNativ.getLogoHeight());
    }
    return extMapNative(dcNativ, asset.setImg(image));
  }

  protected NativeRequest.Asset.Builder buildReqAssetImage(
      NetworkBid.BidRequest.AdSlot.NativeAdTemplate dcNativ) {
    NativeRequest.Asset.Builder asset = newAsset(dcNativ.getRequiredFields(),
        NetworkBid.BidRequest.AdSlot.NativeAdTemplate.Fields.IMAGE);
    NativeRequest.Asset.Image.Builder image = NativeRequest.Asset.Image.newBuilder()
        .setType(NativeRequest.Asset.Image.ImageAssetType.MAIN);
    if (dcNativ.hasImageWidth()) {
      image.setWmin(dcNativ.getImageWidth());
    }
    if (dcNativ.hasImageHeight()) {
      image.setHmin(dcNativ.getImageHeight());
    }
    return extMapNative(dcNativ, asset.setImg(image));
  }

  protected NativeRequest.Asset.Builder buildReqAssetTitle(
      NetworkBid.BidRequest.AdSlot.NativeAdTemplate dcNativ) {
    if (dcNativ.hasHeadlineMaxSafeLength()) {
      NativeRequest.Asset.Builder asset = newAsset(dcNativ.getRequiredFields(),
          NetworkBid.BidRequest.AdSlot.NativeAdTemplate.Fields.HEADLINE);
      NativeRequest.Asset.Title.Builder title = NativeRequest.Asset.Title.newBuilder();
      title.setLen(dcNativ.getHeadlineMaxSafeLength());
      return extMapNative(dcNativ, asset.setTitle(title));
    } else {
      return failReqAsset(dcNativ, NetworkBid.BidRequest.AdSlot.NativeAdTemplate.Fields.HEADLINE);
    }
  }

  @Nullable protected NativeRequest.Asset.Builder failReqAsset(
      NetworkBid.BidRequest.AdSlot.NativeAdTemplate dcNativ,
      NetworkBid.BidRequest.AdSlot.NativeAdTemplate.Fields field) {
    incomplete.inc();
    if (logger.isDebugEnabled()) {
      logger.debug(field.name() + " ignored, missing value: {}",
          TextFormat.shortDebugString(dcNativ));
    }
    return null;
  }

  protected static NativeRequest.Asset.Builder newAsset(
      long reqBits, NetworkBid.BidRequest.AdSlot.NativeAdTemplate.Fields bit) {
    NativeRequest.Asset.Builder asset = NativeRequest.Asset.newBuilder();
    if ((reqBits & bit.getNumber()) != 0) {
      asset.setRequired(true);
    }
    return asset;
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
