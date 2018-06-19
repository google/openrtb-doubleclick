package com.google.doubleclick.openrtb.json;

import static com.google.common.truth.Truth.assertThat;

import com.google.doubleclick.AdxExt;
import com.google.doubleclick.AdxExt.EventTrackerExt;
import com.google.doubleclick.AdxExt.EventTrackerExt.Context;
import com.google.openrtb.OpenRtb.EventTrackingMethod;
import com.google.openrtb.OpenRtb.EventType;
import com.google.openrtb.OpenRtb.NativeRequest;
import com.google.openrtb.OpenRtb.NativeResponse;
import com.google.openrtb.OpenRtb.NativeResponse.EventTracker;
import com.google.openrtb.OpenRtb.NativeResponse.Link;
import com.google.openrtb.json.OpenRtbJsonFactory;
import java.io.IOException;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tests for the AdX/OpenRTB JSON support.
 */
public class AdxExtNativeJsonTest {
  private static final Logger logger = LoggerFactory.getLogger(AdxExtNativeJsonTest.class);

  @Test
  public void testResponse() throws IOException {
    testResponse(NativeResponse.newBuilder()
        .setLink(Link.newBuilder().setUrl("http://url"))
        .addEventtrackers(EventTracker.newBuilder()
            .setEvent(EventType.IMPRESSION)
            .setMethod(EventTrackingMethod.IMG)
            .setExtension(AdxExt.eventtrackers, EventTrackerExt.newBuilder()
                .addContext(Context.OMID)
                .setVerificationParameters("vparam1")
                .setVendorKey("vkey1")
                .build()))
        .build());
  }

  static void testRequest(NativeRequest req) throws IOException {
    OpenRtbJsonFactory jsonFactory = AdxExtUtils.registerAdxExt(OpenRtbJsonFactory.create());
    String jsonReq = jsonFactory.newNativeWriter().writeNativeRequest(req);
    logger.info(jsonReq);
    jsonFactory.setStrict(false).newNativeWriter().writeNativeRequest(req);
    NativeRequest req2 = jsonFactory.newNativeReader().readNativeRequest(jsonReq);
    assertThat(req2).isEqualTo(req);
    jsonFactory.setStrict(false).newNativeReader().readNativeRequest(jsonReq);
  }

  static String testResponse(NativeResponse resp) throws IOException {
    OpenRtbJsonFactory jsonFactory = AdxExtUtils.registerAdxExt(OpenRtbJsonFactory.create());
    String jsonResp = jsonFactory.newNativeWriter().writeNativeResponse(resp);
    logger.info(jsonResp);
    jsonFactory.setStrict(false).newNativeWriter().writeNativeResponse(resp);
    NativeResponse resp2 = jsonFactory.newNativeReader().readNativeResponse(jsonResp);
    assertThat(resp2).isEqualTo(resp);
    jsonFactory.setStrict(false).newNativeReader().readNativeResponse(jsonResp);
    return jsonResp;
  }
}
