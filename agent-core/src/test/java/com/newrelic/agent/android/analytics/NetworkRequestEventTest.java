/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.analytics;

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

import static com.newrelic.agent.android.analytics.AnalyticsAttributeTests.getAttributeByName;

public class NetworkRequestEventTest {
    private HttpTransaction httpTransaction;

    @Before
    public void setUp() throws Exception {
        httpTransaction = Providers.provideHttpTransaction();
    }

    @After
    public void tearDown() throws Exception {
        FeatureFlag.disableFeature(FeatureFlag.DistributedTracing);
    }

    @Test
    public void testDefaultAttributes() throws Exception {
        Set<AnalyticsAttribute> attributes = NetworkRequestEvent.createDefaultAttributeSet(httpTransaction);

        Assert.assertNotNull(getAttributeByName(attributes, AnalyticsAttribute.REQUEST_DOMAIN_ATTRIBUTE));
        Assert.assertNotNull(getAttributeByName(attributes, AnalyticsAttribute.REQUEST_PATH_ATTRIBUTE));
        Assert.assertNotNull(getAttributeByName(attributes, AnalyticsAttribute.REQUEST_URL_ATTRIBUTE));
        Assert.assertNotNull(getAttributeByName(attributes, AnalyticsAttribute.CONNECTION_TYPE_ATTRIBUTE));
        Assert.assertNotNull(getAttributeByName(attributes, AnalyticsAttribute.REQUEST_METHOD_ATTRIBUTE));
        Assert.assertNotNull(getAttributeByName(attributes, AnalyticsAttribute.RESPONSE_TIME_ATTRIBUTE));
        Assert.assertNull(getAttributeByName(attributes, AnalyticsAttribute.BYTES_SENT_ATTRIBUTE));
        Assert.assertNotNull(getAttributeByName(attributes, AnalyticsAttribute.BYTES_RECEIVED_ATTRIBUTE));
    }

    @Test
    public void testCreateNetworkEvent() throws Exception {
        NetworkRequestEvent networkRequestEvent = NetworkRequestEvent.createNetworkEvent(httpTransaction);
        Collection<AnalyticsAttribute> attributes = networkRequestEvent.getAttributeSet();
        Assert.assertNotNull(getAttributeByName(attributes, AnalyticsAttribute.RESPONSE_TIME_ATTRIBUTE));
        Assert.assertNotNull(getAttributeByName(attributes, AnalyticsAttribute.STATUS_CODE_ATTRIBUTE));
        Assert.assertNotNull(getAttributeByName(attributes, AnalyticsAttribute.BYTES_SENT_ATTRIBUTE));
        Assert.assertNotNull(getAttributeByName(attributes, AnalyticsAttribute.BYTES_RECEIVED_ATTRIBUTE));
    }

    @Test
    public void testDistributedTracedRequest() {
        FeatureFlag.enableFeature(FeatureFlag.DistributedTracing);
        httpTransaction = Providers.provideHttpTransaction();
        NetworkRequestEvent networkRequestEvent = NetworkRequestEvent.createNetworkEvent(httpTransaction);
        Collection<AnalyticsAttribute> attributes = networkRequestEvent.getAttributeSet();
        Assert.assertNotNull(getAttributeByName(attributes, DistributedTracing.NR_GUID_ATTRIBUTE));
        Assert.assertNotNull(getAttributeByName(attributes, DistributedTracing.NR_TRACE_ID_ATTRIBUTE));
        Assert.assertEquals(httpTransaction.getTraceContext().getTraceId(), getAttributeByName(attributes, DistributedTracing.NR_TRACE_ID_ATTRIBUTE).valueAsString());
        Assert.assertEquals(httpTransaction.getTraceContext().getTracePayload().getSpanId(), getAttributeByName(attributes, DistributedTracing.NR_GUID_ATTRIBUTE).valueAsString());
    }
}