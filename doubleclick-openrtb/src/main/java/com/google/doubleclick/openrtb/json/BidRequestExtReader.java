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
import com.google.doubleclick.AdxExt.BidRequestExt;
import com.google.doubleclick.AdxExt.BidRequestExt.BidFeedback;
import com.google.doubleclick.AdxExt.BidRequestExt.BidFeedback.EventNotificationToken;
import com.google.openrtb.OpenRtb.BidRequest;
import com.google.openrtb.json.OpenRtbJsonExtComplexReader;
import java.io.IOException;

/**
 * Reader for {@link BidRequestExt}.
 */
class BidRequestExtReader
extends OpenRtbJsonExtComplexReader<BidRequest.Builder, BidRequestExt.Builder> {

  public BidRequestExtReader() {
    super(AdxExt.bidRequest, false, "bid_feedback", "google_query_id");
  }

  @Override protected void read(BidRequestExt.Builder ext, JsonParser par) throws IOException {
    switch (getCurrentName(par)) {
      case "bid_feedback":
        for (startArray(par); endArray(par); par.nextToken()) {
          ext.addBidFeedback(readBidFeedback(par));
        }
        break;
      case "google_query_id":
        ext.setGoogleQueryId(par.nextTextValue());
    }
  }

  private BidFeedback.Builder readBidFeedback(JsonParser par) throws IOException {
    BidFeedback.Builder feedback = BidFeedback.newBuilder();
    for (startObject(par); endObject(par); par.nextToken()) {
      String fieldName = getCurrentName(par);
      if (par.nextToken() != JsonToken.VALUE_NULL) {
        readBidFeedbackField(par, feedback, fieldName);
      }
    }
    return feedback;
  }

  private void readBidFeedbackField(
      JsonParser par, BidFeedback.Builder feedback, String fieldName) throws IOException {
    switch (fieldName) {
      case "request_id":
        feedback.setRequestId(par.getText());
        break;

      case "creative_status_code":
        feedback.setCreativeStatusCode(par.getIntValue());
        break;

      case "price":
        feedback.setPrice(par.getDoubleValue());
        break;

      case "buyer_creative_id":
        feedback.setBuyerCreativeId(par.getText());
        break;

      case "event_notification_token":
        feedback.setEventNotificationToken(readEventNotificationToken(par));
        break;
    }
  }

  private EventNotificationToken.Builder readEventNotificationToken(JsonParser par) throws IOException {
    EventNotificationToken.Builder token = EventNotificationToken.newBuilder();
    for (startObject(par); endObject(par); par.nextToken()) {
      String fieldName = getCurrentName(par);
      if (par.nextToken() != JsonToken.VALUE_NULL) {
        readEventNotificationTokenField(par, token, fieldName);
      }
    }
    return token;
  }

  private void readEventNotificationTokenField(
      JsonParser par, EventNotificationToken.Builder token, String fieldName) throws IOException {
    switch (fieldName) {
      case "payload":
        token.setPayload(par.getText());
        break;
    }
  }
}
