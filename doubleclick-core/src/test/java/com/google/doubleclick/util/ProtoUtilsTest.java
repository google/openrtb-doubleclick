/*
 * Copyright 2015 Google Inc. All Rights Reserved.
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

package com.google.doubleclick.util;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.protos.adx.NetworkBid.BidRequest.AdSlot;

import org.junit.Test;

/**
 * Tests for {@link ProtoUtils}.
 */
public class ProtoUtilsTest {

  @Test
  public void testFilter() {
    ImmutableList<AdSlot> adslots = ImmutableList.of(
        AdSlot.newBuilder().setId(1).build(),
        AdSlot.newBuilder().setId(2).build(),
        AdSlot.newBuilder().setId(3).build());
    assertThat(ProtoUtils.filter(adslots, adslot -> adslot.getId() >= 2)).hasSize(2);
    assertThat(ProtoUtils.filter(adslots, adslot -> true)).isSameAs(adslots);
    assertThat(ProtoUtils.filter(adslots, adslot -> false)).isEmpty();
  }
}
