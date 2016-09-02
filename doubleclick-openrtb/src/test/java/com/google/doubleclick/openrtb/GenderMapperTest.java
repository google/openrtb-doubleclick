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

import com.google.openrtb.Gender;
import com.google.protos.adx.NetworkBid.BidRequest.UserDemographic;
import org.junit.Test;

public class GenderMapperTest {
  @Test
  public void testMapper() {
    assertThat(GenderMapper.toOpenRtb(UserDemographic.Gender.MALE)).isSameAs(Gender.MALE);
    assertThat(GenderMapper.toDoubleClick(Gender.MALE)).isSameAs(UserDemographic.Gender.MALE);

    for (Gender openrtb : Gender.values()) {
      GenderMapper.toDoubleClick(openrtb);
    }
    for (UserDemographic.Gender dc : UserDemographic.Gender.values()) {
      GenderMapper.toOpenRtb(dc);
    }
  }
}
