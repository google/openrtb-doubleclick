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

package com.google.doubleclick.openrtb;

import static org.junit.Assert.assertEquals;

import com.google.openrtb.OpenRtb.BidRequest.Imp.Video.VideoPlaybackMethod;
import com.google.protos.adx.NetworkBid;

import org.junit.Test;

public class VideoPlaybackMethodMapperTest {
  @Test
  public void testMapper() {
    assertEquals(
        NetworkBid.BidRequest.Video.VideoPlaybackMethod.AUTO_PLAY_SOUND_ON,
        VideoPlaybackMethodMapper.toDoubleClick(VideoPlaybackMethod.AUTO_PLAY_SOUND_ON));
    assertEquals(
        VideoPlaybackMethod.AUTO_PLAY_SOUND_ON,
        VideoPlaybackMethodMapper.toOpenRtb(
            NetworkBid.BidRequest.Video.VideoPlaybackMethod.AUTO_PLAY_SOUND_ON));

    for (VideoPlaybackMethod openrtb : VideoPlaybackMethod.values()) {
      VideoPlaybackMethodMapper.toDoubleClick(openrtb);
    }
    for (NetworkBid.BidRequest.Video.VideoPlaybackMethod dc :
        NetworkBid.BidRequest.Video.VideoPlaybackMethod.values()) {
      VideoPlaybackMethodMapper.toOpenRtb(dc);
    }
  }
}
