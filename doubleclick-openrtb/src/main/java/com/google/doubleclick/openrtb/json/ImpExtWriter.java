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

import static com.google.openrtb.json.OpenRtbJsonUtils.writeInts;
import static com.google.openrtb.json.OpenRtbJsonUtils.writeLongs;
import static com.google.openrtb.json.OpenRtbJsonUtils.writeStrings;

import com.fasterxml.jackson.core.JsonGenerator;
import com.google.doubleclick.AdxExt.ImpExt;
import com.google.openrtb.json.OpenRtbJsonExtWriter;
import com.google.openrtb.json.OpenRtbJsonUtils;
import java.io.IOException;

/**
 * Writer for {@link ImpExt}.
 */
class ImpExtWriter extends OpenRtbJsonExtWriter<ImpExt> {

  @Override protected void write(ImpExt ext, JsonGenerator gen) throws IOException {
    writeLongs("billing_id", ext.getBillingIdList(), gen);
    writeLongs("publisher_settings_list_id", ext.getPublisherSettingsListIdList(), gen);
    writeInts("allowed_vendor_type", ext.getAllowedVendorTypeList(), gen);
    writeStrings("publisher_parameter", ext.getPublisherParameterList(), gen);
    if (ext.hasDfpAdUnitCode()) {
      gen.writeStringField("dfp_ad_unit_code", ext.getDfpAdUnitCode());
    }
    if (ext.hasIsRewardedInventory()) {
      OpenRtbJsonUtils.writeIntBoolField(
          "is_rewarded_inventory", ext.getIsRewardedInventory(), gen);
    }
  }
}
