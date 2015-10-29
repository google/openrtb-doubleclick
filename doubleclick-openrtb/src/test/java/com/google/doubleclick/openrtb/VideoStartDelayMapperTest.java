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

import com.google.openrtb.OpenRtb.BidRequest.Imp.Video.VideoStartDelay;

import org.junit.Test;

public class VideoStartDelayMapperTest {
  @Test
  public void testMapper() {
    assertThat(VideoStartDelayMapper.toDoubleClick(VideoStartDelay.GENERIC_MID_ROLL_VALUE))
        .isEqualTo(1);
    assertThat(VideoStartDelayMapper.toDoubleClick(333)).isEqualTo(333);
    assertThat(VideoStartDelayMapper.toOpenRtb(-1))
        .isSameAs(VideoStartDelay.GENERIC_POST_ROLL_VALUE);
    assertThat(VideoStartDelayMapper.toOpenRtb(333)).isEqualTo(333);

    for (VideoStartDelay openrtb : VideoStartDelay.values()) {
      VideoStartDelayMapper.toDoubleClick(openrtb.getNumber());
    }
    for (int dc : new int[]{ 0, -1, 1 }) {
      VideoStartDelayMapper.toOpenRtb(dc);
    }
  }
}
