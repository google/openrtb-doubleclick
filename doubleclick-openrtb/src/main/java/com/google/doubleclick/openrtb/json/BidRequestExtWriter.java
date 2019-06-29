/*
 * Copyright 2018 Google Inc. All Rights Reserved.
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

import com.fasterxml.jackson.core.JsonGenerator;
import com.google.doubleclick.AdxExt.BidRequestExt;
import com.google.doubleclick.AdxExt.BidRequestExt.BidFeedback;
import com.google.doubleclick.AdxExt.BidRequestExt.BidFeedback.EventNotificationToken;
import com.google.openrtb.json.OpenRtbJsonExtWriter;
import java.io.IOException;

/**
 * Writer for {@link BidRequestExt}.
 */
class BidRequestExtWriter extends OpenRtbJsonExtWriter<BidRequestExt> {

  @Override protected void write(BidRequestExt ext, JsonGenerator gen) throws IOException {
    if (ext.getBidFeedbackCount() != 0) {
      gen.writeArrayFieldStart("bid_feedback");
      for (BidFeedback feedback : ext.getBidFeedbackList()) {
        writeBidFeedback(feedback, gen);
      }
      gen.writeEndArray();
    }
    if (ext.hasGoogleQueryId()) {
      gen.writeStringField("google_query_id", ext.getGoogleQueryId());
    }
  }

  private void writeBidFeedback(BidFeedback feedback, JsonGenerator gen) throws IOException {
    gen.writeStartObject();
    writeBidFeedbackFields(feedback, gen);
    gen.writeEndObject();
    gen.flush();
  }

  private void writeBidFeedbackFields(BidFeedback feedback, JsonGenerator gen) throws IOException {
    if (feedback.hasRequestId()) {
      gen.writeStringField("request_id", feedback.getRequestId());
    }
    if (feedback.hasCreativeStatusCode()) {
      gen.writeNumberField("creative_status_code", feedback.getCreativeStatusCode());
    }
    if (feedback.hasPrice()) {
      gen.writeNumberField("price", feedback.getPrice());
    }
    if (feedback.hasMinimumBidToWin()) {
      gen.writeNumberField("minimum_bid_to_win", feedback.getMinimumBidToWin());
    }
    if (feedback.hasBuyerCreativeId()) {
      gen.writeStringField("buyer_creative_id", feedback.getBuyerCreativeId());
    }
    if (feedback.hasEventNotificationToken()) {
      gen.writeFieldName("event_notification_token");
      writeEventNotificationToken(feedback.getEventNotificationToken(), gen);
    }
  }

  private void writeEventNotificationToken(EventNotificationToken token, JsonGenerator gen)
      throws IOException {
    gen.writeStartObject();
    writeEventNotificationTokenFields(token, gen);
    gen.writeEndObject();
    gen.flush();
  }

  private void writeEventNotificationTokenFields(EventNotificationToken token, JsonGenerator gen)
      throws IOException {
    if (token.hasPayload()) {
      gen.writeStringField("payload", token.getPayload());
    }
  }
}
