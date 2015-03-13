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


/**
 * See <a href="https://developers.google.com/ad-exchange/rtb/response-guide#specifying-macros">
 * DoubleClick Ad Exchange's documentation for bid response macros</a>.
 * <p>
 * Specify a macro as part of an HTML snippet in the format %%MACRO%%, where MACRO is one of the
 * supported macros listed in the table below.
 * <p>
 * Google requires that you use use either the CLICK_URL_UNESC or the CLICK_URL_ESC macros within
 * the creative of the third-party served ad. Google uses the CLICK_URL macros for click tracking.
 * To use a macro, include it in the ad so that the URL gets fetched when someone clicks on it.
 * The return value of the fetch is a redirect to another URL that you append to the CLICK_URL.
 */
public enum DoubleClickMacros {
  /**
   * A string representation of a random, unsigned, four-byte integer.
   */
  CACHEBUSTER("%%CACHEBUSTER%%", true),
  /**
   * The escaped click URL for the ad. Use this instead of CLICK_URL_UNESC if you need to
   * first pass the value through another server that will then return a redirect.
   */
  CLICK_URL_ESC("%%CLICK_URL_ESC%%", false),
  /**
   * The double-escaped URL for the ad. Use this instead of CLICK_URL_UNESC if you need to
   * first pass the value through another server that will then return a redirect.
   */
  CLICK_URL_ESC_ESC("%%CLICK_URL_ESC_ESC%%", false),
  /**
   * The unescaped click URL for the ad.  In the snippet, an escaped version of the third party
   * click URL should directly follow the macro.
   */
  CLICK_URL_UNESC("%%CLICK_URL_UNESC%%", false),
  /**
   * Resolves to {@code http:} or {@code https:}, depending on the inventory type.
   * Only the HTML tag’s text provided to Ad Exchange will be adapted, so fourth-party calls
   * outside the HTML tag’s text will not be adapted by this macro.
   */
  SCHEME("%%SCHEME%%", false),
  /**
   * The url-escaped domain of the content URL or the anonymous ID for anonymous inventory.
   */
  SITE("%%SITE%%", false),
  /**
   * The time-zone offset.
   */
  TZ_OFFSET("%%TZ_OFFSET%%", false),
  /**
   * The encoded impression cost (that is, CPI rather than CPM) in micros of the account currency.
   * For example, a winning CPM of $5 USD corresponds to 5,000,000 micros CPM, or 5,000 micros CPI.
   * The decoded value of WINNING_PRICE in this case would be 5,000. If the cost of the impression
   * is not known at the time the ad is served, the macro is replaced by the string UNKNOWN.
   * @see com.google.doubleclick.crypto.DoubleClickCrypto.Price
   */
  WINNING_PRICE("%%WINNING_PRICE%%", true),
  /**
   * URL-escaped WINNING_PRICE.
   * @see com.google.doubleclick.crypto.DoubleClickCrypto.Price
   */
  WINNING_PRICE_ESC("%%WINNING_PRICE_ESC%%", true),

  // Cookie matching macros, see https://developers.google.com/ad-exchange/rtb/cookie-guide

  /**
   * {@code <google user id>}
   */
  GOOGLE_GID("%%GOOGLE_GID%%", true),
  /**
   * {@code &google_gid=<google user id>}
   */
  GOOGLE_GID_PAIR("%%GOOGLE_GID_PAIR%%", true),
  /**
   * {@code <cookie version number>}
   */
  GOOGLE_CVER("%%GOOGLE_CVER%%", true),
  /**
   * {@code &cver=<cookie version number>}
   */
  GOOGLE_CVER_PAIR("%%GOOGLE_CVER_PAIR%%", true),
  /**
   * {@code <error id>}
   */
  GOOGLE_ERROR("%%GOOGLE_ERROR%%", true),
  /**
   * {@code &google_error=<error id>}
   */
  GOOGLE_ERROR_PAIR("%%GOOGLE_ERROR_PAIR%%", true),
  /**
   * {@code <pixel match data>}
   */
  GOOGLE_PUSH("%%GOOGLE_PUSH%%", true),
  /**
   * {@code &google_push=<pixel match data>}
   */
  GOOGLE_PUSH_PAIR("%%GOOGLE_PUSH_PAIR%%", true),
  /**
   * {@code google_gid=<google user id>&cver=<cookie version number>&google_error=<error id>}
   */
  GOOGLE_ALL_PARAMS("%%GOOGLE_ALL_PARAMS%%", true)
  ;

  private final String key;
  private final boolean videoSupported;

  private DoubleClickMacros(String key, boolean videoSupported) {
    this.key = key;
    this.videoSupported = videoSupported;
  }

  /**
   * Returns the key for this macro (string that will be substituted when the macro is processed).
   */
  public final String key() {
    return key;
  }

  /**
   * Returns <code>true</code> if this macro is supported by HTML creatives.
   */
  public final boolean htmlSupported() {
    return true;
  }

  /**
   * Returns <code>true</code> if this macro is supported by video creatives.
   */
  public final boolean videoSupported() {
    return videoSupported;
  }
}
