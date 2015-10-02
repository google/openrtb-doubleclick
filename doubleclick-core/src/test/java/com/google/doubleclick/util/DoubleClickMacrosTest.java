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

package com.google.doubleclick.util;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

/**
 * Tests for {@link DoubleClickMacros}.
 */
public class DoubleClickMacrosTest {

  @Test
  public void testEnum() {
    DoubleClickMacros[] e = DoubleClickMacros.values();
    for (int i = 1; i < e.length; ++i) {
      DoubleClickMacros e1 = e[i - 1];
      DoubleClickMacros e2 = e[i];
      assertThat(e1).isEqualTo(e1);
      assertThat(e2).isNotEqualTo(e1);
      assertThat("").isNotEqualTo(e1);
      e1.hashCode();
    }

    assertThat(DoubleClickMacros.CLICK_URL_ESC.key()).isEqualTo("%%CLICK_URL_ESC%%");
    assertThat(DoubleClickMacros.CACHEBUSTER.htmlSupported()).isTrue();
    assertThat(DoubleClickMacros.CACHEBUSTER.videoSupported()).isTrue();
  }
}
