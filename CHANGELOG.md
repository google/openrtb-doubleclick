RELEASE NOTES
----------------------------------------------------------------------

## Version 1.0.3, ??-03-2016
* Drop support for `Bid.nurl` mapping to impression URL. Please use
  the extension `AdxExt.BidExt.impression_tracking_url` only.
* Added `AdxExtMapper` to support some `AdxExt` extensions as part of
  the AdX->OpenRTB protocol mapping.

## Version 1.0.2, 26-02-2016
* Mapping of mobile interstitial video impression doesn't fail if
  it's multisize, it just picks the first size.
* DoubleClick protocol v.80: Deprecates `Video.inventory_type`,
  updates `Video.format`, adds `Video.placement` and `AdSlot.renderer`. 

## Version 1.0.1, 04-02-2016
* DoubleClick protocol v.77: `BidRequest.vertical_dictionary_version`
  deprecated; added `Video.is_clickable`, `Video.end_cap_support`,
  `AdSlot.click_through_rate`, `Device.hardware_version`.
* Maps `Device.hwv`.
* Native request mapping uses only first `NativeAdTemplate`; previous
  mapping (merging assets from multiple templates) was ambiguous.
* Support for AdX/OpenRTB JSON extensions! Background: this library
  always supported client-side extensions (`doubleclick-ext.proto`)
  which are just soft links from the OpenRTB messages to their source AdX
  messages, allowing a fallback for fields that couldn't be mapped.
  Now AdX's new native (on-wire) support for OpenRTB is also introducing
  its own proper OpenRTB extensions, defined in `openrtb-adx.proto`.
  These two kinds of extensions are mutually exclusive: you can only
  use the first when receiving the native AdX protocol on-wire and
  mapping it to OpenRTB with this library; you can only use the second
  when receiving AdX/OpenRTB messages (either Protobuf or JSON encoding).

## Version 1.0.0, 04-01-2016
* Happy new year!!  And here's the one-dot-zero release of the library.
* This release follow the GA of the native AdX/OpenRTB protocol support;
  the OpenRTB mapper here makes the same mappings for standard fields.
  The only advantage of the client-side mapper at this point is making
  all AdX fields available via the `DcExt` extension, which isn't and
  won't be supported by the native protocol. The latter will be improved
  with extensions for critical fields missing from OpenRTB, and later
  this year the client-side mapper will be retired.
* Video mapping improvements: includes VAST Wrapper values; additional
  mime types for video and companion ads with VPAID enabled.
* `Device.os` mapping fixed for iphone/ipad="iOS"; `Device.make` added.
* Cleanups: Dependency updates; Minor javadoc and test reviews.

## Version 0.9.13, 12-20-2015
* DoubleClick protocol v.74: `NativeAd.impression_tracking_url` is
  deprecated; use the now-GA field `Ad.impression_tracking_url`.
* Native response improvements: changes mapping `link.imprackers`
  to `Ad.impression_tracking_url`; changes ADDRESS asset ->
  `NativeAd.store` to map from the asset's `link.url`; adds mapping
  `link.url` -> `Ad.click_through_url`.

## Version 0.9.12, 12-11-2015
* Fix mapping of `Video.minduration/maxduration/startdelay`.

## Version 0.9.11, 04-11-2015
* Fix mapping of non-interstitial video impressions.
* Removed mapping of `AdSlot.targetable_channel` to `App`/`Site`.`id`;
  this mapping was inadequate. May return later as an extension.
* Avoid creating an `App` or `Site` that's empty except for extensions.

## Version 0.9.10, 29-10-2015
* THE BIG MOVE TO JAVA 8! The library now requires JDK 8, and takes
  advantage of new APIs/features of Java 8.
  - We won't maintain a JDK 7 compatible version of the library; notice
    that JDK 7 was EOL'd since April 2015.
* DoubleClick protocol v.73: Adds `AutoRefresh.refresh_count`.

## Version 0.9.9, 26-09-2015
* DoubleClick protocol v.71: Deprecates `BidRequest.site_list_id`.
* Fix NPE bug (0.9.6+) in the mapping of `Video.playbackmethod`.

## Version 0.9.8, 06-10-2015
* Fixed distinction of `App` vs. `Site` requests in the mapper.

## Version 0.9.7, 02-10-2015
* DoubleClick protocol v.70: Refactors part of `BidRequest`'s `Mobile`
  into a new object `Device`; OpenRTB mapping is updated for that.
* Big round of javadoc & style reviews; tests adopted Truth.

## Version 0.9.6, 22-09-2015
* DoubleClick protocol v.69: Adds unencrypted versions of several
  fields (SSL-exclusive); the mapper doesn't support the encrypted
  hyperlocal anymore. Adds `Video.playback_method`, which now allows
  mapping OpenRTB's `Video.playbackmethod`.  Replaces / renames
  fields `adgroup_id`->`billing_id`.
