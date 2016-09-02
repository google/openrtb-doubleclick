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

import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.Math.max;
import static java.lang.Math.min;

import com.codahale.metrics.Counter;
import com.codahale.metrics.MetricRegistry;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.io.BaseEncoding;
import com.google.doubleclick.DcExt;
import com.google.doubleclick.util.CityDMARegionKey;
import com.google.doubleclick.util.CityDMARegionValue;
import com.google.doubleclick.util.CountryCodes;
import com.google.doubleclick.util.DoubleClickMetadata;
import com.google.doubleclick.util.GeoTarget;
import com.google.openrtb.Gender;
import com.google.openrtb.OpenRtb;
import com.google.openrtb.OpenRtb.APIFramework;
import com.google.openrtb.OpenRtb.AdPosition;
import com.google.openrtb.OpenRtb.AuctionType;
import com.google.openrtb.OpenRtb.BidRequest.App;
import com.google.openrtb.OpenRtb.BidRequest.Content;
import com.google.openrtb.OpenRtb.BidRequest.Content.Builder;
import com.google.openrtb.OpenRtb.BidRequest.Data;
import com.google.openrtb.OpenRtb.BidRequest.Data.Segment;
import com.google.openrtb.OpenRtb.BidRequest.Device;
import com.google.openrtb.OpenRtb.BidRequest.Geo;
import com.google.openrtb.OpenRtb.BidRequest.Imp;
import com.google.openrtb.OpenRtb.BidRequest.Imp.Banner;
import com.google.openrtb.OpenRtb.BidRequest.Imp.Native;
import com.google.openrtb.OpenRtb.BidRequest.Imp.Pmp;
import com.google.openrtb.OpenRtb.BidRequest.Imp.Pmp.Deal;
import com.google.openrtb.OpenRtb.BidRequest.Imp.Video;
import com.google.openrtb.OpenRtb.BidRequest.Publisher;
import com.google.openrtb.OpenRtb.BidRequest.Regs;
import com.google.openrtb.OpenRtb.BidRequest.Site;
import com.google.openrtb.OpenRtb.BidRequest.User;
import com.google.openrtb.OpenRtb.BidResponse.SeatBid;
import com.google.openrtb.OpenRtb.BidResponse.SeatBid.Bid;
import com.google.openrtb.OpenRtb.CompanionType;
import com.google.openrtb.OpenRtb.ContentCategory;
import com.google.openrtb.OpenRtb.DeviceType;
import com.google.openrtb.OpenRtb.PlaybackMethod;
import com.google.openrtb.OpenRtb.Protocol;
import com.google.openrtb.json.OpenRtbJsonFactory;
import com.google.openrtb.mapper.OpenRtbMapper;
import com.google.openrtb.util.OpenRtbUtils;
import com.google.protobuf.TextFormat;
import com.google.protos.adx.NetworkBid;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Calendar;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mapping between the DoubleClick and OpenRTB models.
 *
 * <p>This class is threadsafe. Recommended use is as a singleton, but you may also want to create
 * multiple instances if you need to keep track of metrics separately for different uses
 * (for that to make sense, provide a different {@link MetricRegistry} to each instance).
 */
