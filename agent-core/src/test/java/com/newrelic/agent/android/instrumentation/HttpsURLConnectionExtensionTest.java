/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.instrumentation;

import com.newrelic.agent.android.FeatureFlag;
import com.newrelic.agent.android.distributedtracing.TraceParent;
import com.newrelic.agent.android.distributedtracing.TracePayload;
import com.newrelic.agent.android.distributedtracing.TraceState;

import org.junit.Assert;
import org.junit.Test;

import java.net.URL;
import java.util.List;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;

public class HttpsURLConnectionExtensionTest {

    final String requestUrl = "https://httpbin.org/status/418";

    @Test
    public void testInjectDistributedTracePayload(){
        FeatureFlag.enableFeature(FeatureFlag.DistributedTracing);

        try {
            URL url = new URL(requestUrl);
            HttpsURLConnection urlConnection = (HttpsURLConnection) url.openConnection();
            final HttpsURLConnectionExtension instrumentedConnection = new HttpsURLConnectionExtension(urlConnection);

            TransactionState transactionState = instrumentedConnection.getTransactionState();
            Assert.assertNotNull(transactionState.getTrace());

            Map<String, List<String>> requestPayload = instrumentedConnection.getRequestProperties();
            Assert.assertTrue(requestPayload.containsKey(TracePayload.TRACE_PAYLOAD_HEADER));
            Assert.assertTrue(requestPayload.containsKey(TraceState.TRACE_STATE_HEADER));
            Assert.assertTrue(requestPayload.containsKey(TraceParent.TRACE_PARENT_HEADER));
        } catch (IllegalStateException e) {
            Assert.fail(e.getMessage());
        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }

        FeatureFlag.disableFeature(FeatureFlag.DistributedTracing);
    }
}