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

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.doubleclick.DcExt;
import com.google.doubleclick.Doubleclick;
import com.google.doubleclick.Doubleclick.BidRequest.Hyperlocal;
import com.google.doubleclick.Doubleclick.BidRequest.HyperlocalSet;
import com.google.doubleclick.Doubleclick.BidRequest.UserDemographic;
import com.google.doubleclick.crypto.DoubleClickCrypto;
import com.google.doubleclick.crypto.DoubleClickCryptoException;
import com.google.doubleclick.util.DoubleClickMetadata;
import com.google.openrtb.OpenRtb;
import com.google.openrtb.OpenRtb.BidRequest.App;
import com.google.openrtb.OpenRtb.BidRequest.Content;
import com.google.openrtb.OpenRtb.BidRequest.Device;
import com.google.openrtb.OpenRtb.BidRequest.Geo;
import com.google.openrtb.OpenRtb.BidRequest.Impression;
import com.google.openrtb.OpenRtb.BidRequest.Impression.ApiFramework;
import com.google.openrtb.OpenRtb.BidRequest.Impression.Banner;
import com.google.openrtb.OpenRtb.BidRequest.Impression.PMP;
import com.google.openrtb.OpenRtb.BidRequest.Impression.PMP.DirectDeal;
import com.google.openrtb.OpenRtb.BidRequest.Impression.Video;
import com.google.openrtb.OpenRtb.BidRequest.Impression.Video.CompanionType;
import com.google.openrtb.OpenRtb.BidRequest.Impression.Video.Linearity;
import com.google.openrtb.OpenRtb.BidRequest.Impression.Video.Protocol;
import com.google.openrtb.OpenRtb.BidRequest.Publisher;
import com.google.openrtb.OpenRtb.BidRequest.Regulations;
import com.google.openrtb.OpenRtb.BidRequest.Site;
import com.google.openrtb.OpenRtb.BidRequest.User;
import com.google.openrtb.OpenRtb.BidResponse.SeatBid;
import com.google.openrtb.OpenRtb.BidResponse.SeatBid.Bid;
import com.google.openrtb.OpenRtb.ContentCategory;
import com.google.openrtb.OpenRtb.Flag;
import com.google.openrtb.mapper.OpenRtbMapper;
import com.google.openrtb.util.OpenRtbUtils;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import com.codahale.metrics.Counter;
import com.codahale.metrics.MetricRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.util.Calendar;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import javax.annotation.Nullable;
import javax.inject.Inject;

/**
 * Mapping between the DoubleClick and OpenRTB models.
 */
