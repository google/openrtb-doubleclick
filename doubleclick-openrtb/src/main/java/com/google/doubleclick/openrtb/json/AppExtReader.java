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

import static com.google.openrtb.json.OpenRtbJsonUtils.endArray;
import static com.google.openrtb.json.OpenRtbJsonUtils.endObject;
import static com.google.openrtb.json.OpenRtbJsonUtils.getCurrentName;
import static com.google.openrtb.json.OpenRtbJsonUtils.startArray;
import static com.google.openrtb.json.OpenRtbJsonUtils.startObject;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.google.doubleclick.AdxExt;
import com.google.doubleclick.AdxExt.AppExt;
import com.google.doubleclick.AdxExt.AppExt.InstalledSdk;
import com.google.doubleclick.AdxExt.AppExt.InstalledSdk.Version;
import com.google.openrtb.OpenRtb.BidRequest.App;
import com.google.openrtb.json.OpenRtbJsonExtComplexReader;
import java.io.IOException;

/**
 * Reader for {@link AppExt}.
 */
class AppExtReader extends OpenRtbJsonExtComplexReader<App.Builder, AppExt.Builder> {

  public AppExtReader() {
    super(AdxExt.app, /*isJsonObject=*/ false, "installed_sdk");
  }

  @Override protected void read(AppExt.Builder ext, JsonParser par) throws IOException {
    switch (getCurrentName(par)) {
      case "installed_sdk":
        for (startArray(par); endArray(par); par.nextToken()) {
          ext.addInstalledSdk(readInstalledSdk(par));
        }
        break;
    }
  }

  public final InstalledSdk.Builder readInstalledSdk(JsonParser par) throws IOException {
    InstalledSdk.Builder sdk = InstalledSdk.newBuilder();
    for (startObject(par); endObject(par); par.nextToken()) {
      String fieldName = getCurrentName(par);
      if (par.nextToken() != JsonToken.VALUE_NULL) {
        readInstalledSdkField(par, sdk, fieldName);
      }
    }
    return sdk;
  }

  protected void readInstalledSdkField(JsonParser par, InstalledSdk.Builder sdk, String fieldName)
      throws IOException {
    switch (fieldName) {
      case "id":
        sdk.setId(par.getText());
        break;
      case "sdk_version":
        sdk.setSdkVersion(readVersion(par));
        break;
      case "adapter_version":
        sdk.setAdapterVersion(readVersion(par));
        break;
    }
  }

  public final Version.Builder readVersion(JsonParser par) throws IOException {
    Version.Builder version = Version.newBuilder();
    for (startObject(par); endObject(par); par.nextToken()) {
      String fieldName = getCurrentName(par);
      if (par.nextToken() != JsonToken.VALUE_NULL) {
        readVersionField(par, version, fieldName);
      }
    }
    return version;
  }

  protected void readVersionField(JsonParser par, Version.Builder version, String fieldName)
      throws IOException {
    switch (fieldName) {
      case "major":
        version.setMajor(par.getIntValue());
        break;
      case "minor":
        version.setMinor(par.getIntValue());
        break;
      case "micro":
        version.setMicro(par.getIntValue());
        break;
    }
  }
}