* Updated mapping for MRAID: The DoubleClick creative attribute 32
  changed, now it means any MRAID (1 or 2) not just MRAID 1. Removed
  the confusing mapping of OpenRTB's `USER_INTERACTIVE` attribute.

## Version 0.9.5, 02-09-2015
* DoubleClick protocol v.68: Changes `MatchingAdData.adgroup_id` to
  `repeated`; adds `AdSlot.auto_refresh`; deprecates `snippet_template`.
* Added `DoubleClickMetadata.mobileCarriers()`.

## Version 0.9.4, 01-09-2015
* Fix missing mapping of some AdX category codes to `ContentCategory`.

## Version 0.9.3, 18-08-2015
* Improved `Video` mapping, supports interstitial video impressions.
* Maps `Bid.adomain` to `click_through_url`. Notice the OpenRTB
  specification only allows populating `adomain` with domain names
  (like “myads.com”), not full URLs. The mapper supports both:
  domains, which may result in less precise classification by AdX;
  and full URLs, which are ideal for AdX but not spec-compliant
  so avoid doing that in portable code used with other exchanges.

## Version 0.9.2, 22-07-2015
* Fixed mapping of `Geo.metro`, now gets the Google DMA Region code
  (as required by the spec) instead of the region name.
* Improvements to `DoubleClickMetadata` and related helper classes:
  - Added `dmaRegions()`, loads the Cities - DMA Regions table.
  - (Breaking) Renamed several getters that aren't JavaBean accessors.
  - Saving memory with interning of common strings across all metadata.

## Version 0.9.1, 15-07-2015
* Support for the `SnippetProcessor` improvements in _openrtb-core_.
* DoubleClick protocol v.64, changes `publisher_settings_list_id` from
  `optional` to `repeated`.

## Version 0.9.0, 01-07-2015
* Mapper changes for OpenRTB model changes.

## Version 0.8.6, 24-06-2015

* Small mapper reviews. Breaking changes to `ExtMapper`.
* Fixes for a couple potential NPEs.

## Version 0.8.5, 11-06-2015

* Fix mapping for `Bid.cat`, now includes all categories.
* Improved mapping for `ExpandableDirection`.
* Cleanup `ExtMapper.toDoubleClickAd()`, has one less parameter.

## Version 0.8.4, 29-05-2015

* Improved mapping of `Device.devicetype`.
* Changes for the new names of some OpenRTB messages and enums.
* Several small mapping reviews, in particular to avoid setting a value
  that would be redundant because it's the default in the target model.

## Version 0.8.3, 22-05-2015

* Maps `Video.content.title/len/keywords`.
* DoubleClick protocol v.63, only fixes two enum value names.
* Mapping of `Impression.bidfloor` now ignores DC `BuyerPricingRule`,
  which could rarely result in zero prices.

## Version 0.8.1, 29-04-2015

* DoubleClick protocol v.61, adds `inventory_type` and `deal_type`.
* Maps `BidRequest.at`, `Deal.at`.

## Version 0.8.0, 21-04-2015

* DoubleClick protocol v.60, adds `allowed_restricted_category_for_deals`.
* `DoubleClickOpenRtbNativeMapper` more lenient.
* Improved mapping of `api` fields (`ApiFramework`).
* Maps `Native.battr`, `Native.api`.

## Version 0.8.0-beta5, 03-04-2015

* Improved handling of `geo_criteria_id` in the `DoubleclickMetadata` API
  and the OpenRTB mapper. You should see more `Geo` fields populated
  for many requests, e.g. `city` for requests located at a postal code,
  `metro` for requests from a city that belongs to a metro, etc.

## Version 0.8.0-beta3, 31-03-2015

* Logging updates, mostly avoiding multiline logs (bad for syslog).
* Refactor some `DoubleClickMetadata` helper types.

## Version 0.8.0-beta2, 13-03-2015

* `BidRequest.id` & `User.customdata` using base64Url, not base16.
* Fix `bcat` mapping; optimize some enum mappings.
* More metadata: Agencies, Site lists, Creative status.
* Removed error-prone from build, new version had some issues too.

## Version 0.8.0-beta, 20-02-2015

* Support for Native Ads completed!
* Maps `Publisher.name`.
* `ExtMapper` has new methods that make easier to create mapper
  extensions for the objects: `App`, `Site`, `User`.
* DoubleClick protocol v.59, adds `mediation_status`.
* Updated to latest error-prone; now Maven build works with JDK 8!

## Version 0.7.5, 02-12-2014

