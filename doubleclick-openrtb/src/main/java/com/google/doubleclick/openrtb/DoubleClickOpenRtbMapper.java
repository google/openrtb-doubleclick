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
import com.google.common.io.BaseEncoding;
import com.google.doubleclick.DcExt;
import com.google.doubleclick.crypto.DoubleClickCrypto;
import com.google.doubleclick.util.CountryCodes;
import com.google.doubleclick.util.DoubleClickMetadata;
import com.google.doubleclick.util.GeoTarget;
import com.google.openrtb.OpenRtb;
import com.google.openrtb.OpenRtb.BidRequest.App;
import com.google.openrtb.OpenRtb.BidRequest.AuctionType;
import com.google.openrtb.OpenRtb.BidRequest.Content;
import com.google.openrtb.OpenRtb.BidRequest.Content.Builder;
import com.google.openrtb.OpenRtb.BidRequest.Data;
import com.google.openrtb.OpenRtb.BidRequest.Data.Segment;
import com.google.openrtb.OpenRtb.BidRequest.Device;
import com.google.openrtb.OpenRtb.BidRequest.Device.DeviceType;
import com.google.openrtb.OpenRtb.BidRequest.Geo;
import com.google.openrtb.OpenRtb.BidRequest.Imp;
import com.google.openrtb.OpenRtb.BidRequest.Imp.APIFramework;
import com.google.openrtb.OpenRtb.BidRequest.Imp.AdPosition;
import com.google.openrtb.OpenRtb.BidRequest.Imp.Banner;
import com.google.openrtb.OpenRtb.BidRequest.Imp.Pmp;
import com.google.openrtb.OpenRtb.BidRequest.Imp.Pmp.Deal;
import com.google.openrtb.OpenRtb.BidRequest.Imp.Video;
import com.google.openrtb.OpenRtb.BidRequest.Imp.Video.VASTCompanionType;
import com.google.openrtb.OpenRtb.BidRequest.Imp.Video.VideoBidResponseProtocol;
import com.google.openrtb.OpenRtb.BidRequest.Publisher;
import com.google.openrtb.OpenRtb.BidRequest.Regs;
import com.google.openrtb.OpenRtb.BidRequest.Site;
import com.google.openrtb.OpenRtb.BidRequest.User;
import com.google.openrtb.OpenRtb.BidResponse.SeatBid;
import com.google.openrtb.OpenRtb.BidResponse.SeatBid.Bid;
import com.google.openrtb.OpenRtb.ContentCategory;
import com.google.openrtb.json.OpenRtbJsonFactory;
import com.google.openrtb.mapper.OpenRtbMapper;
import com.google.openrtb.util.OpenRtbUtils;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.TextFormat;
import com.google.protos.adx.NetworkBid;

import com.codahale.metrics.Counter;
import com.codahale.metrics.MetricRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SignatureException;
import java.util.Calendar;
import java.util.EnumSet;
import java.util.List;
import java.util.regex.Pattern;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Mapping between the DoubleClick and OpenRTB models.
 * <p>
 * This class is threadsafe. Recommended use is as a singleton, but you may also want to create
 * multiple instances if you need to keep track of metrics separately for different uses
 * (for that to make sense, provide a different {@link MetricRegistry} to each instance).
 */
