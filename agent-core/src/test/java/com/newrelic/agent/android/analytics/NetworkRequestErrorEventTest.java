/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.analytics;

import static com.newrelic.agent.android.analytics.AnalyticsAttributeTests.getAttributeByName;

import com.newrelic.agent.android.FeatureFlag;
import com.newrelic.agent.android.distributedtracing.DistributedTracing;
import com.newrelic.agent.android.harvest.HttpTransaction;
import com.newrelic.agent.android.test.mock.Providers;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Collection;
import java.util.Set;

public class NetworkRequestErrorEventTest {
    private HttpTransaction httpTransaction;

    @Before
    public void setUp() throws Exception {
        httpTransaction = Providers.provideHttpTransaction();
        httpTransaction.setAppData(Providers.APP_DATA);
    }

    @After
    public void tearDown() throws Exception {
        FeatureFlag.disableFeature(FeatureFlag.DistributedTracing);
    }

    @Test
    public void testDefaultAttributes() throws Exception {
        Set<AnalyticsAttribute> attributes = NetworkRequestErrorEvent.createErrorAttributeSet(httpTransaction);

        Assert.assertNotNull(getAttributeByName(attributes, AnalyticsAttribute.REQUEST_DOMAIN_ATTRIBUTE));
        Assert.assertNotNull(getAttributeByName(attributes, AnalyticsAttribute.REQUEST_PATH_ATTRIBUTE));
        Assert.assertNotNull(getAttributeByName(attributes, AnalyticsAttribute.REQUEST_URL_ATTRIBUTE));
        Assert.assertNotNull(getAttributeByName(attributes, AnalyticsAttribute.CONNECTION_TYPE_ATTRIBUTE));
        Assert.assertNotNull(getAttributeByName(attributes, AnalyticsAttribute.REQUEST_METHOD_ATTRIBUTE));
        Assert.assertNotNull(getAttributeByName(attributes, AnalyticsAttribute.RESPONSE_TIME_ATTRIBUTE));
        Assert.assertNotNull(getAttributeByName(attributes, AnalyticsAttribute.BYTES_RECEIVED_ATTRIBUTE));
    }

    @Test
    public void testErrorAttributes() throws Exception {
        Set<AnalyticsAttribute> attributes = NetworkRequestErrorEvent.createErrorAttributeSet(httpTransaction);

        Assert.assertNotNull(getAttributeByName(attributes, AnalyticsAttribute.RESPONSE_BODY_ATTRIBUTE));
        Assert.assertNotNull(getAttributeByName(attributes, AnalyticsAttribute.APP_DATA_ATTRIBUTE));
        Assert.assertNotNull(getAttributeByName(attributes, AnalyticsAttribute.CONTENT_TYPE_ATTRIBUTE));
    }

    @Test
    public void testHttpErrorEvent() throws Exception {
        NetworkRequestErrorEvent httpErrorEvent = NetworkRequestErrorEvent.createHttpErrorEvent(Providers.provideHttpRequestError());
        Collection<AnalyticsAttribute> attributes = httpErrorEvent.getAttributeSet();
        Assert.assertNotNull(getAttributeByName(attributes, AnalyticsAttribute.STATUS_CODE_ATTRIBUTE));
    }

    @Test
    public void testNetworkFailureEvent() throws Exception {
        NetworkRequestErrorEvent httpErrorEvent = NetworkRequestErrorEvent.createNetworkFailureEvent(httpTransaction);
        Collection<AnalyticsAttribute> attributes = httpErrorEvent.getAttributeSet();
        Assert.assertNotNull(getAttributeByName(attributes, AnalyticsAttribute.NETWORK_ERROR_CODE_ATTRIBUTE));
    }

    @Test
    public void testResponseBodies() throws Exception {
        StringBuffer superLongResponseBody = new StringBuffer();
        while (superLongResponseBody.length() < 6543) {
            superLongResponseBody.append("blibityBlab");
        }
        httpTransaction.setResponseBody(superLongResponseBody.toString());
        NetworkRequestErrorEvent httpErrorEvent = NetworkRequestErrorEvent.createNetworkFailureEvent(httpTransaction);
        Collection<AnalyticsAttribute> attributes = httpErrorEvent.getAttributeSet();
        AnalyticsAttribute responseBodyAttribute = getAttributeByName(attributes, AnalyticsAttribute.RESPONSE_BODY_ATTRIBUTE);
        Assert.assertNotNull(responseBodyAttribute);
        Assert.assertTrue("Should truncate response body to attribute limit", responseBodyAttribute.getStringValue().length() == AnalyticsAttribute.ATTRIBUTE_VALUE_MAX_LENGTH);
    }

    @Test
    public void testDisabledResponseBodies() throws Exception {
        FeatureFlag.disableFeature(FeatureFlag.HttpResponseBodyCapture);
        NetworkRequestErrorEvent httpErrorEvent = NetworkRequestErrorEvent.createNetworkFailureEvent(httpTransaction);
        Collection<AnalyticsAttribute> attributes = httpErrorEvent.getAttributeSet();
        AnalyticsAttribute responseBodyAttribute = getAttributeByName(attributes, AnalyticsAttribute.RESPONSE_BODY_ATTRIBUTE);
        Assert.assertNotNull("Should include response body attribute", responseBodyAttribute);
        Assert.assertEquals("Should include 'disabled ff' in response body", responseBodyAttribute.getStringValue(), NetworkRequestErrorEvent.DISABLE_FF_RESPONSE);

        FeatureFlag.enableFeature(FeatureFlag.HttpResponseBodyCapture);
        httpErrorEvent = NetworkRequestErrorEvent.createNetworkFailureEvent(httpTransaction);
        attributes = httpErrorEvent.getAttributeSet();
        responseBodyAttribute = getAttributeByName(attributes, AnalyticsAttribute.RESPONSE_BODY_ATTRIBUTE);
        Assert.assertNotNull("Should include response body attribute", responseBodyAttribute);
        Assert.assertNotEquals("Should not include 'disabled ff' in response body", responseBodyAttribute.getStringValue(), NetworkRequestErrorEvent.DISABLE_FF_RESPONSE);
    }

    @Test
    public void testDistributedTracedRequest() {
        FeatureFlag.enableFeature(FeatureFlag.DistributedTracing);
        httpTransaction = Providers.provideHttpTransaction();
        NetworkRequestErrorEvent httpErrorEvent = NetworkRequestErrorEvent.createNetworkFailureEvent(httpTransaction);
        Collection<AnalyticsAttribute> attributes = httpErrorEvent.getAttributeSet();
        Assert.assertNotNull(getAttributeByName(attributes, DistributedTracing.NR_GUID_ATTRIBUTE));
        Assert.assertNotNull(getAttributeByName(attributes, DistributedTracing.NR_TRACE_ID_ATTRIBUTE));
        Assert.assertEquals(httpTransaction.getTraceContext().getTraceId(), getAttributeByName(attributes, DistributedTracing.NR_TRACE_ID_ATTRIBUTE).valueAsString());
        Assert.assertEquals(httpTransaction.getTraceContext().getTracePayload().getSpanId(), getAttributeByName(attributes, DistributedTracing.NR_GUID_ATTRIBUTE).valueAsString());
    }

}