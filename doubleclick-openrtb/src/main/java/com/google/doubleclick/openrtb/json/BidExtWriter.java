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

import static com.google.openrtb.json.OpenRtbJsonUtils.writeEnumField;
import static com.google.openrtb.json.OpenRtbJsonUtils.writeStrings;
import static com.google.openrtb.json.OpenRtbJsonUtils.writeInts;

import com.fasterxml.jackson.core.JsonGenerator;
import com.google.doubleclick.AdxExt.BidExt;
import com.google.openrtb.json.OpenRtbJsonExtWriter;
import java.io.IOException;

/**
 * Writer for {@link BidExt}.
 */
class BidExtWriter extends OpenRtbJsonExtWriter<BidExt> {

  @Override protected void write(BidExt ext, JsonGenerator gen) throws IOException {
    writeStrings("impression_tracking_url", ext.getImpressionTrackingUrlList(), gen);
    if (ext.hasAdChoicesDestinationUrl()) {
      gen.writeStringField("ad_choices_destination_url", ext.getAdChoicesDestinationUrl());
    }
    if (ext.hasBidderName()) {
      gen.writeStringField("bidder_name", ext.getBidderName());
    }
    if (ext.hasExchangeDealType()) {
      writeEnumField("exchange_deal_type", ext.getExchangeDealType(), gen);
    }
    writeInts("attributes", ext.getAttributeList(), gen);
  }
}
