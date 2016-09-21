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

import com.google.openrtb.OpenRtb.PlaybackMethod;
import com.google.protos.adx.NetworkBid;

/**
 * Maps between AdX's {@code playback_method} and OpenRTB's {@code playbackmethod}.
 */
public class PlaybackMethodMapper {
  public static PlaybackMethod toOpenRtb(NetworkBid.BidRequest.Video.VideoPlaybackMethod dc) {
    switch (dc) {
      case AUTO_PLAY_SOUND_OFF:
        return PlaybackMethod.AUTO_PLAY_SOUND_OFF;
      case AUTO_PLAY_SOUND_ON:
        return PlaybackMethod.AUTO_PLAY_SOUND_ON;
      case CLICK_TO_PLAY:
        return PlaybackMethod.CLICK_TO_PLAY;
      case METHOD_UNKNOWN:
      default:
        return null;
    }
  }

  public static NetworkBid.BidRequest.Video.VideoPlaybackMethod toDoubleClick(
      PlaybackMethod openrtb) {
    switch (openrtb) {
      case AUTO_PLAY_SOUND_OFF:
        return NetworkBid.BidRequest.Video.VideoPlaybackMethod.AUTO_PLAY_SOUND_OFF;
      case AUTO_PLAY_SOUND_ON:
        return NetworkBid.BidRequest.Video.VideoPlaybackMethod.AUTO_PLAY_SOUND_ON;
      case CLICK_TO_PLAY:
        return NetworkBid.BidRequest.Video.VideoPlaybackMethod.CLICK_TO_PLAY;
      case MOUSE_OVER:
      default:
        return null;
    }
  }
}