* Partial support for OpenRTB 2.3! The missing item is Native ads,
  which depends on the OpenRTB Native 1.0 spec (proposed final draft
  at this time). This support will come in a future update.
  (Meanwhile you can bid on native ads with DoubleClick's protocol.)
  - Maps OpenRTB `Bid.cat` / DC `Ad.category`
  - Maps DC `BidRequest.isTest` / OpenRTB `BidRequest.test`
  - Maps DC `Mobile.[constrainedUsage]encryptedAdvertisingId`
    / OpenRTB `Device.ifa`; also, sets OpenRTB `Device.lmt`
  - Maps DC `Mobile.screenWidth/screenHeight` / OpenRTB `Device.w/h`
  - Maps DC `Mobile.devicePixelRatioMillis` / OpenRTB `Device.pxratio`
  - Maps DC `BidRequest.timezoneOffset` / OpenRTB `Geo.utcoffset`
  - Maps DC `Mobile.mobileWebOptimized` / OpenRTB `Site.mobile`
* Fix `BidResponse` mapping, was broken for non-multisize, interstitial
  video impression (DoubleClick requires setting `width/height`).
* Improvements and cleanups in the internal CSV parser.

## Version 0.7.4, 21-11-2014

* DoubleClick proto v57.  Notice that the major new in this update is
  native ads, but the corresponding OpenRTB spec is not finalized so
  there's no DoubleClick/OpenRTB mapping support at this time.
  - Maps DC `IFramingState` / OpenRTB `topframe`.
  - Maps OpenRTB `Bid.nurl` / DC `Ad.impressionTrackingUrl`

## Version 0.7.3, 17-11-2014

* Fix mapping of `BidRequest.User.customdata`.

## Version 0.7.2, 29-10-2014

* Updated to Protocol Buffers 2.6.1 (bugfix, doesn't require rebuilds).
* `DoubleClickMetadata` more resilient to bad resources.
* DoubleClick protocol v.54.
* Test & logging reviews.

## Version 0.7.1, 20-10-2014

* Updated to Protocol Buffers 2.6.0. Full rebuild recommended, the
  code generated by protoc 2.6.0 is NOT 100% compatible with 2.5.0.

## Version 0.7.0, 16-10-2014

* Fix namespace of DoubleClick proto to Google standard: replace all
  `com.google.doubleclick.Doubleclick`->`com.google.protos.adx.NetworkBid`.
* `DoubleClickValidator` validates SSL-enabled ads.

## Version 0.6.6, 14-10-2014

* DoubleClick proto v.52.
* Mapper: Much better mapping of IAB categories.
* New link extension `DcExt.bidResponse`.
* Javadocs for thead safety.
* Update Guava library.

## Version 0.6.5, 18-08-2014

- Crypto reviews: `javax.security`'s exceptions; initVector improvements;
  fix block cypher for >1 blocks; `Price` method names (millis/micros).
- Metadata: content-labels, publisher-verticals; better GeoTable parser.
- Mapper: Fix `AdPosition` & `Banner.mimes`; add `Content.contentrating`,
  `User.data.segment`, `Banner.expdir`, `Video.startdelay` (special values).
- DoubleClickValidator: validates `deal_id`; optimizations.

## Version 0.6.4, 10-08-2014

* Remove dependency from apache-commons-codec!
* `DoubleClickValidator` improved (better logs) and refactored to not
  depend on OpenRTB; moved to the doubleclick-core module.
* Provide a `DoubleClickMetadata.URLConnectionTransport`.
* Added missing two methods in the mapper interface.
* `DoubleClickCrypto.Price` supports micros & currency unit.
* No need anymore to checkout the openrtb project for building.

## Version 0.6.3, 02-08-2014

* Update DoubleClick protocol to v51.
* Map `User.gender/age` from new `UserDemographics` data.
* Decrypt `HyperlocalSet`, keep in a link extension, and map `Geo.lat/lon`.
* Map `Video.mimes` and `Video.companionad.mimes`.
* `DoubleClickCrypto`: IDFA/Hyperlocal now correct; big general review.
* Fixed mapping of `price` and `bidfloor` properties to use currency units
  (which is the standard). Previous code used micros, that was a legacy
  from this code's DoubleClick roots but was not OpenRTB-compliant.

## Version 0.6.2, 25-07-2014

* `DoubleClickCrypto`: optimize memory usage in Base64 encoding/decoding;
  some initial fixes for IDFA/Hyperlocal but still broken in some cases.
* Remove dependency on buggy, unsupported opencsv; using custom parser.
* Mapping: Fix semi-transparent branded channels.

## Version 0.6.1, 15-07-2014

* Remove depedency from Guice! The libraries still supports all
  JSR-305 DI frameworks, but now only uses `javax.inject`.
* DoubleClick protocol v50; map `app.content.userrating`, `app.name`.
* Build system improvements (Maven, Eclipse, NetBeans).
* Improved `OpenRtbSnippetProcessor` handling of macro dependencies;
  see new documentation about this in `OpenRtbMacros`.

## Version 0.6, 10-07-2014

* Initial Open Source release.
