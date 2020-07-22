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
import com.google.doubleclick.AdxExt.AppExt;
import com.google.doubleclick.AdxExt.AppExt.InstalledSdk;
import com.google.doubleclick.AdxExt.AppExt.InstalledSdk.Version;
import com.google.openrtb.json.OpenRtbJsonExtWriter;
import java.io.IOException;

/**
 * Writer for {@link AppExt}.
 */
class AppExtWriter extends OpenRtbJsonExtWriter<AppExt> {

  @Override protected void write(AppExt ext, JsonGenerator gen) throws IOException {
    if (ext.getInstalledSdkCount() != 0) {
      gen.writeArrayFieldStart("installed_sdk");
      for (InstalledSdk sdk : ext.getInstalledSdkList()) {
        writeInstalledSdk(sdk, gen);
      }
      gen.writeEndArray();
    }
  }

  public final void writeInstalledSdk(InstalledSdk sdk, JsonGenerator gen) throws IOException {
    gen.writeStartObject();
    writeInstalledSdkFields(sdk, gen);
    gen.writeEndObject();
    gen.flush();
  }

  protected void writeInstalledSdkFields(InstalledSdk sdk, JsonGenerator gen) throws IOException {
    if (sdk.hasId()) {
      gen.writeStringField("id", sdk.getId());
    }
    if (sdk.hasSdkVersion()) {
      gen.writeFieldName("sdk_version");
      writeVersion(sdk.getSdkVersion(), gen);
    }
    if (sdk.hasAdapterVersion()) {
      gen.writeFieldName("adapter_version");
      writeVersion(sdk.getAdapterVersion(), gen);
    }
  }

  public final void writeVersion(Version version, JsonGenerator gen) throws IOException {
    gen.writeStartObject();
    writeVersionFields(version, gen);
    gen.writeEndObject();
    gen.flush();
  }

  protected void writeVersionFields(Version version, JsonGenerator gen) throws IOException {
    if (version.hasMajor()) {
      gen.writeNumberField("major", version.getMajor());
    }
    if (version.hasMinor()) {
      gen.writeNumberField("minor", version.getMinor());
    }
    if (version.hasMicro()) {
      gen.writeNumberField("micro", version.getMicro());
    }
  }
}
