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

import com.google.openrtb.OpenRtb.BidRequest.AuctionType;
import com.google.protos.adx.NetworkBid.BidRequest.AdSlot.MatchingAdData.DirectDeal.DealType;

import javax.annotation.Nullable;

/**
 * Maps between AdX's {@link DealType} and OpenRTB's {@link AuctionType}.
 */
public class DealTypeMapper {
  public static @Nullable AuctionType toOpenRtb(DealType dc) {
    switch (dc) {
      case PREFERRED_DEAL:
        return AuctionType.FIXED_PRICE;
      case PRIVATE_AUCTION:
        return null;  // Mapping is SECOND_PRICE => OpenRTB's default
      case UNKNOWN_DEAL_TYPE:
        return null;
    }
    return null;
  }

  public static @Nullable DealType toDoubleClick(AuctionType openrtb) {
    switch (openrtb) {
      case FIRST_PRICE:
      case SECOND_PRICE:
        return DealType.PRIVATE_AUCTION;
      case FIXED_PRICE:
        return DealType.PREFERRED_DEAL;
    }
    return null;
  }
}
