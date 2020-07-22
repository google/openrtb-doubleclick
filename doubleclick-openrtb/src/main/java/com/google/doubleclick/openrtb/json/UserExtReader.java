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
import com.google.doubleclick.AdxExt.UserExt;
import com.google.doubleclick.AdxExt.UserExt.ConsentedProvidersSettings;
import com.google.openrtb.OpenRtb.BidRequest.User;
import com.google.openrtb.json.OpenRtbJsonExtComplexReader;
import java.io.IOException;

/**
 * Reader for {@link UserExt}.
 */
class UserExtReader extends OpenRtbJsonExtComplexReader<User.Builder, UserExt.Builder> {

  public UserExtReader() {
    super(AdxExt.user, /*isJsonObject=*/ false, "consented_providers_settings", "consent");
  }

  @Override protected void read(UserExt.Builder ext, JsonParser par) throws IOException {
    switch (getCurrentName(par)) {
      case "consented_providers_settings":
        ext.setConsentedProvidersSettings(readConsentedProvidersSettings(par));
        break;
      case "consent":
        ext.setConsent(par.nextTextValue());
        break;
    }
  }

  public final ConsentedProvidersSettings.Builder readConsentedProvidersSettings(JsonParser par)
      throws IOException {
    ConsentedProvidersSettings.Builder cps = ConsentedProvidersSettings.newBuilder();
    for (startObject(par); endObject(par); par.nextToken()) {
      String fieldName = getCurrentName(par);
      if (par.nextToken() != JsonToken.VALUE_NULL) {
        readConsentedProvidersSettingsField(par, cps, fieldName);
      }
    }
    return cps;
  }

  protected void readConsentedProvidersSettingsField(
      JsonParser par, ConsentedProvidersSettings.Builder cps, String fieldName) throws IOException {
    switch (fieldName) {
      case "consented_providers":
        for (startArray(par); endArray(par); par.nextToken()) {
          cps.addConsentedProviders(par.getLongValue());
        }
        break;
    }
  }
}