@Singleton
public class DoubleClickOpenRtbMapper implements OpenRtbMapper<
    NetworkBid.BidRequest, NetworkBid.BidResponse,
    NetworkBid.BidRequest.Builder, NetworkBid.BidResponse.Builder> {
  private static final Logger logger = LoggerFactory.getLogger(DoubleClickOpenRtbMapper.class);
  private static final Joiner csvJoiner = Joiner.on(",").skipNulls();
  private static final int MICROS_PER_CURRENCY_UNIT = 1_000_000;

  private final DoubleClickMetadata metadata;
  private final ImmutableList<ExtMapper> extMappers;
  private final DoubleClickOpenRtbNativeMapper nativeMapper;
  private final Counter missingCrid = new Counter();
  private final Counter invalidImp = new Counter();
  private final Counter missingSize = new Counter();
  private final Counter noVideoOrBanner = new Counter();
  private final Counter coppaTreatment = new Counter();
  private final Counter noImp = new Counter();
  private final Counter invalidGeoId = new Counter();
  private final Counter invalidHyperlocal = new Counter();
  private final Counter noCid = new Counter();
  private final Counter invalidContentCategory = new Counter();
  private final Counter unsupportedNurl = new Counter();

  @Inject
  public DoubleClickOpenRtbMapper(
      MetricRegistry metricRegistry,
      DoubleClickMetadata metadata,
      @Nullable OpenRtbJsonFactory jsonFactory,
      List<ExtMapper> extMappers) {
    this.metadata = metadata;
    this.extMappers = ImmutableList.copyOf(extMappers);
    this.nativeMapper = new DoubleClickOpenRtbNativeMapper(metricRegistry, jsonFactory, extMappers);
    Class<? extends DoubleClickOpenRtbMapper> cls = getClass();
    metricRegistry.register(MetricRegistry.name(cls, "missing-crid"), missingCrid);
    metricRegistry.register(MetricRegistry.name(cls, "invalid-imp"), invalidImp);
    metricRegistry.register(MetricRegistry.name(cls, "missing-size"), missingSize);
    metricRegistry.register(MetricRegistry.name(cls, "no-video-or-banner"), noVideoOrBanner);
    metricRegistry.register(MetricRegistry.name(cls, "coppa-treatment"), coppaTreatment);
    metricRegistry.register(MetricRegistry.name(cls, "no-imp"), noImp);
    metricRegistry.register(MetricRegistry.name(cls, "invalid-geoid"), invalidGeoId);
    metricRegistry.register(MetricRegistry.name(cls, "invalid-hyperlocal"), invalidHyperlocal);
    metricRegistry.register(MetricRegistry.name(cls, "no-cid"), noCid);
    metricRegistry.register(MetricRegistry.name(cls, "invalid-cat"), invalidContentCategory);
    metricRegistry.register(MetricRegistry.name(cls, "unsupported-nurl"), unsupportedNurl);
  }

  @Override public OpenRtb.BidRequest.Builder toOpenRtbBidRequest(NetworkBid.BidRequest dcRequest) {
    OpenRtb.BidRequest.Builder request = OpenRtb.BidRequest.newBuilder()
        .setId(MapperUtil.toBase64(dcRequest.getId()));

    if (dcRequest.getIsPing()) {
      return request;
    }

    boolean coppa = false;
    for (NetworkBid.BidRequest.UserDataTreatment dcUDT : dcRequest.getUserDataTreatmentList()) {
      if (dcUDT == NetworkBid.BidRequest.UserDataTreatment.TAG_FOR_CHILD_DIRECTED_TREATMENT) {
        coppa = true;
        break;
      }
    }
    if (coppa) {
      coppaTreatment.inc();
      request.setRegs(Regs.newBuilder().setCoppa(true));
    }

    request.setDevice(mapDevice(dcRequest));

    if (dcRequest.getMobile().getIsApp()) {
      App.Builder app = mapApp(dcRequest);
      if (app != null) {
        request.setApp(app);
      }
    } else {
      Site.Builder site = mapSite(dcRequest);
      if (site != null) {
        request.setSite(site);
      }
    }

    EnumSet<ContentCategory> cats = EnumSet.noneOf(ContentCategory.class);
    for (NetworkBid.BidRequest.AdSlot dcSlot : dcRequest.getAdslotList()) {
      Imp.Builder imp = mapImp(dcRequest, dcSlot);
      if (imp != null) {
        request.addImp(imp);
        AdCategoryMapper.toOpenRtb(dcSlot.getExcludedProductCategoryList(), cats);
        AdCategoryMapper.toOpenRtb(dcSlot.getExcludedSensitiveCategoryList(), cats);
      }
    }
    for (ContentCategory cat : cats) {
      request.addBcat(OpenRtbUtils.categoryToJsonName(cat.name()));
    }

    if (request.getImpCount() == 0) {
      noImp.inc();
      logger.debug("Request has no impressions");
    }

    User.Builder user = mapUser(dcRequest);
    if (user != null) {
      request.setUser(user);
    }

    if (dcRequest.getIsTest()) {
      request.setTest(dcRequest.getIsTest());
    }
    request.setTmax(100);

    for (ExtMapper extMapper : extMappers) {
      extMapper.toOpenRtbBidRequest(dcRequest, request);
    }

    return request;
  }

  protected Device.Builder mapDevice(NetworkBid.BidRequest dcRequest) {
    Device.Builder device = Device.newBuilder();

    if (dcRequest.hasIp()) {
      if (dcRequest.getIp().size() <= 4) {
        device.setIp(MapperUtil.toIpv4String(dcRequest.getIp()));
      } else {
        device.setIpv6(MapperUtil.toIpv6String(dcRequest.getIp()));
      }
    }

    if (dcRequest.hasUserAgent()) {
      device.setUa(dcRequest.getUserAgent());
    }

    Geo.Builder geo = mapGeo(dcRequest);
    if (geo != null) {
      device.setGeo(geo);
    }

    if (dcRequest.hasMobile()) {
      NetworkBid.BidRequest.Mobile dcMobile = dcRequest.getMobile();

      if (dcMobile.hasAdvertisingId()) {
        device.setIfa(MapperUtil.toUUID(dcMobile.getAdvertisingId(),
            "iOS".equals(DeviceOSMapper.toOpenRtb(dcRequest.getDevice().getPlatform()))));
      }
      if (dcMobile.hasHashedIdfa()) {
        device.setDpidmd5(BaseEncoding.base16().encode(dcMobile.getHashedIdfa().toByteArray()));
      }
    }

    if (dcRequest.hasDevice()) {
      NetworkBid.BidRequest.Device dcDevice = dcRequest.getDevice();
      if (dcDevice.hasCarrierId()) {
        device.setCarrier(String.valueOf(dcDevice.getCarrierId()));
      }
      if (dcDevice.hasBrand()) {
        device.setMake(dcDevice.getBrand());
      }
      if (dcDevice.hasModel()) {
        device.setModel(dcDevice.getModel());
      }
      if (dcDevice.hasPlatform()) {
        device.setOs(DeviceOSMapper.toOpenRtb(dcDevice.getPlatform()));
      }
      if (dcDevice.getOsVersion().hasMajor()) {
        NetworkBid.BidRequest.Device.OsVersion dcVer = dcDevice.getOsVersion();
        StringBuilder osv = new StringBuilder().append(dcVer.getMajor());
        if (dcVer.hasMinor()) {
          osv.append('.').append(dcVer.getMinor());
          if (dcVer.hasMicro()) {
            osv.append('.').append(dcVer.getMicro());
          }
        }
        device.setOsv(osv.toString());
      }
      if (dcDevice.hasHardwareVersion()) {
        device.setHwv(dcDevice.getHardwareVersion());
      }
      if (dcDevice.hasDeviceType()) {
        DeviceType type = DeviceTypeMapper.toOpenRtb(dcDevice.getDeviceType());
        if (type != null) {
          device.setDevicetype(type);
        }
      }
      if (dcDevice.hasScreenWidth()) {
        device.setW(dcDevice.getScreenWidth());
      }
      if (dcDevice.hasScreenHeight()) {
        device.setH(dcDevice.getScreenHeight());
      }
      if (dcDevice.hasScreenPixelRatioMillis()) {
        device.setPxratio(dcDevice.getScreenPixelRatioMillis() / 1000.0);
      }
    }

    for (ExtMapper extMapper : extMappers) {
      extMapper.toOpenRtbDevice(dcRequest, device);
    }

    return device;
  }

  @Nullable protected Geo.Builder mapGeo(NetworkBid.BidRequest dcRequest) {
    if (!dcRequest.hasGeoCriteriaId() && !dcRequest.hasHyperlocalSet()
        && !dcRequest.hasPostalCode() && !dcRequest.hasPostalCodePrefix()) {
      return null;
    }

    Geo.Builder geo = Geo.newBuilder();

    if (dcRequest.hasPostalCode()) {
      geo.setZip(dcRequest.getPostalCode());
    } else if (dcRequest.hasPostalCodePrefix()) {
      geo.setZip(dcRequest.getPostalCodePrefix());
    }

    if (dcRequest.hasGeoCriteriaId()) {
      int geoCriteriaId = dcRequest.getGeoCriteriaId();

      GeoTarget geoTarget = metadata.geoTargetFor(geoCriteriaId);

      if (geoTarget == null) {
        invalidGeoId.inc();
        if (logger.isDebugEnabled()) {
          logger.debug("Received unknown geo_criteria_id: {}", geoCriteriaId);
        }
      } else {
        mapGeoTarget(geoTarget, geo);
      }
    }

    if (dcRequest.hasHyperlocalSet()) {
      if (dcRequest.getHyperlocalSet().hasCenterPoint()) {
        NetworkBid.BidRequest.Hyperlocal.Point center =
            dcRequest.getHyperlocalSet().getCenterPoint();
        if (center.hasLatitude() && center.hasLongitude()) {
          geo.setLat(center.getLatitude());
          geo.setLon(center.getLongitude());
        }
      }
    }

    if (dcRequest.hasTimezoneOffset()) {
      geo.setUtcoffset(dcRequest.getTimezoneOffset());
    }

    for (ExtMapper extMapper : extMappers) {
      extMapper.toOpenRtbGeo(dcRequest, geo);
    }

    return geo;
  }

  protected void mapGeoTarget(GeoTarget geoTarget, Geo.Builder geo) {
    for (int chain = 0; chain < 2; ++chain) {
      String countryAlpha2 = null;
      int cityCriteriaId = -1;
      String dmaRegionName = null;

      for (GeoTarget target = geoTarget; target != null;
          // Looks up the canonical chain last, so its results overwrite those
          // obtained by the parentId chain if there's conflict
          target = (chain == 0) ? target.idParent() : target.canonParent()) {

        if (target.countryCode() != null) {
          countryAlpha2 = target.countryCode();
        }
        switch (target.type()) {
          case CITY:
          case PREFECTURE:
            geo.setCity(target.name());
            geo.setCountry(target.countryCode());
            cityCriteriaId = target.criteriaId();
            break;

          case DMA_REGION:
            dmaRegionName = target.name();
            break;

          case STATE:
          case REGION:
            geo.setRegion(target.name());
            break;

          default:
        }
      }

      if (countryAlpha2 != null) {
        CountryCodes countryCodes = metadata.countryCodes().get(countryAlpha2);
        if (countryCodes != null) {
          geo.setCountry(countryCodes.alpha3());
        }
      }

      if (cityCriteriaId != -1 && dmaRegionName != null) {
        CityDMARegionValue dma = metadata.dmaRegions().get(
            new CityDMARegionKey(cityCriteriaId, dmaRegionName));
        if (dma != null) {
          geo.setMetro(String.valueOf(dma.regionCode()));
        }
      }
    }
  }

  @Nullable protected App.Builder mapApp(NetworkBid.BidRequest dcRequest) {
    NetworkBid.BidRequest.Mobile dcMobile = dcRequest.getMobile();
    App.Builder app = App.newBuilder();
    boolean mapped = false;

    Content.Builder content = mapAppContent(dcRequest);
    if (content != null) {
      app.setContent(content);
      mapped = true;
    }

    if (dcMobile.hasAppId()) {
      app.setBundle(dcMobile.getAppId());
      mapped = true;
    }
    if (dcMobile.hasAppName()) {
      app.setName(dcMobile.getAppName());
      mapped = true;
    }

    Publisher.Builder pub = mapPublisher(dcRequest);
    if (pub != null) {
      app.setPublisher(pub);
      mapped = true;
    }

    if (!mapped) {
      return null;
    }

    for (ExtMapper extMapper : extMappers) {
      extMapper.toOpenRtbApp(dcRequest, app);
    }

    return app;
  }

  @Nullable protected Content.Builder mapAppContent(NetworkBid.BidRequest dcRequest) {
    Content.Builder content = mapContent(dcRequest);
    if (content == null) {
      if (!dcRequest.hasUrl() && !dcRequest.hasAnonymousId()
          && !dcRequest.getMobile().hasAppRating()) {
        return null;
      } else {
        content = Content.newBuilder();
      }
    }

    if (dcRequest.hasUrl()) {
      content.setUrl(dcRequest.getUrl());
    }
    if (dcRequest.hasAnonymousId()) {
      content.setId(dcRequest.getAnonymousId());
    }
    if (dcRequest.getMobile().hasAppRating()) {
      content.setUserrating(String.valueOf(dcRequest.getMobile().getAppRating()));
    }

    return content;
  }

  @Nullable protected Site.Builder mapSite(NetworkBid.BidRequest dcRequest) {
    Site.Builder site = Site.newBuilder();
    boolean mapped = false;

    Builder content = mapContent(dcRequest);
    if (content != null) {
      site.setContent(content);
      mapped = true;
    }

    if (dcRequest.hasUrl()) {
      site.setPage(dcRequest.getUrl());
      mapped = true;
    }
    if (dcRequest.hasAnonymousId()) {
      site.setName(dcRequest.getAnonymousId());
      mapped = true;
    }

    Publisher.Builder pub = mapPublisher(dcRequest);
    if (pub != null) {
      site.setPublisher(pub);
      mapped = true;
    }

    if (dcRequest.getMobile().hasIsMobileWebOptimized()) {
      site.setMobile(dcRequest.getMobile().getIsMobileWebOptimized());
      mapped = true;
    }

    if (!mapped) {
      return null;
    }

    for (ExtMapper extMapper : extMappers) {
      extMapper.toOpenRtbSite(dcRequest, site);
    }

    return site;
  }

  @Nullable protected Content.Builder mapContent(NetworkBid.BidRequest dcRequest) {
    if (dcRequest.getDetectedLanguageCount() == 0
        && dcRequest.getDetectedContentLabelCount() == 0
        && !dcRequest.getVideo().hasContentAttributes()) {
      return null;
    }

    Content.Builder content = Content.newBuilder();
    int nLangs = dcRequest.getDetectedLanguageCount();

    if (nLangs == 1) {
      content.setLanguage(mapLanguage(dcRequest.getDetectedLanguage(0)));
    } else if (nLangs != 0) {
      StringBuilder sb = new StringBuilder(nLangs * 3 - 1);
      for (String langCulture : dcRequest.getDetectedLanguageList()) {
        if (sb.length() != 0) {
          sb.append(',');
        }
        sb.append(mapLanguage(langCulture));
      }
      content.setLanguage(sb.toString());
    }

    String rating = ContentRatingMapper.toOpenRtb(dcRequest.getDetectedContentLabelList());
    if (rating != null) {
      content.setContentrating(rating);
    }

    if (dcRequest.getVideo().hasContentAttributes()) {
      NetworkBid.BidRequest.Video.ContentAttributes dcContent =
          dcRequest.getVideo().getContentAttributes();
      if (dcContent.hasTitle()) {
        content.setTitle(dcContent.getTitle());
      }
      if (dcContent.hasDurationSeconds()) {
        content.setLen(dcContent.getDurationSeconds());
      }
      content.setKeywords(csvJoiner.join(dcContent.getKeywordsList()));
    }

    return content;
  }

  @Nullable protected Publisher.Builder mapPublisher(NetworkBid.BidRequest dcRequest) {
    if (!dcRequest.hasSellerNetworkId()) {
      return null;
    }

    Publisher.Builder publisher = Publisher.newBuilder()
        .setId(String.valueOf(dcRequest.getSellerNetworkId()));

    String sellerNetwork = metadata.sellerNetworks().get(dcRequest.getSellerNetworkId());
    if (sellerNetwork != null) {
      publisher.setName(sellerNetwork);
    }

    return publisher;
  }

  @Nullable protected Imp.Builder mapImp(
      NetworkBid.BidRequest dcRequest, NetworkBid.BidRequest.AdSlot dcSlot) {
    Imp.Builder imp = Imp.newBuilder()
        .setId(String.valueOf(dcSlot.getId()));

    Long bidFloor = null;

    for (NetworkBid.BidRequest.AdSlot.MatchingAdData dcAdData : dcSlot.getMatchingAdDataList()) {
      if (dcAdData.hasMinimumCpmMicros()) {
        bidFloor = (bidFloor == null)
            ? dcAdData.getMinimumCpmMicros()
            : min(bidFloor, dcAdData.getMinimumCpmMicros());
      }

      Pmp.Builder pmp = mapPmp(dcAdData);
      if (pmp != null) {
        imp.setPmp(pmp);
      }
    }

    if (bidFloor != null) {
      imp.setBidfloor(((double) bidFloor) / MICROS_PER_CURRENCY_UNIT);
    }

    boolean interstitial = dcRequest.getMobile().getIsInterstitialRequest();
    if (interstitial) {
      imp.setInstl(interstitial);
    }

    if (dcSlot.hasAdBlockKey()) {
      imp.setTagid(String.valueOf(dcSlot.getAdBlockKey()));
    }

    if (dcSlot.getNativeAdTemplateCount() != 0) {
      Native.Builder nativ = nativeMapper.mapNativeRequest(dcSlot);
      if (nativ != null) {
        imp.setNative(nativ);
      }
    } else if (dcRequest.hasVideo()) {
      Video.Builder video = mapVideo(dcSlot, dcRequest.getVideo(), interstitial);
      if (video != null) {
        imp.setVideo(video);
      }
    } else {
      Banner.Builder banner = mapBanner(dcSlot);
      if (banner != null) {
        imp.setBanner(banner);
      }
    }

    for (ExtMapper extMapper : extMappers) {
      extMapper.toOpenRtbImp(dcSlot, imp);
    }

    return imp.hasVideo() || imp.hasBanner() || imp.hasNative() ? imp : null;
  }

  @Nullable protected Pmp.Builder mapPmp(NetworkBid.BidRequest.AdSlot.MatchingAdData dcAdData) {
    if (dcAdData.getDirectDealCount() == 0) {
      return null;
    }

    Pmp.Builder pmp = Pmp.newBuilder();
    for (NetworkBid.BidRequest.AdSlot.MatchingAdData.DirectDeal dcDeal
        : dcAdData.getDirectDealList()) {
      Deal.Builder deal = mapDeal(dcDeal);
      if (deal != null) {
        pmp.addDeals(deal);
      }
    }

    for (ExtMapper extMapper : extMappers) {
      extMapper.toOpenRtbPmp(dcAdData, pmp);
    }

    return pmp;
  }

  @Nullable protected Deal.Builder mapDeal(
      NetworkBid.BidRequest.AdSlot.MatchingAdData.DirectDeal dcDeal) {
    if (!dcDeal.hasDirectDealId()) {
      return null;
    }

    Deal.Builder deal = Deal.newBuilder()
        .setId(String.valueOf(dcDeal.getDirectDealId()));
    if (dcDeal.hasFixedCpmMicros()) {
      deal.setBidfloor(dcDeal.getFixedCpmMicros() / ((double) MICROS_PER_CURRENCY_UNIT));
    }
    if (dcDeal.hasDealType()) {
      AuctionType at = DealTypeMapper.toOpenRtb(dcDeal.getDealType());
      if (at != null) {
        deal.setAt(at);
      }
    }
    return deal;
  }

  protected void mapBannerSize(Banner.Builder banner, List<Integer> widths, List<Integer> heights) {
    if (widths.isEmpty()) {
      return;
    }

    banner.setW(widths.get(0));
    banner.setH(heights.get(0));

    if (widths.size() == 1) {
      return;
    }

    int wMin = Integer.MAX_VALUE, wMax = 0, hMin = Integer.MAX_VALUE, hMax = 0;
    for (int sizeIndex = 0; sizeIndex < widths.size(); ++sizeIndex) {
      int w = widths.get(sizeIndex);
      wMin = min(wMin, w);
      wMax = max(wMax, w);
      int h = heights.get(sizeIndex);
      hMin = min(hMin, h);
      hMax = max(hMax, h);
    }

    if (wMin != wMax || hMin != hMax) {
      banner.setWmin(wMin);
      banner.setWmax(wMax);
      banner.setHmin(hMin);
      banner.setHmax(hMax);
    }
  }

  protected Video.Builder mapVideo(
      NetworkBid.BidRequest.AdSlot dcSlot, NetworkBid.BidRequest.Video dcVideo,
      boolean interstitial) {
    Video.Builder video = Video.newBuilder()
        .addProtocols(Protocol.VAST_2_0)
        .addProtocols(Protocol.VAST_3_0)
        .addProtocols(Protocol.VAST_2_0_WRAPPER)
        .addProtocols(Protocol.VAST_3_0_WRAPPER)
        .addAllBattr(CreativeAttributeMapper.toOpenRtb(dcSlot.getExcludedAttributeList(), null));

    boolean vpaid = !dcSlot.getExcludedAttributeList().contains(30 /* Vpaid Flash */);
    if (vpaid) {
      video.addApi(APIFramework.VPAID_1);
      video.addApi(APIFramework.VPAID_2);
    }
    if (!dcSlot.getExcludedAttributeList().contains(32 /* MraidType: MRAID */)) {
      video.addApi(APIFramework.MRAID_1);
      video.addApi(APIFramework.MRAID_2);
    }

    if (dcVideo.hasMinAdDuration()) {
      video.setMinduration(dcVideo.getMinAdDuration() / 1000);
    }
    if (dcVideo.hasMaxAdDuration()) {
      video.setMaxduration(dcVideo.getMaxAdDuration() / 1000);
    }

    if (dcVideo.getAllowedVideoFormatsCount() != 0) {
      video.addAllMimes(VideoMimeMapper.toOpenRtb(
          dcVideo.getAllowedVideoFormatsList(), null));
    }

    if (dcVideo.hasPlaybackMethod()) {
      PlaybackMethod playbackMethod = PlaybackMethodMapper.toOpenRtb(dcVideo.getPlaybackMethod());
      if (playbackMethod != null) {
        video.addPlaybackmethod(playbackMethod);
      }
    }

    if (dcSlot.hasSlotVisibility()) {
      AdPosition pos = AdPositionMapper.toOpenRtb(dcSlot.getSlotVisibility());
      if (pos != null) {
        video.setPos(pos);
      }
    }

    if (dcVideo.hasVideoadStartDelay()) {
      video.setStartdelay(VideoStartDelayMapper.toOpenRtb(dcVideo.getVideoadStartDelay()));
    }

    if (dcSlot.getWidthCount() != 0) {
      video.setW(dcSlot.getWidth(0));
      video.setH(dcSlot.getHeight(0));
    }

    if (dcVideo.getCompanionSlotCount() != 0) {
      EnumSet<CompanionType> companionTypes = EnumSet.noneOf(CompanionType.class);

      for (NetworkBid.BidRequest.Video.CompanionSlot dcCompSlot
          : dcVideo.getCompanionSlotList()) {
        Banner.Builder companion = Banner.newBuilder();
        mapBannerSize(companion, dcCompSlot.getWidthList(), dcCompSlot.getHeightList());

        if (dcCompSlot.getCreativeFormatCount() != 0) {
          companion.addAllMimes(
              BannerMimeMapper.toOpenRtb(dcCompSlot.getCreativeFormatList(), vpaid, null));
          CompanionTypeMapper.toOpenRtb(dcCompSlot.getCreativeFormatList(), companionTypes);
        }

        video.addCompanionad(companion);
      }

      video.addAllCompaniontype(companionTypes);
    }

    for (ExtMapper extMapper : extMappers) {
      extMapper.toOpenRtbVideo(dcVideo, video);
    }

    return video;
  }

  protected Banner.Builder mapBanner(NetworkBid.BidRequest.AdSlot dcSlot) {
    Banner.Builder banner = Banner.newBuilder()
        .setId(String.valueOf(dcSlot.getId()))
        .addAllBattr(CreativeAttributeMapper.toOpenRtb(dcSlot.getExcludedAttributeList(), null));

    mapBannerSize(banner, dcSlot.getWidthList(), dcSlot.getHeightList());

    if (dcSlot.hasSlotVisibility()) {
      AdPosition pos = AdPositionMapper.toOpenRtb(dcSlot.getSlotVisibility());
      if (pos != null) {
        banner.setPos(pos);
      }
    }

    if (!dcSlot.getExcludedAttributeList().contains(32 /* MraidType: Mraid 1.0 */)) {
      banner.addApi(APIFramework.MRAID_1);
      banner.addApi(APIFramework.MRAID_2);
    }

    banner.addAllExpdir(ExpandableDirectionMapper.toOpenRtb(dcSlot.getExcludedAttributeList()));

    if (dcSlot.hasIframingState()) {
      Boolean topframe = IFramingStateMapper.toOpenRtb(dcSlot.getIframingState());
      if (topframe != null) {
        banner.setTopframe(topframe);
      }
    }

    for (ExtMapper extMapper : extMappers) {
      extMapper.toOpenRtbBanner(dcSlot, banner);
    }

    return banner;
  }

  @Nullable protected User.Builder mapUser(NetworkBid.BidRequest dcRequest) {
    if (!dcRequest.hasGoogleUserId()) {
      return null;
    }

    User.Builder user = User.newBuilder().setId(dcRequest.getGoogleUserId());

    if (dcRequest.hasHostedMatchData()) {
      user.setCustomdata(MapperUtil.toBase64(dcRequest.getHostedMatchData()));
    }

    if (dcRequest.hasUserDemographic()) {
      NetworkBid.BidRequest.UserDemographic dcUser = dcRequest.getUserDemographic();

      if (dcUser.hasGender()) {
        Gender gender = GenderMapper.toOpenRtb(dcUser.getGender());
        if (gender != null) {
          user.setGender(gender.code());
        }
      }
      if (dcUser.hasAgeLow() || dcUser.hasAgeHigh()) {
        // OpenRTB only supports a single age, not a range. We have to be pessimistic;
        // if the age range is [X...Y], assume X to be the age (the youngest possible).
        // We don't want to get in trouble e.g. if the age is [14..30], using the high
        // or even average would classify as adult an user that's possibly minor.
        // If the publisher is known to slot users into certain standard ranges, you
        // can translate this back, i.e. age 25 could mean the [25-34] range.
        int age = dcUser.hasAgeHigh() ? dcUser.getAgeHigh() : dcUser.getAgeLow();
        Calendar today = Calendar.getInstance();
        user.setYob(today.get(Calendar.YEAR) - age);
      }
    }

    if (dcRequest.getDetectedVerticalCount() != 0) {
      Data.Builder data = OpenRtb.BidRequest.Data.newBuilder()
          .setId("DetectedVerticals")
          .setName("DoubleClick");
      for (NetworkBid.BidRequest.Vertical dcVertical : dcRequest.getDetectedVerticalList()) {
        Segment.Builder segment = Segment.newBuilder()
            .setId(String.valueOf(dcVertical.getId()))
            .setValue(String.valueOf(dcVertical.getWeight()));
        String name = metadata.publisherVerticals().get(dcVertical.getId());
        if (name != null) {
          segment.setName(name);
        }
        data.addSegment(segment);
      }
      user.addData(data);
    }

    for (ExtMapper extMapper : extMappers) {
      extMapper.toOpenRtbUser(dcRequest, user);
    }

    return user;
  }

  @Override public NetworkBid.BidResponse.Builder toExchangeBidResponse(
    OpenRtb.BidRequest request, OpenRtb.BidResponse response) {
    checkNotNull(request);
    NetworkBid.BidResponse.Builder dcResponse = NetworkBid.BidResponse.newBuilder();

    if (response.hasBidid()) {
      dcResponse.setDebugString(response.getBidid());
    }

    for (SeatBid seatBid : response.getSeatbidList()) {
      for (Bid bid : seatBid.getBidList()) {
        dcResponse.addAd(mapResponseAd(request, bid));
      }
    }

    for (ExtMapper extMapper : extMappers) {
      extMapper.toDoubleClickBidResponse(response, dcResponse);
    }

    return dcResponse;
  }

  protected NetworkBid.BidResponse.Ad.Builder mapResponseAd(
      OpenRtb.BidRequest request, Bid bid) {
    NetworkBid.BidResponse.Ad.Builder dcAd;

    if (bid.hasExtension(DcExt.ad)) {
      dcAd = bid.getExtension(DcExt.ad).toBuilder();
    } else {
      dcAd = NetworkBid.BidResponse.Ad.newBuilder();
    }

    if (!bid.hasCrid()) {
      missingCrid.inc();
      throw new MapperException("Bid.crid is not set, mandatory for DoubleClick");
    }
    dcAd.setBuyerCreativeId(bid.getCrid());

    if (bid.hasNurl()) {
      unsupportedNurl.inc();
      throw new MapperException("Bid.nurl is set, unsupported for DoubleClick");
    }

    Imp matchingImp = OpenRtbUtils.impWithId(request, bid.getImpid());
    if (matchingImp == null) {
      invalidImp.inc();
      throw new MapperException(
          "Impresson.id doesn't match any request impression: %s", bid.getImpid());
    }

    if (matchingImp.hasNative()) {
      nativeMapper.mapNativeResponse(dcAd, bid, matchingImp);
    } else if (matchingImp.hasVideo() && bid.getAdm().regionMatches(true, 0, "https://", 0, 8)) {
      dcAd.setVideoUrl(bid.getAdm());
      mapAdSize(bid, dcAd, matchingImp);
    } else if (matchingImp.hasBanner() || matchingImp.hasVideo()) {
      dcAd.setHtmlSnippet(bid.getAdm());
      mapAdSize(bid, dcAd, matchingImp);
    } else {
      noVideoOrBanner.inc();
      throw new MapperException("Imp has neither of Video or Banner");
    }

    NetworkBid.BidResponse.Ad.AdSlot.Builder dcSlot = dcAd.addAdslotBuilder()
        .setId(Integer.parseInt(bid.getImpid()))
        .setMaxCpmMicros((long) (bid.getPrice() * MICROS_PER_CURRENCY_UNIT));
    if (matchingImp.getExtension(DcExt.adSlot).getMatchingAdDataCount() > 1) {
      if (bid.hasCid()) {
        dcSlot.setBillingId(Long.parseLong(bid.getCid()));
      } else {
        noCid.inc();
        if (logger.isDebugEnabled()) {
          logger.debug("Missing cid in a Bid created for multi-campaign Imp: {}",
              TextFormat.shortDebugString(bid));
        }
      }
    }
    if (bid.hasDealid()) {
      dcSlot.setDealId(Long.parseLong(bid.getDealid()));
    }

    dcAd.addAllAttribute(CreativeAttributeMapper.toDoubleClick(bid.getAttrList(), null));

    Set<Integer> cats = new LinkedHashSet<>();
    for (String catName : bid.getCatList()) {
      try {
        ContentCategory cat = OpenRtbUtils.categoryFromName(catName);
        cats.addAll(AdCategoryMapper.toDoubleClick(cat));
      } catch (IllegalArgumentException e) {
        invalidContentCategory.inc();
      }
    }
    dcAd.addAllCategory(cats);

    for (String adomain : bid.getAdomainList()) {
      if (adomain.contains("://")) {
        dcAd.addClickThroughUrl(adomain);
      } else {
        try {
          URL url = new URL("http", adomain, "");
          dcAd.addClickThroughUrl(url.toExternalForm());
        } catch (MalformedURLException e) {
        }
      }
    }

    for (ExtMapper extMapper : extMappers) {
      extMapper.toDoubleClickAd(request, bid, dcAd);
    }

    return dcAd;
  }

  protected void mapAdSize(Bid bid, NetworkBid.BidResponse.Ad.Builder dcAd, Imp matchingImp) {
    boolean multisize = matchingImp.getExtension(DcExt.adSlot).getWidthCount() > 1;

    if (multisize || matchingImp.getInstl()) {
      if (bid.hasW() && bid.hasH()) {
        dcAd.setWidth(bid.getW());
        dcAd.setHeight(bid.getH());
      } else {
        missingSize.inc();
        if (logger.isDebugEnabled()) {
          logger.debug("Missing size in a Bid created for {} impression: {}",
              multisize ? "multisize" : "interstitial", TextFormat.shortDebugString(bid));
        }
      }
    }
  }

  protected static String mapLanguage(String langCulture) {
    int iSep = langCulture.indexOf('_');
    return iSep == -1 ? langCulture : langCulture.substring(0, iSep);
  }

  /**
   * Not implemented yet!
   */
  @Override public NetworkBid.BidRequest.Builder toExchangeBidRequest(
      OpenRtb.BidRequest request) {
    throw new UnsupportedOperationException();
  }

  /**
   * Not implemented yet!
   */
  @Override public OpenRtb.BidResponse.Builder toOpenRtbBidResponse(
      NetworkBid.BidRequest request, NetworkBid.BidResponse response) {
    throw new UnsupportedOperationException();
  }
}
