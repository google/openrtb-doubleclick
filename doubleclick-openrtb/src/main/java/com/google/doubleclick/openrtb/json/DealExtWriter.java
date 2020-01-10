/*
 * Copyright 2019 Google Inc. All Rights Reserved.
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
import static com.google.openrtb.json.OpenRtbJsonUtils.writeIntBoolField;

import com.fasterxml.jackson.core.JsonGenerator;
import com.google.doubleclick.AdxExt.DealExt;
import com.google.openrtb.json.OpenRtbJsonExtWriter;
import java.io.IOException;

/** Writer for {@link DealExt}. */
class DealExtWriter extends OpenRtbJsonExtWriter<DealExt> {

  @Override
  protected void write(DealExt ext, JsonGenerator gen) throws IOException {
    if (ext.hasDealType()) {
      writeEnumField("deal_type", ext.getDealType(), gen);
    }
    if (ext.hasMustBid()) {
      writeIntBoolField("must_bid", ext.getMustBid(), gen);
    }
    if (ext.hasPublisherBlocksOverridden()) {
      writeIntBoolField("publisher_blocks_overridden", ext.getPublisherBlocksOverridden(), gen);
    }
  }
}
