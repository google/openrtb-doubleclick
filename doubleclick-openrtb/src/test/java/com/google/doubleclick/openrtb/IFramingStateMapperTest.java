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

import static java.util.Arrays.asList;
import static org.junit.Assert.assertSame;

import com.google.protos.adx.NetworkBid.BidRequest.AdSlot.IFramingState;

import org.junit.Test;

public class IFramingStateMapperTest {
  @Test
  public void testMapper() {
    assertSame(
        false,
        IFramingStateMapper.toOpenRtb(IFramingState.NO_IFRAME));
    assertSame(
        IFramingState.NO_IFRAME,
        IFramingStateMapper.toDoubleClick(false));
    assertSame(
        IFramingState.UNKNOWN_IFRAME_STATE,
        IFramingStateMapper.toDoubleClick(null));

    for (boolean openrtb : asList(false, true)) {
      IFramingStateMapper.toDoubleClick(openrtb);
    }
    for (IFramingState dc : IFramingState.values()) {
      IFramingStateMapper.toOpenRtb(dc);
    }
  }
}
