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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

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
      assertEquals(e1, e1);
      assertNotEquals(e1, e2);
      assertNotEquals(e1, "");
      e1.hashCode();
    }

    assertEquals("%%CLICK_URL_ESC%%", DoubleClickMacros.CLICK_URL_ESC.key());
    assertTrue(DoubleClickMacros.CACHEBUSTER.htmlSupported());
    assertTrue(DoubleClickMacros.CACHEBUSTER.videoSupported());
  }
}
