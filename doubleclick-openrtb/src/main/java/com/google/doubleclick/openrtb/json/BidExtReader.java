/*
 * Copyright 2016 Google Inc. All Rights Reserved.
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

package com.google.doubleclick.openrtb.json;

import static com.google.openrtb.json.OpenRtbJsonUtils.endArray;
import static com.google.openrtb.json.OpenRtbJsonUtils.endObject;
import static com.google.openrtb.json.OpenRtbJsonUtils.getCurrentName;
import static com.google.openrtb.json.OpenRtbJsonUtils.startArray;
import static com.google.openrtb.json.OpenRtbJsonUtils.startObject;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.google.doubleclick.AdxExt;
import com.google.doubleclick.AdxExt.BidExt;
import com.google.doubleclick.AdxExt.BidExt.EventNotificationToken;
import com.google.openrtb.OpenRtb.BidResponse.SeatBid.Bid;
import com.google.openrtb.json.OpenRtbJsonExtComplexReader;
import java.io.IOException;

/**
 * Reader for {@link BidExt}.
 */
class BidExtReader extends OpenRtbJsonExtComplexReader<Bid.Builder, BidExt.Builder> {

  public BidExtReader() {
    super(AdxExt.bid, false,
        "impression_tracking_url", "ad_choices_destination_url", "bidder_name",
        "exchange_deal_type", "event_notification_token", "attribute", "amp_ad_url");
  }

  @Override protected void read(BidExt.Builder ext, JsonParser par) throws IOException {
    switch (getCurrentName(par)) {
      case "impression_tracking_url":
        for (startArray(par); endArray(par); par.nextToken()) {
          ext.addImpressionTrackingUrl(par.getText());
        }
        break;
      case "ad_choices_destination_url":
        ext.setAdChoicesDestinationUrl(par.nextTextValue());
        break;
      case "bidder_name":
        ext.setBidderName(par.nextTextValue());
        break;
      case "exchange_deal_type": {
          BidExt.ExchangeDealType value = BidExt.ExchangeDealType.forNumber(par.nextIntValue(0));
          if (checkEnum(value)) {
            ext.setExchangeDealType(value);
          }
        }
        break;
      case "event_notification_token":
        ext.setEventNotificationToken(readEventNotificationToken(par).build());
        break;

      case "attribute":
        for (startArray(par); endArray(par); par.nextToken()) {
          ext.addAttribute(par.getIntValue());
        }
        break;
      case "amp_ad_url":
        ext.setAmpAdUrl(par.nextTextValue());
        break;
    }
  }

  public final EventNotificationToken.Builder readEventNotificationToken(JsonParser par)
      throws IOException {
    EventNotificationToken.Builder token = EventNotificationToken.newBuilder();
    for (startObject(par); endObject(par); par.nextToken()) {
      String fieldName = getCurrentName(par);
      if (par.nextToken() != JsonToken.VALUE_NULL) {
        readEventNotificationTokenField(par, token, fieldName);
      }
    }
    return token;
  }

  protected void readEventNotificationTokenField(
      JsonParser par, EventNotificationToken.Builder token, String fieldName)
          throws IOException {
    switch (fieldName) {
      case "payload":
        token.setPayload(par.getText());
        break;
    }
  }
}
