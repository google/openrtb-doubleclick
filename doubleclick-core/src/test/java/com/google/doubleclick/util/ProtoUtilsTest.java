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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
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
    assertEquals(2, Iterables.size(ProtoUtils.filter(adslots, new Predicate<AdSlot>() {
      @Override public boolean apply(AdSlot adslot) {
        return adslot.getId() >= 2;
      }})));
    assertSame(adslots, ProtoUtils.filter(adslots, Predicates.<AdSlot>alwaysTrue()));
    assertTrue(Iterables.isEmpty(ProtoUtils.filter(adslots, Predicates.<AdSlot>alwaysFalse())));
  }
}
