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
import com.google.doubleclick.AdxExt.ImpExt;
import com.google.doubleclick.AdxExt.ImpExt.AmpAdRequirementType;
import com.google.doubleclick.AdxExt.ImpExt.BuyerGeneratedRequestData;
import com.google.doubleclick.AdxExt.ImpExt.BuyerGeneratedRequestData.SourceApp;
import com.google.doubleclick.AdxExt.ImpExt.OpenBidding;
import com.google.openrtb.OpenRtb.BidRequest.Imp;
import com.google.openrtb.json.OpenRtbJsonExtComplexReader;
import java.io.IOException;

/**
 * Reader for {@link ImpExt}.
 */
class ImpExtReader extends OpenRtbJsonExtComplexReader<Imp.Builder, ImpExt.Builder> {

  public ImpExtReader() {
    super(
        AdxExt.imp, /*isJsonObject=*/ false,
        "billing_id", "publisher_settings_list_id", "allowed_vendor_type", "publisher_parameter",
        "dfp_ad_unit_code", "is_rewarded_inventory", "ampad", "buyer_generated_request_data",
        "excluded_creatives", "open_bidding", "allowed_restricted_category");
  }

  @Override protected void read(ImpExt.Builder ext, JsonParser par) throws IOException {
    switch (getCurrentName(par)) {
      case "billing_id":
        for (startArray(par); endArray(par); par.nextToken()) {
          ext.addBillingId(par.getValueAsLong());
        }
        break;
      case "publisher_settings_list_id":
        for (startArray(par); endArray(par); par.nextToken()) {
          ext.addPublisherSettingsListId(par.getValueAsLong());
        }
        break;
      case "allowed_vendor_type":
        for (startArray(par); endArray(par); par.nextToken()) {
          ext.addAllowedVendorType(par.getIntValue());
        }
        break;
      case "publisher_parameter":
        for (startArray(par); endArray(par); par.nextToken()) {
          ext.addPublisherParameter(par.getText());
        }
        break;
      case "dfp_ad_unit_code":
        ext.setDfpAdUnitCode(par.nextTextValue());
        break;
      case "is_rewarded_inventory":
        par.nextToken();
        ext.setIsRewardedInventory(par.getValueAsBoolean());
        break;
      case "ampad": {
          AmpAdRequirementType value = AmpAdRequirementType.forNumber(par.nextIntValue(0));
          if (checkEnum(value)) {
            ext.setAmpad(value);
          }
        }
        break;
      case "buyer_generated_request_data":
        for (startArray(par); endArray(par); par.nextToken()) {
          ext.addBuyerGeneratedRequestData(readBuyerGeneratedRequestData(par));
        }
        break;
      case "excluded_creatives":
        for (startArray(par); endArray(par); par.nextToken()) {
          ext.addExcludedCreatives(readExcludedCreatives(par));
        }
        break;
      case "open_bidding":
        ext.setOpenBidding(readOpenBidding(par));
        break;
      case "allowed_restricted_category":
        for (startArray(par); endArray(par); par.nextToken()) {
          ext.addAllowedRestrictedCategory(par.getIntValue());
        }
        break;
    }
  }

  public final OpenBidding.Builder readOpenBidding(JsonParser par) throws IOException {
    OpenBidding.Builder obid = OpenBidding.newBuilder();
    for (startObject(par); endObject(par); par.nextToken()) {
      String fieldName = getCurrentName(par);
      if (par.nextToken() != JsonToken.VALUE_NULL) {
        readOpenBiddingField(par, obid, fieldName);
      }
    }
    return obid;
  }

  protected void readOpenBiddingField(JsonParser par, OpenBidding.Builder obid, String fieldName)
      throws IOException {
    switch (fieldName) {
      case "is_open_bidding":
        obid.setIsOpenBidding(par.getValueAsBoolean());
        break;
    }
  }

  public final BuyerGeneratedRequestData.Builder readBuyerGeneratedRequestData(JsonParser par)
      throws IOException {
    BuyerGeneratedRequestData.Builder data = BuyerGeneratedRequestData.newBuilder();
    for (startObject(par); endObject(par); par.nextToken()) {
      String fieldName = getCurrentName(par);
      if (par.nextToken() != JsonToken.VALUE_NULL) {
        readBuyerGeneratedRequestDataField(par, data, fieldName);
      }
    }
    return data;
  }

  protected void readBuyerGeneratedRequestDataField(
      JsonParser par, BuyerGeneratedRequestData.Builder data, String fieldName)
          throws IOException {
    switch (fieldName) {
      case "source_app":
        data.setSourceApp(readSourceApp(par));
        break;
      case "data":
        data.setData(par.getText());
        break;
    }
  }

  public final SourceApp.Builder readSourceApp(JsonParser par) throws IOException {
    SourceApp.Builder data = SourceApp.newBuilder();
    for (startObject(par); endObject(par); par.nextToken()) {
      String fieldName = getCurrentName(par);
      if (par.nextToken() != JsonToken.VALUE_NULL) {
        readSourceAppField(par, data, fieldName);
      }
    }
    return data;
  }

  protected void readSourceAppField(JsonParser par, SourceApp.Builder app, String fieldName)
      throws IOException {
    switch (fieldName) {
      case "id":
        app.setId(par.getText());
        break;
    }
  }

  public final ImpExt.ExcludedCreative.Builder readExcludedCreatives(JsonParser par)
          throws IOException {
    ImpExt.ExcludedCreative.Builder exCreat = ImpExt.ExcludedCreative.newBuilder();
    for (startObject(par); endObject(par); par.nextToken()) {
      String fieldName = getCurrentName(par);
      if (par.nextToken() != JsonToken.VALUE_NULL) {
        readExcludedCreativeField(par, exCreat, fieldName);
      }
    }
    return exCreat;
  }

  protected void readExcludedCreativeField(
      JsonParser par, ImpExt.ExcludedCreative.Builder exCreat, String fieldName)
          throws IOException {
    switch (fieldName) {
      case "buyer_creative_id":
        exCreat.setBuyerCreativeId(par.getText());
        break;
    }
  }
}
