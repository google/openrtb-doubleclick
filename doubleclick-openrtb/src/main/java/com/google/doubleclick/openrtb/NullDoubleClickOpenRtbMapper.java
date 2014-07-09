/*
 * Copyright 2014 Google Inc. All Rights Reserved.
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

package com.google.doubleclick.openrtb;

import com.google.doubleclick.Doubleclick;
import com.google.openrtb.OpenRtb;
import com.google.openrtb.mapper.OpenRtbMapper;

import javax.annotation.Nullable;

/**
 * Dummy implementation of {@link OpenRtbMapper}, maps all messages to {@code null}.
 */
public class NullDoubleClickOpenRtbMapper
    implements OpenRtbMapper<Doubleclick.BidRequest, Doubleclick.BidResponse.Builder> {
  public static final NullDoubleClickOpenRtbMapper INSTANCE = new NullDoubleClickOpenRtbMapper();

  private NullDoubleClickOpenRtbMapper() {
  }

  @Override
  public Doubleclick.BidResponse.Builder toNative(
      OpenRtb.BidRequest request, OpenRtb.BidResponse response) {
    return Doubleclick.BidResponse.newBuilder();
  }

  @Override
  public @Nullable OpenRtb.BidRequest toOpenRtb(Doubleclick.BidRequest dcRequest) {
    return null;
  }
}