public class DoubleClickOpenRtbMapper
    implements OpenRtbMapper<Doubleclick.BidRequest, Doubleclick.BidResponse.Builder> {
  private static final Logger logger = LoggerFactory.getLogger(DoubleClickOpenRtbMapper.class);
  private static final String YOUTUBE_AFV_USER_ID = "afv_user_id_";
  private static final Pattern SEMITRANSPARENT_CHANNEL =
      Pattern.compile("pack-(brand|semi|anon)-([^\\-]+)::(.+)");
  private static final Joiner versionJoiner = Joiner.on(".").skipNulls();

  private final DoubleClickMetadata metadata;
  private final DoubleClickCrypto.Hyperlocal hyperlocalCrypto;
  private final ImmutableList<ExtMapper> extMappers;
  private final Counter missingCrid = new Counter();
  private final Counter invalidImp = new Counter();
  private final Counter missingSize = new Counter();
  private final Counter noVideoOrBanner = new Counter();
  private final Counter coppaTreatment = new Counter();
  private final Counter noImp = new Counter();
  private final Counter invalidGeoId = new Counter();
  private final Counter invalidHyperlocal = new Counter();
  private final Counter noCid = new Counter();

  @Inject
  public DoubleClickOpenRtbMapper(
      MetricRegistry metricRegistry,
      DoubleClickMetadata metadata,
      @Nullable DoubleClickCrypto.Hyperlocal hyperlocalCrypto,
      List<ExtMapper> extMappers) {
    this.metadata = metadata;
    this.hyperlocalCrypto = hyperlocalCrypto;
    this.extMappers = ImmutableList.copyOf(extMappers);
    metricRegistry.register(MetricRegistry.name(getClass(), "missing-crid"), missingCrid);
    metricRegistry.register(MetricRegistry.name(getClass(), "invalid-imp"), invalidImp);
    metricRegistry.register(MetricRegistry.name(getClass(), "missing-size"), missingSize);
    metricRegistry.register(MetricRegistry.name(getClass(), "no-video-or-banner"), noVideoOrBanner);
    metricRegistry.register(MetricRegistry.name(getClass(), "coppa-treatment"), coppaTreatment);
    metricRegistry.register(MetricRegistry.name(getClass(), "no-imp"), noImp);
    metricRegistry.register(MetricRegistry.name(getClass(), "invalid-geoid"), invalidGeoId);
    metricRegistry.register(MetricRegistry.name(getClass(), "invalid-hyperlocal"), invalidHyperlocal);
    metricRegistry.register(MetricRegistry.name(getClass(), "no-cid"), noCid);
  }

  @Override
  public Doubleclick.BidResponse.Builder toNative(
    OpenRtb.BidRequest request, OpenRtb.BidResponse response) {
    checkNotNull(request);
    Doubleclick.BidResponse.Builder dcResponse = Doubleclick.BidResponse.newBuilder();

    if (response.hasBidid()) {
      dcResponse.setDebugString(response.getBidid());
    }

    for (SeatBid seatBid : response.getSeatbidList()) {
      for (Bid bid : seatBid.getBidList()) {
        dcResponse.addAd(buildResponseAd(request, response, bid));
      }
    }

    return dcResponse;
  }

  protected Doubleclick.BidResponse.Ad.Builder buildResponseAd(
      OpenRtb.BidRequest request, OpenRtb.BidResponse response, final Bid bid) {
    Doubleclick.BidResponse.Ad.Builder dcAd;

    if (bid.hasExtension(DcExt.ad)) {
      dcAd = bid.getExtension(DcExt.ad).toBuilder();
    } else {
      dcAd = Doubleclick.BidResponse.Ad.newBuilder();
    }

    if (!bid.hasCrid()) {
      missingCrid.inc();
      throw new MapperException(
          "Bid.crid is not set, mandatory for DoubleClick");
    }

    dcAd.setBuyerCreativeId(bid.getCrid());

    Impression matchingImp = OpenRtbUtils.impWithId(request, bid.getImpid());
    if (matchingImp == null) {
      invalidImp.inc();
      throw new MapperException(
          "Impresson.id doesn't match any request impression: %s", bid.getImpid());
    }

    Doubleclick.BidRequest.AdSlot dcImpSlot = matchingImp.getExtension(DcExt.adSlot);
    boolean multisize = dcImpSlot.getWidthCount() > 1;

    if (matchingImp.hasVideo()) {
      dcAd.setVideoUrl(bid.getAdm());

      if (multisize) {
        if (bid.hasW() && bid.hasH()) {
          dcAd.setWidth(bid.getW());
          dcAd.setHeight(bid.getH());
        } else {
          missingSize.inc();
          logger.debug("Missing size in a Bid created for multisize Video impression: {}", bid);
        }
      }
    } else if (matchingImp.hasBanner()) {
      if (dcAd.getTemplateParameterCount() == 0) {
        dcAd.setHtmlSnippet(bid.getAdm());
      } else {
        if (!dcAd.hasSnippetTemplate()) {
          if (bid.hasAdm()) {
            logger.debug("Ad fragment has snippetTemplate, ignoring bid's adm");
          }
          dcAd.setSnippetTemplate(bid.getAdm());
        }
      }

      if (multisize) {
        if (bid.hasW() && bid.hasH()) {
          dcAd.setWidth(bid.getW());
          dcAd.setHeight(bid.getH());
        } else {
          missingSize.inc();
          logger.debug("Missing size in a Bid created for multisize Banner impression: {}", bid);
        }
      }
    } else {
      noVideoOrBanner.inc();
      throw new MapperException("Impression has neither of Video or Banner");
    }

    Doubleclick.BidResponse.Ad.AdSlot.Builder dcSlot = dcAd.addAdslotBuilder()
        .setId(Integer.parseInt(bid.getImpid()))
        .setMaxCpmMicros((long) bid.getPrice());
    if (dcImpSlot.getMatchingAdDataCount() > 1) {
      if (bid.hasCid()) {
        dcSlot.setAdgroupId(Long.parseLong(bid.getCid()));
      } else {
        noCid.inc();
        logger.debug("Missing cid in a Bid created for multi-campaign Impression: {}", bid);
      }
    }
    if (bid.hasDealid()) {
      dcSlot.setDealId(Long.parseLong(bid.getDealid()));
    }

    dcAd.addAllAttribute(CreativeAttributeMapper.toDoubleClick(bid.getAttrList()));

    for (ExtMapper extMapper : extMappers) {
      extMapper.toNative(request, response, bid, dcAd);
    }

    return dcAd;
  }

  @Override
  public OpenRtb.BidRequest toOpenRtb(Doubleclick.BidRequest dcRequest) {
    OpenRtb.BidRequest.Builder request = OpenRtb.BidRequest.newBuilder()
        .setId(MapperUtil.toHexString(dcRequest.getId()));

    if (dcRequest.getIsPing()) {
      return request.build();
    }

    boolean coppa = false;
    for (Doubleclick.BidRequest.UserDataTreatment dcUDT : dcRequest.getUserDataTreatmentList()) {
      if (dcUDT == Doubleclick.BidRequest.UserDataTreatment.TAG_FOR_CHILD_DIRECTED_TREATMENT) {
        coppa = true;
        break;
      }
    }
    if (coppa) {
      coppaTreatment.inc();
      request.setRegs(Regulations.newBuilder().setCoppa(Flag.YES));
    }

    request.setDevice(buildDevice(dcRequest, coppa));

    if (dcRequest.hasMobile()) {
      request.setApp(buildApp(dcRequest));
    } else {
      request.setSite(buildSite(dcRequest));
    }

    addBcat(dcRequest, request);
    buildImps(dcRequest, request);

    User.Builder user = buildUser(dcRequest, coppa);
    if (user != null) {
      request.setUser(user);
    }

    for (ExtMapper extMapper : extMappers) {
      extMapper.toOpenRtb(dcRequest, request);
    }

    return request
        .setTmax(100)
        .build();
  }

  protected User.Builder buildUser(Doubleclick.BidRequest dcRequest, boolean coppa) {
    if ((!coppa && !dcRequest.hasGoogleUserId())
        || (coppa && !dcRequest.hasConstrainedUsageGoogleUserId())) {
      return null;
    }

    User.Builder user = User.newBuilder().setId(coppa
        ? dcRequest.getConstrainedUsageGoogleUserId()
        : dcRequest.getGoogleUserId());

    if ((coppa && dcRequest.hasConstrainedUsageHostedMatchData())
        || (!coppa && dcRequest.hasHostedMatchData())) {
      try {
        ByteString dcHMD = coppa
            ? dcRequest.getConstrainedUsageHostedMatchData()
            : dcRequest.getHostedMatchData();
        user.setCustomdata(MapperUtil.decodeUri(dcHMD.toString("US-ASCII")));
      } catch (UnsupportedEncodingException e) {}
    }

    if (dcRequest.hasUserDemographic()) {
      UserDemographic dcUser = dcRequest.getUserDemographic();

      if (dcUser.hasGender()) {
        user.setGender(GenderMapper.toOpenRtb(dcUser.getGender()));
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

    return user;
  }

  protected void buildImps(Doubleclick.BidRequest dcRequest, OpenRtb.BidRequest.Builder request) {
    for (Doubleclick.BidRequest.AdSlot dcSlot : dcRequest.getAdslotList()) {
      Impression.Builder imp = request.addImpBuilder()
          .setId(String.valueOf(dcSlot.getId()));

      Float bidFloor = null;

      for (Doubleclick.BidRequest.AdSlot.MatchingAdData dcAdData :
          dcSlot.getMatchingAdDataList()) {

        if (dcAdData.hasMinimumCpmMicros()) {
          bidFloor = (bidFloor == null)
              ? dcAdData.getMinimumCpmMicros()
              : min(bidFloor, dcAdData.getMinimumCpmMicros());
        }

        PMP.Builder pmp = buildPmp(dcAdData);
        if (pmp != null) {
          imp.setPmp(pmp);
        }
      }

      if (bidFloor != null) {
        imp.setBidfloor(bidFloor);
      }

      if (dcRequest.getMobile().hasIsInterstitialRequest()) {
        imp.setInstl(dcRequest.getMobile().getIsInterstitialRequest() ? Flag.YES : Flag.NO);
      }

      if (dcSlot.hasAdBlockKey()) {
        imp.setTagid(String.valueOf(dcSlot.getAdBlockKey()));
      }

      if (dcRequest.hasVideo()) {
        imp.setVideo(buildVideo(dcSlot, dcRequest.getVideo()));
      } else {
        imp.setBanner(buildBanner(dcSlot));
      }

      for (ExtMapper extMapper : extMappers) {
        extMapper.toOpenRtb(dcSlot, imp);
      }
    }

    if (request.getImpCount() == 0) {
      noImp.inc();
      logger.warn("Request has no impressions");
    }
  }

  protected Banner.Builder buildBanner(Doubleclick.BidRequest.AdSlot dcSlot) {
    Banner.Builder banner = Banner.newBuilder()
        .setId(String.valueOf(dcSlot.getId()))
        .addAllBattr(CreativeAttributeMapper.toOpenRtb(dcSlot.getExcludedAttributeList()));

    setSize(banner, dcSlot.getWidthList(), dcSlot.getHeightList());

    if (dcSlot.hasSlotVisibility()) {
      banner.setPos(AdPositionMapper.toOpenRtb(dcSlot.getSlotVisibility()));
    }

    if (!dcSlot.getExcludedAttributeList().contains(32 /* MraidType: Mraid 1.0 */)) {
      banner.addApi(ApiFramework.MRAID);
    }

    for (ExtMapper extMapper : extMappers) {
      extMapper.toOpenRtb(dcSlot, banner);
    }

    return banner;
  }

  protected PMP.Builder buildPmp(Doubleclick.BidRequest.AdSlot.MatchingAdData dcAdData) {
    if (dcAdData.getDirectDealCount() == 0) {
      return null;
    }

    PMP.Builder pmp = PMP.newBuilder();
    for (Doubleclick.BidRequest.AdSlot.MatchingAdData.DirectDeal dcDeal
        : dcAdData.getDirectDealList()) {
      if (dcDeal.hasDirectDealId()) {
        DirectDeal.Builder deal = DirectDeal.newBuilder()
            .setId(String.valueOf(dcDeal.getDirectDealId()));
        if (dcDeal.hasFixedCpmMicros()) {
          deal.setBidfloor(dcDeal.getFixedCpmMicros());
        }
        pmp.addDeals(deal);
      }
    }

    for (ExtMapper extMapper : extMappers) {
      extMapper.toOpenRtb(dcAdData, pmp);
    }

    return pmp;
  }

  protected Video.Builder buildVideo(
      Doubleclick.BidRequest.AdSlot dcSlot, Doubleclick.BidRequest.Video dcVideo) {
    Video.Builder video = Video.newBuilder()
        .setLinearity(Linearity.LINEAR)
        .setProtocol(Protocol.VAST_3_0)
        .setMinduration(dcVideo.getMinAdDuration())
        .setMaxduration(dcVideo.getMaxAdDuration())
        .addAllBattr(CreativeAttributeMapper.toOpenRtb(dcSlot.getExcludedAttributeList()));

    if (dcSlot.hasSlotVisibility()) {
      video.setPos(AdPositionMapper.toOpenRtb(dcSlot.getSlotVisibility()));
    }
    if (dcVideo.hasVideoadStartDelay()) {
      video.setStartdelay(dcVideo.getVideoadStartDelay());
    }
    if (!dcSlot.getExcludedAttributeList().contains(32 /* MraidType: Mraid 1.0 */)) {
      video.addApi(ApiFramework.MRAID);
    }

    if (dcSlot.getWidthCount() == 1) {
      video.setW(dcSlot.getWidth(0));
      video.setH(dcSlot.getHeight(0));
    } else if (dcSlot.getWidthCount() != 0) {
      logger.debug("Video request is multisize; no size mapped");
    }

    if (dcVideo.getCompanionSlotCount() != 0) {
      Set<CompanionType> companionTypes = new LinkedHashSet<>();

      for (Doubleclick.BidRequest.Video.CompanionSlot dcCompSlot
          : dcVideo.getCompanionSlotList()) {
        Banner.Builder companion = Banner.newBuilder();
        setSize(companion, dcCompSlot.getWidthList(), dcCompSlot.getHeightList());
        video.addCompanionad(companion);

        for (Doubleclick.BidRequest.Video.CompanionSlot.CreativeFormat dcCompSlotFmt
            : dcCompSlot.getCreativeFormatList()) {
          companionTypes.add(CompanionTypeMapper.toOpenRtb(dcCompSlotFmt));
        }
      }

      if (!companionTypes.isEmpty()) {
        video.addAllCompaniontype(companionTypes);
      }
    }

    for (ExtMapper extMapper : extMappers) {
      extMapper.toOpenRtb(dcVideo, video);
    }

    return video;
  }

  protected void setSize(Banner.Builder banner, List<Integer> widths, List<Integer> heights) {
    if (!widths.isEmpty()) {
      int wMin = Integer.MAX_VALUE, wMax = 0, hMin = Integer.MAX_VALUE, hMax = 0;
      for (int sizeIndex = 0; sizeIndex < widths.size(); ++sizeIndex) {
        int w = widths.get(sizeIndex);
        wMin = min(wMin, w);
        wMax = max(wMax, w);
        int h = heights.get(sizeIndex);
        hMin = min(hMin, h);
        hMax = max(hMax, h);
      }

      if (wMin == wMax) {
        banner.setW(wMin);
      } else {
        banner.setWmin(wMin);
        banner.setWmax(wMax);
      }
      if (hMin == hMax) {
        banner.setH(hMin);
      } else {
        banner.setHmin(hMin);
        banner.setHmax(hMax);
      }
    }
  }

  protected Device.Builder buildDevice(Doubleclick.BidRequest dcRequest, boolean coppa) {
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

    Geo.Builder geo = buildGeo(dcRequest);
    if (geo != null) {
      device.setGeo(geo);
    }

    if (dcRequest.hasMobile()) {
      Doubleclick.BidRequest.Mobile dcMobile = dcRequest.getMobile();
      if (dcMobile.hasCarrierId()) {
        device.setCarrier(String.valueOf(dcMobile.getCarrierId()));
      }
      if ((coppa && dcMobile.hasConstrainedUsageEncryptedHashedIdfa())
          || (!coppa && dcMobile.hasEncryptedHashedIdfa())) {
        device.setDpidmd5(MapperUtil.toHexString(coppa
            ? dcMobile.getConstrainedUsageEncryptedHashedIdfa()
            : dcMobile.getEncryptedHashedIdfa()));
      }
      if (dcMobile.hasModel()) {
        device.setModel(dcMobile.getModel());
      }
      if (dcMobile.hasPlatform()) {
        device.setOs(dcMobile.getPlatform());
      }
      if (dcMobile.hasOsVersion()) {
        Doubleclick.BidRequest.Mobile.DeviceOsVersion dcVer = dcMobile.getOsVersion();
        device.setOsv(versionJoiner.join(
            dcVer.hasOsVersionMajor() ? dcVer.getOsVersionMajor() : null,
            dcVer.hasOsVersionMinor() ? dcVer.getOsVersionMinor() : null,
            dcVer.hasOsVersionMicro() ? dcVer.getOsVersionMicro() : null));
      }
      if (dcMobile.hasMobileDeviceType()) {
        device.setDevicetype(DeviceTypeMapper.toOpenRtb(dcMobile.getMobileDeviceType()));
      }
    }

    for (ExtMapper extMapper : extMappers) {
      extMapper.toOpenRtb(dcRequest, device);
    }

    return device;
  }

  protected Geo.Builder buildGeo(Doubleclick.BidRequest dcRequest) {
    if (!dcRequest.hasGeoCriteriaId() && !dcRequest.hasEncryptedHyperlocalSet()
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

      DoubleClickMetadata.GeoTarget geoTarget = metadata.getGeoTarget(geoCriteriaId);

      if (geoTarget == null) {
        invalidGeoId.inc();
        if (logger.isDebugEnabled()) {
          logger.debug("Received unknown geo_criteria_id: {}", geoCriteriaId);
        }
      } else {
        mapGeo(geoTarget, geo);
      }
    }

    if (dcRequest.hasEncryptedHyperlocalSet() && hyperlocalCrypto != null) {
      try {
        HyperlocalSet hyperlocalSet = HyperlocalSet.parseFrom(hyperlocalCrypto.decryptHyperlocal(
            dcRequest.getEncryptedHyperlocalSet().toByteArray()));
        geo.setExtension(DcExt.hyperLocal, hyperlocalSet);
        if (hyperlocalSet.hasCenterPoint()) {
          Hyperlocal.Point center = hyperlocalSet.getCenterPoint();
          if (center.hasLatitude() && center.hasLongitude()) {
            geo.setLat(center.getLatitude());
            geo.setLon(center.getLongitude());
          }
        }
      } catch (InvalidProtocolBufferException | DoubleClickCryptoException e) {
        invalidHyperlocal.inc();
        logger.warn("Invalid encrypted_hyperlocal_set: {}", e.toString());
      }
    }

    return geo;
  }

  protected void mapGeo(DoubleClickMetadata.GeoTarget geoTarget, Geo.Builder geo) {
    DoubleClickMetadata.GeoTarget city = null;
    DoubleClickMetadata.GeoTarget metro = null;
    DoubleClickMetadata.GeoTarget region = null;
    DoubleClickMetadata.GeoTarget country = null;

    for (DoubleClickMetadata.GeoTarget currTarget = geoTarget; currTarget != null;
        currTarget = currTarget.getParent()) {
      switch (currTarget.getTargetType()) {
        case CITY:
        case PREFECTURE:
          city = currTarget;
          break;

        case COUNTRY:
          country = currTarget;
          break;

        case DMA_REGION:
          metro = currTarget;
          break;

        case STATE:
        case REGION:
          region = currTarget;
          break;

        default:
      }
    }

    if (city != null) {
      geo.setCity(city.getName());
      geo.setCountry(city.getCountryCode());
    }
    if (metro != null) {
      geo.setMetro(metro.getName());
    }
    if (region != null) {
      geo.setRegion(region.getName());
    }
    if (country != null) {
      DoubleClickMetadata.CountryCodes countryCodes =
          metadata.getCountryCodes().get(country.getCountryCode());
      if (countryCodes != null) {
        geo.setCountry(countryCodes.getAlpha3());
      }
    }
  }

  protected Site.Builder buildSite(Doubleclick.BidRequest dcRequest) {
    Site.Builder site = Site.newBuilder();
    site.setContent(buildContent(dcRequest));

    if (dcRequest.hasUrl()) {
      site.setPage(dcRequest.getUrl());
    } else if (dcRequest.hasAnonymousId()) {
      site.setName(dcRequest.getAnonymousId());
    } else {
      logger.debug("Site request is missing both url and anonymousId: {}", dcRequest);
    }

    String channelId = findChannelId(dcRequest);
    if (channelId != null) {
      site.setId(channelId);
    }

    Publisher.Builder pub = buildPublisher(dcRequest);
    if (pub != null) {
      site.setPublisher(pub);
    }

    return site;
  }

  protected App.Builder buildApp(Doubleclick.BidRequest dcRequest) {
    Doubleclick.BidRequest.Mobile dcMobile = dcRequest.getMobile();
    App.Builder app = App.newBuilder();

    Content.Builder content = buildContent(dcRequest);
    if (dcRequest.hasUrl()) {
      content.setUrl(dcRequest.getUrl());
    } else if (dcRequest.hasAnonymousId()) {
      content.setId(dcRequest.getAnonymousId());
    } else {
      logger.debug("App request is missing both url and anonymousId: {}", dcRequest);
    }
    if (dcMobile.hasAppRating()) {
      content.setUserrating(String.valueOf(dcMobile.getAppRating()));
    }
    app.setContent(content);

    if (dcMobile.hasAppId()) {
      app.setBundle(dcMobile.getAppId());
    }
    if (dcMobile.hasAppName()) {
      app.setName(dcMobile.getAppName());
    }

    String channelId = findChannelId(dcRequest);
    if (channelId != null) {
      app.setId(channelId);
    }

    Publisher.Builder pub = buildPublisher(dcRequest);
    if (pub != null) {
      app.setPublisher(pub);
    }

    return app;
  }

  protected Publisher.Builder buildPublisher(Doubleclick.BidRequest dcRequest) {
    if (!dcRequest.hasSellerNetworkId()) {
      return null;
    }

    return Publisher.newBuilder()
        .setId(String.valueOf(dcRequest.getSellerNetworkId()));
  }

  protected String findChannelId(Doubleclick.BidRequest dcRequest) {
    for (Doubleclick.BidRequest.AdSlot dcSlot : dcRequest.getAdslotList()) {
      for (String dcChannel : dcSlot.getTargetableChannelList()) {
        if (dcChannel.startsWith(YOUTUBE_AFV_USER_ID)) {
          return dcChannel.substring(YOUTUBE_AFV_USER_ID.length());
        } else if (SEMITRANSPARENT_CHANNEL.matcher(dcChannel).matches()) {
          return dcChannel;
        }
      }
    }

    return null;
  }

  protected Content.Builder buildContent(Doubleclick.BidRequest dcRequest) {
    Content.Builder content = Content.newBuilder();
    int nLangs = dcRequest.getDetectedLanguageCount();

    if (nLangs == 1) {
      content.setLanguage(getLanguage(dcRequest.getDetectedLanguage(0)));
    } else if (nLangs != 0) {
      StringBuilder sb = new StringBuilder(nLangs * 3 - 1);
      for (String langCulture : dcRequest.getDetectedLanguageList()) {
        if (sb.length() != 0) {
          sb.append(',');
        }
        sb.append(getLanguage(langCulture));
      }
      content.setLanguage(sb.toString());
    }

    return content;
  }

  protected static String getLanguage(String langCulture) {
    int iSep = langCulture.indexOf('_');
    return iSep == -1 ? langCulture : langCulture.substring(0, iSep);
  }

  protected void addBcat(Doubleclick.BidRequest dcRequest, OpenRtb.BidRequest.Builder request) {
    for (Doubleclick.BidRequest.AdSlot dcSlot : dcRequest.getAdslotList()) {
      for (Integer dcCat : dcSlot.getExcludedProductCategoryList()) {
        for (ContentCategory cat : AdCategoryMapper.toOpenRtb(dcCat)) {
          request.addBcat(AdCategoryMapper.getNameMap().get(cat));
        }
      }

      for (Integer dcCat : dcSlot.getExcludedSensitiveCategoryList()) {
        for (ContentCategory cat : AdCategoryMapper.toOpenRtb(dcCat)) {
          request.addBcat(AdCategoryMapper.getNameMap().get(cat));
        }
      }
    }
  }
}
