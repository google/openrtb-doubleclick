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

import static org.junit.Assert.assertEquals;

import com.google.protobuf.ByteString;

import org.junit.Test;

public class MapperUtilTest {

  @Test
  public void testDump() {
    ByteString data = ByteString.copyFrom(new byte[] {
        0x00, 0x01, 0x0F, 0x10, 0x1F, 0x7f, (byte) 0x80, (byte) 0xFF
    });
    String str = MapperUtil.toHexString(data);
    assertEquals("00010F101F7F80FF", str);
    ByteString data2 = MapperUtil.toByteString(str);
    assertEquals(data, data2);
  }
}
