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

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.doubleclick.util.DoubleClickMetadata;
import com.google.openrtb.OpenRtb.ContentCategory;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import org.junit.Test;

public class AdCategoryMapperTest {
  private static final boolean SLOW = Boolean.getBoolean("slowTests");

  @Test
  public void testMapper() {
    assertThat(AdCategoryMapper.toDoubleClick(ImmutableList.of(ContentCategory.IAB1_4), null))
        .containsExactly(10106, 13760);
    assertThat(AdCategoryMapper.toOpenRtb(ImmutableList.of(10106), null))
        .containsExactly(ContentCategory.IAB1_4);
  }

  @Test
  public void testFullMapping() {
    if (!SLOW) {
      return;
    }

    DoubleClickMetadata metadata =
        new DoubleClickMetadata(new DoubleClickMetadata.URLConnectionTransport());

    SortedMap<Integer, String> categories = new TreeMap<>();
    categories.putAll(metadata.productCategories());
    categories.putAll(metadata.restrictedCategories());
    categories.putAll(metadata.sensitiveCategories());

    System.err.println("Unmapped AdX categories:");
    for (Map.Entry<Integer, String> dc : categories.entrySet()) {
      Set<ContentCategory> openrtb = AdCategoryMapper.toOpenRtb(dc.getKey());
      if (openrtb.isEmpty()) {
        reportUnmappedAdX(dc);
      }
    }

    System.err.println("Unmapped OpenRTB categories:");
    for (ContentCategory openrtb : ContentCategory.values()) {
      Set<Integer> dc = AdCategoryMapper.toDoubleClick(openrtb);
      if (dc.isEmpty()) {
        System.err.println("** " + openrtb);
      }
    }
  }

  private static void reportUnmappedAdX(
      Entry<Integer, String> dc) {
    Splitter splitter = Splitter.on('/').omitEmptyStrings().trimResults();
    String dcStr = dc.getKey() + "|" + dc.getValue();
    System.err.println("** " + dcStr);

    for (Map.Entry<ContentCategory, String> od : AdCategoryMapper.openrtbDesc().entrySet()) {
      boolean similar =
          od.getValue().contains(dc.getValue())
          || dc.getValue().contains(od.getValue());
      if (!similar) {
        for (int dcOther : AdCategoryMapper.toDoubleClick(od.getKey())) {
          for (String dcDesc : splitter.split(AdCategoryMapper.dcDesc().get(dcOther))) {
            if (dcDesc != null
                && (dcDesc.contains(dc.getValue()) || dc.getValue().contains(dcDesc))) {
              similar = true;
              break;
            }
          }
        }
      }
      if (similar) {
        System.err.println(od.getKey().getNumber() + "|" + od.getValue() + "|" + dcStr);
      }
    }
  }
}
