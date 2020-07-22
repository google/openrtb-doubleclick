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

import static com.google.openrtb.json.OpenRtbJsonUtils.writeLongs;

import com.fasterxml.jackson.core.JsonGenerator;
import com.google.doubleclick.AdxExt.UserExt;
import com.google.doubleclick.AdxExt.UserExt.ConsentedProvidersSettings;
import com.google.openrtb.json.OpenRtbJsonExtWriter;
import java.io.IOException;

/**
 * Writer for {@link UserExt}.
 */
class UserExtWriter extends OpenRtbJsonExtWriter<UserExt> {

  @Override protected void write(UserExt ext, JsonGenerator gen) throws IOException {
    if (ext.hasConsentedProvidersSettings()) {
      gen.writeFieldName("consented_providers_settings");
      writeConsentedProvidersSettings(ext.getConsentedProvidersSettings(), gen);
    }
    if (ext.hasConsent()) {
      gen.writeStringField("consent", ext.getConsent());
    }
  }

  public final void writeConsentedProvidersSettings(
      ConsentedProvidersSettings cps, JsonGenerator gen) throws IOException {
    gen.writeStartObject();
    writeConsentedProvidersSettingsFields(cps, gen);
    gen.writeEndObject();
    gen.flush();
  }

  protected void writeConsentedProvidersSettingsFields(
      ConsentedProvidersSettings cps, JsonGenerator gen) throws IOException {
    writeLongs("consented_providers", cps.getConsentedProvidersList(), gen);
  }
}
