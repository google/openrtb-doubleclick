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
import com.google.doubleclick.AdxExt.NativeRequestExt;
import com.google.doubleclick.AdxExt.NativeRequestExt.LayoutType;
import com.google.openrtb.OpenRtb.NativeRequest;
import com.google.openrtb.json.OpenRtbJsonExtComplexReader;
import java.io.IOException;

/**
 * Reader for {@link NativeRequestExt}.
 */
class NativeRequestExtReader
    extends OpenRtbJsonExtComplexReader<NativeRequest.Builder, NativeRequestExt.Builder> {

  public NativeRequestExtReader() {
    super(AdxExt.nativeExt, false,
        "style_id", "style_height", "style_width", "style_layout_type");
  }

  @Override protected void read(NativeRequestExt.Builder ext, JsonParser par) throws IOException {
    switch (getCurrentName(par)) {
      case "style_id":
        ext.setStyleId(par.nextIntValue(0));
        break;
      case "style_height":
        ext.setStyleHeight(par.nextIntValue(0));
        break;
      case "style_width":
        ext.setStyleWidth(par.nextIntValue(0));
        break;
      case "style_layout_type": {
          LayoutType value = LayoutType.valueOf(par.nextIntValue(0));
          if (checkEnum(value)) {
            ext.setStyleLayoutType(value);
          }
        }
        break;
    }
  }
}
