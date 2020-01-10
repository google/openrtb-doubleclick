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

import static com.google.openrtb.json.OpenRtbJsonUtils.getCurrentName;

import com.fasterxml.jackson.core.JsonParser;
import com.google.doubleclick.AdxExt;
import com.google.doubleclick.AdxExt.DealExt;
import com.google.doubleclick.AdxExt.DealExt.DealType;
import com.google.openrtb.OpenRtb.BidRequest.Imp.Pmp.Deal;
import com.google.openrtb.json.OpenRtbJsonExtComplexReader;
import java.io.IOException;

/**
 * Reader for {@link DealExt}.
 */
class DealExtReader extends OpenRtbJsonExtComplexReader<Deal.Builder, DealExt.Builder> {

  public DealExtReader() {
    super(
        AdxExt.deal, /*isJsonObject=*/ false,
        "deal_type", "must_bid", "publisher_blocks_overridden");
  }

  @Override protected void read(DealExt.Builder ext, JsonParser par) throws IOException {
    switch (getCurrentName(par)) {
      case "deal_type":
        DealType value  = DealType.forNumber(par.nextIntValue(0));
        if (checkEnum(value)) {
          ext.setDealType(value);
        }
        break;
      case "must_bid":
        par.nextToken();
        ext.setMustBid(par.getValueAsBoolean());
        break;
      case "publisher_blocks_overridden":
        par.nextToken();
        ext.setPublisherBlocksOverridden(par.getValueAsBoolean());
        break;
    }
  }
}
