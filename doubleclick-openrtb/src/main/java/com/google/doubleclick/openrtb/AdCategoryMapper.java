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

import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.CharStreams;
import com.google.openrtb.OpenRtb.ContentCategory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Maps between AdX's ad (product, restricted and sensitive) categories,
 * and OpenRTB's IAB-based {@link ContentCategory}.
 */
public class AdCategoryMapper {
  private static final Logger logger = LoggerFactory.getLogger(AdCategoryMapper.class);
  private static ImmutableSet<ContentCategory>[] dcToOpenrtb;
  private static ImmutableMultimap<ContentCategory, Integer> openrtbToDc;
  private static ImmutableBiMap<ContentCategory, String> openrtbToName;

  static {
    Pattern pattern = Pattern.compile("(\\d+)\\|.*\\|(\\d+)\\|.*");
    ImmutableMultimap.Builder<ContentCategory, Integer> data = ImmutableMultimap.builder();
    Map<ContentCategory, String> names = new HashMap<>();

    try (InputStream isMetadata = AdCategoryMapper.class.getResourceAsStream(
        "/adx-openrtb/category-mapping-smartclip.txt")) {
      for (String line : CharStreams.readLines(new InputStreamReader(isMetadata))) {
        Matcher matcher = pattern.matcher(line);

        if (matcher.matches()) {
          int openrtbCode = Integer.parseInt(matcher.group(1));
          int dcCode = Integer.parseInt(matcher.group(2));
          ContentCategory openrtbCat = ContentCategory.valueOf(openrtbCode);
          if (openrtbCat == null) {
            logger.warn("Ignoring unknown ContentCategory code: {}", line);
          } else {
            data.put(openrtbCat, dcCode);
            if (!names.containsKey(openrtbCat)) {
              names.put(openrtbCat, openrtbCat.name().replace("_", "-"));
            }
          }
        }
      }

      openrtbToDc = data.build();
      openrtbToName = ImmutableBiMap.copyOf(names);
      AdCategoryMapper.dcToOpenrtb = MapperUtil.multimapIntToSets(openrtbToDc.inverse());
    } catch (IOException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  public static ImmutableCollection<ContentCategory> toOpenRtb(int dc) {
    return MapperUtil.get(dcToOpenrtb, dc);
  }

  public static ImmutableCollection<Integer> toDoubleClick(ContentCategory openrtb) {
    return openrtbToDc.get(openrtb);
  }

  public static ImmutableBiMap<ContentCategory, String> getNameMap() {
    return openrtbToName;
  }

  public static ImmutableSet<ContentCategory> toOpenRtb(Collection<Integer> dcList) {
    ImmutableSet.Builder<ContentCategory> openrtbSet = ImmutableSet.builder();

    for (int dc : dcList) {
      openrtbSet.addAll(toOpenRtb(dc));
    }

    return openrtbSet.build();
  }

  public static ImmutableSet<Integer> toDoubleClick(Collection<ContentCategory> openrtbList) {
    ImmutableSet.Builder<Integer> dcSet = ImmutableSet.builder();

    for (ContentCategory openrtb : openrtbList) {
      dcSet.addAll(toDoubleClick(openrtb));
    }

    return dcSet.build();
  }
}
