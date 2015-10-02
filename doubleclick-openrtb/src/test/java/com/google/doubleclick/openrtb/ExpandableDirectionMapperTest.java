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

import com.google.common.collect.ImmutableList;
import com.google.openrtb.OpenRtb.BidRequest.Imp.Banner.ExpandableDirection;

import org.junit.Test;

public class ExpandableDirectionMapperTest {
  @Test
  public void testMapper() {
    ImmutableList<Integer> dcAll = ImmutableList.of(13, 14, 15, 16, 17, 18, 19, 20, 25, 26, 27);
    ImmutableList<ExpandableDirection> openrtbAll = ImmutableList.of(
        ExpandableDirection.LEFT, ExpandableDirection.RIGHT,
        ExpandableDirection.UP, ExpandableDirection.DOWN);
    assertThat(ExpandableDirectionMapper.toOpenRtb(ImmutableList.<Integer>of()))
        .containsExactlyElementsIn(openrtbAll);
    assertThat(ExpandableDirectionMapper.toOpenRtb(dcAll)).isEmpty();
    assertThat(ExpandableDirectionMapper.toDoubleClick(openrtbAll)).isEmpty();
    assertThat(ExpandableDirectionMapper.toDoubleClick(ImmutableList.<ExpandableDirection>of()))
        .containsExactlyElementsIn(dcAll);
  }
}
