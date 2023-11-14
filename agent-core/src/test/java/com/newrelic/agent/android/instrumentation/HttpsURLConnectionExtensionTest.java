/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.instrumentation;

import com.newrelic.agent.android.FeatureFlag;
import com.newrelic.agent.android.HttpHeaders;
import com.newrelic.agent.android.distributedtracing.TraceParent;
import com.newrelic.agent.android.distributedtracing.TracePayload;
import com.newrelic.agent.android.distributedtracing.TraceState;

import org.junit.Assert;
import org.junit.Test;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;

public class HttpsURLConnectionExtensionTest {

    final String requestUrl = "https://httpbin.org/status/418";
    final String headerName = "X-Custom-Header-1";

    final String header2Name = "X-Custom-Header-2";

    final String headerValue = "Custom-Value";

    final String header2Value = "Custom-Value-1";

    @Test
    public void testInjectDistributedTracePayload() {
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
        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }

        FeatureFlag.disableFeature(FeatureFlag.DistributedTracing);
    }

    @Test
    public void testHeadersCaptureFromRequestForCustomAttribute() {


        List<String> httpHeaders = new ArrayList<>();
        httpHeaders.add(headerName);
        httpHeaders.add(header2Name);
        HttpHeaders.getInstance().addHttpHeadersAsAttributes(httpHeaders);

        try {
            URL url = new URL(requestUrl);
            HttpsURLConnection urlConnection = (HttpsURLConnection) url.openConnection();
            final HttpsURLConnectionExtension instrumentedConnection = new HttpsURLConnectionExtension(urlConnection);
            instrumentedConnection.setRequestProperty(headerName, headerValue);
            instrumentedConnection.setRequestProperty(header2Name, header2Value);
            TransactionState transactionState = instrumentedConnection.getTransactionState();
            Assert.assertNotNull(transactionState.getTrace());

            Assert.assertEquals(2, transactionState.getParams().size());
            Assert.assertTrue(transactionState.getParams().containsKey(headerName));
            Assert.assertTrue(transactionState.getParams().containsKey(header2Name));
        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }

        FeatureFlag.disableFeature(FeatureFlag.DistributedTracing);
    }

    @Test
    public void testHeadersCaptureFromRequestForCustomAttributeWhenNoHeadersToCapture() {

        HttpHeaders.getInstance().removeHttpHeaderAsAttribute(headerName);
        try {
            URL url = new URL(requestUrl);
            HttpsURLConnection urlConnection = (HttpsURLConnection) url.openConnection();
            final HttpURLConnectionExtension instrumentedConnection = new HttpURLConnectionExtension(urlConnection);
            instrumentedConnection.setRequestProperty(headerName, headerValue);
            TransactionState transactionState = instrumentedConnection.getTransactionState();
            Assert.assertNotNull(transactionState.getTrace());

            Assert.assertEquals(0, transactionState.getParams().size());
        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }

        HttpHeaders.getInstance().addHttpHeaderAsAttribute(headerName);

    }
}