@Singleton
public class DoubleClickOpenRtbMapper implements OpenRtbMapper<
    NetworkBid.BidRequest, NetworkBid.BidResponse,
    NetworkBid.BidRequest.Builder, NetworkBid.BidResponse.Builder> {
  private static final Logger logger = LoggerFactory.getLogger(DoubleClickOpenRtbMapper.class);
  private static final String YOUTUBE_AFV_USER_ID = "afv_user_id_";
  private static final Pattern SEMITRANSPARENT_CHANNEL =
      Pattern.compile("pack-(brand|semi|anon)-([^\\-]+)::(.+)");
  private static final Joiner versionJoiner = Joiner.on(".").skipNulls();
  private static final int MICROS_PER_CURRENCY_UNIT = 1_000_000;

  private final DoubleClickMetadata metadata;
  private final DoubleClickCrypto.Hyperlocal hyperlocalCrypto;
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

  @Inject
  public DoubleClickOpenRtbMapper(
      MetricRegistry metricRegistry,
      DoubleClickMetadata metadata,
      @Nullable OpenRtbJsonFactory jsonFactory,
      @Nullable DoubleClickCrypto.Hyperlocal hyperlocalCrypto,
      List<ExtMapper> extMappers) {
    this.metadata = metadata;
    this.hyperlocalCrypto = hyperlocalCrypto;
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
  }

  @Override
  public NetworkBid.BidResponse.Builder toExchangeBidResponse(
    OpenRtb.BidRequest request, OpenRtb.BidResponse response) {
    checkNotNull(request);
    NetworkBid.BidResponse.Builder dcResponse = NetworkBid.BidResponse.newBuilder();

    if (response.hasBidid()) {
      dcResponse.setDebugString(response.getBidid());
    }

    for (SeatBid seatBid : response.getSeatbidList()) {
      for (Bid bid : seatBid.getBidList()) {
        dcResponse.addAd(buildResponseAd(request, bid));
      }
    }

    return dcResponse;
  }

  protected NetworkBid.BidResponse.Ad.Builder buildResponseAd(
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

    Imp matchingImp = OpenRtbUtils.impWithId(request, bid.getImpid());
    if (matchingImp == null) {
      invalidImp.inc();
      throw new MapperException(
          "Impresson.id doesn't match any request impression: %s", bid.getImpid());
    }

    if (matchingImp.hasVideo()) {
      dcAd.setVideoUrl(bid.getAdm());
      setAdSize(bid, dcAd, matchingImp);
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
      setAdSize(bid, dcAd, matchingImp);
    } else if (matchingImp.hasNative()) {
      dcAd.setNativeAd(nativeMapper.buildNativeResponse(bid, matchingImp));
    } else {
      noVideoOrBanner.inc();
      throw new MapperException("Imp has neither of Video or Banner");
    }

    NetworkBid.BidResponse.Ad.AdSlot.Builder dcSlot = dcAd.addAdslotBuilder()
        .setId(Integer.parseInt(bid.getImpid()))
        .setMaxCpmMicros((long) (bid.getPrice() * MICROS_PER_CURRENCY_UNIT));
    if (matchingImp.getExtension(DcExt.adSlot).getMatchingAdDataCount() > 1) {
      if (bid.hasCid()) {
        dcSlot.setAdgroupId(Long.parseLong(bid.getCid()));
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

    if (bid.hasNurl()) {
      dcAd.setImpressionTrackingUrl(bid.getNurl());
    }

    if (bid.getCatCount() != 0) {
      dcAd.addAllCategory(AdCategoryMapper.toDoubleClick(bid.getCatList(), null));
    }

    for (ExtMapper extMapper : extMappers) {
      extMapper.toDoubleClickAd(request, bid, dcAd);
    }

    return dcAd;
  }

  protected void setAdSize(Bid bid, NetworkBid.BidResponse.Ad.Builder dcAd, Imp matchingImp) {
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

  @Override
  public OpenRtb.BidRequest.Builder toOpenRtbBidRequest(NetworkBid.BidRequest dcRequest) {
    OpenRtb.BidRequest.Builder request = OpenRtb.BidRequest.newBuilder()
        .setId(BaseEncoding.base64Url().omitPadding().encode(dcRequest.getId().toByteArray()));

    if (dcRequest.getIsPing()) {
      return request;
    }

    request.setAt(AuctionType.SECOND_PRICE);

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

    request.setDevice(buildDevice(dcRequest, coppa));

    if (dcRequest.hasMobile()) {
      request.setApp(buildApp(dcRequest));
    } else {
      request.setSite(buildSite(dcRequest));
    }

    addBcat(dcRequest, request);

    for (NetworkBid.BidRequest.AdSlot dcSlot : dcRequest.getAdslotList()) {
      Imp.Builder imp = buildImp(dcRequest, dcSlot);
      if (imp != null) {
        request.addImp(imp);
      }
    }
    if (request.getImpCount() == 0) {
      noImp.inc();
      logger.debug("Request has no impressions");
    }

    User.Builder user = buildUser(dcRequest, coppa);
    if (user != null) {
      request.setUser(user);
    }

    if (dcRequest.hasIsTest()) {
      request.setTest(dcRequest.getIsTest());
    }
    request.setTmax(100);

    for (ExtMapper extMapper : extMappers) {
      extMapper.toOpenRtbBidRequest(dcRequest, request);
    }

    return request;
  }

  protected @Nullable User.Builder buildUser(NetworkBid.BidRequest dcRequest, boolean coppa) {
    if ((!coppa && !dcRequest.hasGoogleUserId())
        || (coppa && !dcRequest.hasConstrainedUsageGoogleUserId())) {
      return null;
    }

    User.Builder user = User.newBuilder().setId(coppa
        ? dcRequest.getConstrainedUsageGoogleUserId()
        : dcRequest.getGoogleUserId());

    if ((coppa && dcRequest.hasConstrainedUsageHostedMatchData())
        || (!coppa && dcRequest.hasHostedMatchData())) {
      ByteString dcHMD = coppa
          ? dcRequest.getConstrainedUsageHostedMatchData()
          : dcRequest.getHostedMatchData();
      user.setCustomdata(BaseEncoding.base64Url().omitPadding().encode(dcHMD.toByteArray()));
    }

    if (dcRequest.hasUserDemographic()) {
      NetworkBid.BidRequest.UserDemographic dcUser = dcRequest.getUserDemographic();

      if (dcUser.hasGender()) {
        User.Gender gender = GenderMapper.toOpenRtb(dcUser.getGender());
        if (gender != null) {
          user.setGender(gender);
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
        String name = metadata.getPublisherVerticals().get(dcVertical.getId());
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

  protected Imp.Builder buildImp(
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

      Pmp.Builder pmp = buildPmp(dcAdData);
      if (pmp != null) {
        imp.setPmp(pmp);
      }
    }

    if (bidFloor != null) {
      imp.setBidfloor(((double) bidFloor) / MICROS_PER_CURRENCY_UNIT);
    }

    if (dcRequest.getMobile().hasIsInterstitialRequest()) {
      imp.setInstl(dcRequest.getMobile().getIsInterstitialRequest());
    }

    if (dcSlot.hasAdBlockKey()) {
      imp.setTagid(String.valueOf(dcSlot.getAdBlockKey()));
    }

    if (dcSlot.getNativeAdTemplateCount() != 0) {
      imp.setNative(nativeMapper.buildNativeRequest(dcSlot));
    } else if (dcRequest.hasVideo()) {
      imp.setVideo(buildVideo(dcSlot, dcRequest.getVideo()));
    } else {
      imp.setBanner(buildBanner(dcSlot));
    }

    for (ExtMapper extMapper : extMappers) {
      extMapper.toOpenRtbImp(dcSlot, imp);
    }

    return imp;
  }

  protected Banner.Builder buildBanner(NetworkBid.BidRequest.AdSlot dcSlot) {
    Banner.Builder banner = Banner.newBuilder()
        .setId(String.valueOf(dcSlot.getId()))
        .addAllBattr(CreativeAttributeMapper.toOpenRtb(dcSlot.getExcludedAttributeList(), null));

    setSize(banner, dcSlot.getWidthList(), dcSlot.getHeightList());

    if (dcSlot.hasSlotVisibility()) {
      AdPosition pos = AdPositionMapper.toOpenRtb(dcSlot.getSlotVisibility());
      if (pos != null) {
        banner.setPos(pos);
      }
    }

    if (!dcSlot.getExcludedAttributeList().contains(32 /* MraidType: Mraid 1.0 */)) {
      banner.addApi(APIFramework.MRAID_1);
    }
    banner.addApi(APIFramework.MRAID_2);

    banner.addAllExpdir(ExpandableDirectionMapper.toOpenRtb(dcSlot.getExcludedAttributeList()));

    if (dcSlot.hasIframingState()) {
      Boolean f = IFramingStateMapper.toOpenRtb(dcSlot.getIframingState());
      if (f != null) {
        banner.setTopframe(f);
      }
    }

    for (ExtMapper extMapper : extMappers) {
      extMapper.toOpenRtbBanner(dcSlot, banner);
    }

    return banner;
  }

  protected @Nullable Pmp.Builder buildPmp(NetworkBid.BidRequest.AdSlot.MatchingAdData dcAdData) {
    if (dcAdData.getDirectDealCount() == 0) {
      return null;
    }

    Pmp.Builder pmp = Pmp.newBuilder();
    for (NetworkBid.BidRequest.AdSlot.MatchingAdData.DirectDeal dcDeal
        : dcAdData.getDirectDealList()) {
      if (dcDeal.hasDirectDealId()) {
        Deal.Builder deal = Deal.newBuilder()
            .setId(String.valueOf(dcDeal.getDirectDealId()));
        if (dcDeal.hasFixedCpmMicros()) {
          deal.setBidfloor(dcDeal.getFixedCpmMicros() / ((double) MICROS_PER_CURRENCY_UNIT));
        }
        if (dcDeal.hasDealType()) {
          AuctionType type = DealTypeMapper.toOpenRtb(dcDeal.getDealType());
          if (type != null) {
            deal.setAt(type);
          }
        }
        pmp.addDeals(deal);
      }
    }

    for (ExtMapper extMapper : extMappers) {
      extMapper.toOpenRtbPmp(dcAdData, pmp);
    }

    return pmp;
  }

  protected Video.Builder buildVideo(
      NetworkBid.BidRequest.AdSlot dcSlot, NetworkBid.BidRequest.Video dcVideo) {
    Video.Builder video = Video.newBuilder()
        .addProtocols(VideoBidResponseProtocol.VAST_2_0)
        .addProtocols(VideoBidResponseProtocol.VAST_3_0)
        .addAllBattr(CreativeAttributeMapper.toOpenRtb(dcSlot.getExcludedAttributeList(), null));

    if (dcVideo.hasMinAdDuration()) {
      video.setMinduration(dcVideo.getMinAdDuration());
    }
    if (dcVideo.hasMaxAdDuration()) {
      video.setMaxduration(dcVideo.getMaxAdDuration());
    }

    if (dcVideo.getAllowedVideoFormatsCount() != 0) {
      video.addAllMimes(VideoMimeMapper.toOpenRtb(dcVideo.getAllowedVideoFormatsList(), null));
    }

    if (dcSlot.hasSlotVisibility()) {
      AdPosition pos = AdPositionMapper.toOpenRtb(dcSlot.getSlotVisibility());
      if (pos != null) {
        video.setPos(pos);
      }
    }

    if (dcVideo.hasVideoadStartDelay()) {
      video.setStartdelay(VideoStartDelayMapper.toDoubleClick(dcVideo.getVideoadStartDelay()));
    }

    if (!dcSlot.getExcludedAttributeList().contains(30 /* InstreamVastVideoType: Vpaid Flash */)) {
      video.addApi(APIFramework.VPAID_1);
      video.addApi(APIFramework.VPAID_2);
    }
    if (!dcSlot.getExcludedAttributeList().contains(32 /* MraidType: Mraid 1.0 */)) {
      video.addApi(APIFramework.MRAID_1);
    }
    video.addApi(APIFramework.MRAID_2);

    if (dcSlot.getWidthCount() == 1) {
      video.setW(dcSlot.getWidth(0));
      video.setH(dcSlot.getHeight(0));
    } else if (dcSlot.getWidthCount() != 0) {
      logger.debug("Video request is multisize; no size mapped");
    }

    if (dcVideo.getCompanionSlotCount() != 0) {
      EnumSet<VASTCompanionType> companionTypes = EnumSet.noneOf(VASTCompanionType.class);

      for (NetworkBid.BidRequest.Video.CompanionSlot dcCompSlot
          : dcVideo.getCompanionSlotList()) {
        Banner.Builder companion = Banner.newBuilder();
        setSize(companion, dcCompSlot.getWidthList(), dcCompSlot.getHeightList());

        if (dcCompSlot.getCreativeFormatCount() != 0) {
          companion.addAllMimes(
              BannerMimeMapper.toOpenRtb(dcCompSlot.getCreativeFormatList(), null));
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

  protected Device.Builder buildDevice(NetworkBid.BidRequest dcRequest, boolean coppa) {
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
      NetworkBid.BidRequest.Mobile dcMobile = dcRequest.getMobile();

      if ((coppa && dcMobile.hasConstrainedUsageEncryptedAdvertisingId())
          || (!coppa && dcMobile.hasEncryptedAdvertisingId())) {
        device.setIfa(BaseEncoding.base16().encode((coppa
            ? dcMobile.getConstrainedUsageEncryptedAdvertisingId()
            : dcMobile.getEncryptedAdvertisingId()).toByteArray()));
        device.setLmt(false);
      }
      else if ((coppa && dcMobile.hasConstrainedUsageEncryptedHashedIdfa())
          || (!coppa && dcMobile.hasEncryptedHashedIdfa())) {
        device.setDpidmd5(BaseEncoding.base16().encode((coppa
            ? dcMobile.getConstrainedUsageEncryptedHashedIdfa()
            : dcMobile.getEncryptedHashedIdfa()).toByteArray()));
        device.setLmt(false);
      } else {
        device.setLmt(true);
      }

      if (dcMobile.hasCarrierId()) {
        device.setCarrier(String.valueOf(dcMobile.getCarrierId()));
      }
      if (dcMobile.hasModel()) {
        device.setModel(dcMobile.getModel());
      }
      if (dcMobile.hasPlatform()) {
        device.setOs(dcMobile.getPlatform());
      }
      if (dcMobile.hasOsVersion()) {
        NetworkBid.BidRequest.Mobile.DeviceOsVersion dcVer = dcMobile.getOsVersion();
        device.setOsv(versionJoiner.join(
            dcVer.hasOsVersionMajor() ? dcVer.getOsVersionMajor() : null,
            dcVer.hasOsVersionMinor() ? dcVer.getOsVersionMinor() : null,
            dcVer.hasOsVersionMicro() ? dcVer.getOsVersionMicro() : null));
      }
      if (dcMobile.hasMobileDeviceType()) {
        DeviceType type = DeviceTypeMapper.toOpenRtb(dcMobile.getMobileDeviceType());
        if (type != null) {
          device.setDevicetype(type);
        }
      }
      if (dcMobile.hasScreenWidth()) {
        device.setW(dcMobile.getScreenWidth());
      }
      if (dcMobile.hasScreenHeight()) {
        device.setH(dcMobile.getScreenHeight());
      }
      if (dcMobile.hasDevicePixelRatioMillis()) {
        device.setPxratio(dcMobile.getDevicePixelRatioMillis() / 1000.0);
      }
    }

    for (ExtMapper extMapper : extMappers) {
      extMapper.toOpenRtbDevice(dcRequest, device);
    }

    return device;
  }

  protected @Nullable Geo.Builder buildGeo(NetworkBid.BidRequest dcRequest) {
    if (!dcRequest.hasGeoCriteriaId() && !dcRequest.hasEncryptedHyperlocalSet()
        && !dcRequest.hasPostalCode() && !dcRequest.hasPostalCodePrefix()) {
      return null;
    }

    Geo.Builder geo = Geo.newBuilder();
    NetworkBid.BidRequest.HyperlocalSet hyperlocalSet = null;

    if (dcRequest.hasPostalCode()) {
      geo.setZip(dcRequest.getPostalCode());
    } else if (dcRequest.hasPostalCodePrefix()) {
      geo.setZip(dcRequest.getPostalCodePrefix());
    }

    if (dcRequest.hasGeoCriteriaId()) {
      int geoCriteriaId = dcRequest.getGeoCriteriaId();

      GeoTarget geoTarget = metadata.getGeoTarget(geoCriteriaId);

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
        hyperlocalSet = NetworkBid.BidRequest.HyperlocalSet
            .parseFrom(hyperlocalCrypto.decryptHyperlocal(
                dcRequest.getEncryptedHyperlocalSet().toByteArray()));
        if (hyperlocalSet.hasCenterPoint()) {
          NetworkBid.BidRequest.Hyperlocal.Point center = hyperlocalSet.getCenterPoint();
          if (center.hasLatitude() && center.hasLongitude()) {
            geo.setLat(center.getLatitude());
            geo.setLon(center.getLongitude());
          }
        }
      } catch (InvalidProtocolBufferException | SignatureException | IllegalArgumentException e) {
        invalidHyperlocal.inc();
        logger.warn("Invalid encrypted_hyperlocal_set: {}", e.toString());
      }
    }

    if (dcRequest.hasTimezoneOffset()) {
      geo.setUtcoffset(dcRequest.getTimezoneOffset());
    }

    for (ExtMapper extMapper : extMappers) {
      extMapper.toOpenRtbGeo(dcRequest, geo, hyperlocalSet);
    }

    return geo;
  }

  protected void mapGeo(GeoTarget geoTarget, Geo.Builder geo) {
    String countryAlpha2 = null;
    for (int chain = 0; chain < 2; ++chain) {
      for (GeoTarget target = geoTarget; target != null;
          // Looks up the canonical chain last, so its results overwrite those
          // obtained by the parentId chain if there's conflict
          target = (chain == 0) ? target.getIdParent() : target.getCanonParent()) {
        if (target.getCountryCode() != null) {
          countryAlpha2 = target.getCountryCode();
        }
        switch (target.getType()) {
          case CITY:
          case PREFECTURE:
            geo.setCity(target.getName());
            geo.setCountry(target.getCountryCode());
            break;

          case DMA_REGION:
            geo.setMetro(target.getName());
            break;

          case STATE:
          case REGION:
            geo.setRegion(target.getName());
            break;

          default:
        }
      }
      if (countryAlpha2 != null) {
        CountryCodes countryCodes = metadata.getCountryCodes().get(countryAlpha2);
        if (countryCodes != null) {
          geo.setCountry(countryCodes.getAlpha3());
        }
      }
    }
  }

  protected Site.Builder buildSite(NetworkBid.BidRequest dcRequest) {
    Site.Builder site = Site.newBuilder();

    Builder content = buildContent(dcRequest);
    if (content != null) {
      site.setContent(content);
    }

    if (dcRequest.getMobile().hasIsMobileWebOptimized()) {
      site.setMobile(dcRequest.getMobile().getIsMobileWebOptimized());
    }

    if (dcRequest.hasUrl()) {
      site.setPage(dcRequest.getUrl());
    } else if (dcRequest.hasAnonymousId()) {
      site.setName(dcRequest.getAnonymousId());
    } else {
      if (logger.isDebugEnabled()) {
        logger.debug("Site request is missing both url and anonymousId: {}",
            TextFormat.shortDebugString(dcRequest));
      }
    }

    String channelId = findChannelId(dcRequest);
    if (channelId != null) {
      site.setId(channelId);
    }

    Publisher.Builder pub = buildPublisher(dcRequest);
    if (pub != null) {
      site.setPublisher(pub);
    }

    for (ExtMapper extMapper : extMappers) {
      extMapper.toOpenRtbSite(dcRequest, site);
    }

    return site;
  }

  protected App.Builder buildApp(NetworkBid.BidRequest dcRequest) {
    NetworkBid.BidRequest.Mobile dcMobile = dcRequest.getMobile();
    App.Builder app = App.newBuilder();

    Content.Builder content = buildAppContent(dcRequest);
    if (content != null) {
      app.setContent(content);
    }

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

    for (ExtMapper extMapper : extMappers) {
      extMapper.toOpenRtbApp(dcRequest, app);
    }

    return app;
  }

  protected @Nullable Content.Builder buildAppContent(NetworkBid.BidRequest dcRequest) {
    Content.Builder content = buildContent(dcRequest);
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
    } else if (dcRequest.hasAnonymousId()) {
      content.setId(dcRequest.getAnonymousId());
    } else {
      if (logger.isDebugEnabled()) {
        logger.debug("App request is missing both url and anonymousId: {}",
            TextFormat.shortDebugString(dcRequest));
      }
    }

    if (dcRequest.getMobile().hasAppRating()) {
      content.setUserrating(String.valueOf(dcRequest.getMobile().getAppRating()));
    }

    return content;
  }

  protected @Nullable Publisher.Builder buildPublisher(NetworkBid.BidRequest dcRequest) {
    if (!dcRequest.hasSellerNetworkId()) {
      return null;
    }

    Publisher.Builder publisher = Publisher.newBuilder()
        .setId(String.valueOf(dcRequest.getSellerNetworkId()));

    String sellerNetwork = metadata.getSellerNetworks().get(dcRequest.getSellerNetworkId());
    if (sellerNetwork != null) {
      publisher.setName(sellerNetwork);
    }

    return publisher;
  }

  protected @Nullable String findChannelId(NetworkBid.BidRequest dcRequest) {
    for (NetworkBid.BidRequest.AdSlot dcSlot : dcRequest.getAdslotList()) {
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

  protected @Nullable Content.Builder buildContent(NetworkBid.BidRequest dcRequest) {
    if (dcRequest.getDetectedLanguageCount() == 0
        && dcRequest.getDetectedContentLabelCount() == 0
        && !dcRequest.getVideo().hasContentAttributes()) {
      return null;
    }

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
      content.addAllKeywords(dcContent.getKeywordsList());
    }

    return content;
  }

  protected static String getLanguage(String langCulture) {
    int iSep = langCulture.indexOf('_');
    return iSep == -1 ? langCulture : langCulture.substring(0, iSep);
  }

  protected void addBcat(NetworkBid.BidRequest dcRequest, OpenRtb.BidRequest.Builder request) {
    EnumSet<ContentCategory> cats = EnumSet.noneOf(ContentCategory.class);

    for (NetworkBid.BidRequest.AdSlot dcSlot : dcRequest.getAdslotList()) {
      AdCategoryMapper.toOpenRtb(dcSlot.getExcludedProductCategoryList(), cats);
      AdCategoryMapper.toOpenRtb(dcSlot.getExcludedSensitiveCategoryList(), cats);
    }

    request.addAllBcat(cats);
  }

  /**
   * Not implemented yet!
   */
  @Override
  public NetworkBid.BidRequest.Builder toExchangeBidRequest(OpenRtb.BidRequest request) {
    throw new UnsupportedOperationException();
  }

  /**
   * Not implemented yet!
   */
  @Override
  public OpenRtb.BidResponse.Builder toOpenRtbBidResponse(
      NetworkBid.BidRequest request, NetworkBid.BidResponse response) {
    throw new UnsupportedOperationException();
  }
}
