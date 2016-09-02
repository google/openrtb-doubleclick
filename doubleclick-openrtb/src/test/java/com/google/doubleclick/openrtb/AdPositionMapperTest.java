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

import static com.google.common.truth.Truth.assertThat;

import com.google.openrtb.OpenRtb.AdPosition;
import com.google.protos.adx.NetworkBid.BidRequest.AdSlot.SlotVisibility;
import org.junit.Test;

public class AdPositionMapperTest {
  @Test
  public void testMapper() {
    assertThat(AdPositionMapper.toOpenRtb(SlotVisibility.ABOVE_THE_FOLD))
        .isSameAs(AdPosition.ABOVE_THE_FOLD);
    assertThat(AdPositionMapper.toDoubleClick(AdPosition.ABOVE_THE_FOLD))
        .isSameAs(SlotVisibility.ABOVE_THE_FOLD);

    for (AdPosition openrtb : AdPosition.values()) {
      AdPositionMapper.toDoubleClick(openrtb);
    }
    for (SlotVisibility dc : SlotVisibility.values()) {
      AdPositionMapper.toOpenRtb(dc);
    }
  }
}
