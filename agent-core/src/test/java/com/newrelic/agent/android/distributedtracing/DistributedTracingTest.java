/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.distributedtracing;

import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.FeatureFlag;
import com.newrelic.agent.android.analytics.AnalyticsAttribute;
import com.newrelic.agent.android.analytics.AnalyticsControllerImpl;
import com.newrelic.agent.android.analytics.AnalyticsEvent;
import com.newrelic.agent.android.analytics.NetworkEventController;
import com.newrelic.agent.android.harvest.HttpTransaction;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.logging.ConsoleAgentLog;
import com.newrelic.agent.android.test.mock.Providers;
import com.newrelic.agent.android.test.stub.StubAgentImpl;
import com.newrelic.agent.android.test.stub.StubAnalyticsAttributeStore;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

public class DistributedTracingTest {

    private AnalyticsControllerImpl controller;
    private AgentConfiguration config;
    private DistributedTracing instance;

    @Before
    public void preTest() {
        AgentLogManager.setAgentLog(new ConsoleAgentLog());
        AgentLogManager.getAgentLog().setLevel(0);
        config = new AgentConfiguration();
        config.setEnableAnalyticsEvents(true);
        config.setAnalyticsAttributeStore(new StubAnalyticsAttributeStore());
        controller = (AnalyticsControllerImpl) AnalyticsControllerImpl.getInstance();
        AnalyticsControllerImpl.shutdown();
    }

    @Before
    public void defaultFeatures() {
        FeatureFlag.enableFeature(FeatureFlag.DistributedTracing);
        instance = new DistributedTracing();
    }

    @Test
    public void defaultEnabledFeatures() throws Exception {
        Assert.assertTrue("Distributed tracing is enabled by default", FeatureFlag.featureEnabled(FeatureFlag.DistributedTracing));
    }

    @Test
    public void resetFeatureFlags() throws Exception {
        Assert.assertTrue("DistributedTracing is enabled by default", FeatureFlag.featureEnabled(FeatureFlag.DistributedTracing));
        FeatureFlag.disableFeature(FeatureFlag.DistributedTracing);
        Assert.assertFalse("Distributed tracing is now disabled", FeatureFlag.featureEnabled(FeatureFlag.DistributedTracing));
        FeatureFlag.resetFeatures();
        Assert.assertTrue("Distributed tracing is now enabled", FeatureFlag.featureEnabled(FeatureFlag.DistributedTracing));
    }

    @Test
    public void createHttpErrorEventWithDistributedTracing() throws Exception {
        AnalyticsControllerImpl.initialize(config, new StubAgentImpl());

        HttpTransaction txn = Providers.provideHttpTransaction();
        txn.setStatusCode(418);
        txn.setErrorCode(-1100);

        NetworkEventController.createHttpErrorEvent(txn);
        Collection<AnalyticsEvent> events = controller.getEventManager().getQueuedEvents();
        Assert.assertEquals("Queued event collection should have a size of 1.", 1, events.size());

        Iterator<AnalyticsEvent> eventIt = events.iterator();
        Collection<AnalyticsAttribute> eventAttrs = eventIt.next().getAttributeSet();
        AnalyticsAttribute statusCode = getAttributeByName(eventAttrs, AnalyticsAttribute.STATUS_CODE_ATTRIBUTE);
        Assert.assertNull("Transaction error code should not exist on http error event.", getAttributeByName(eventAttrs, AnalyticsAttribute.NETWORK_ERROR_CODE_ATTRIBUTE));
        Assert.assertNotNull("Transaction status code should exist.", statusCode);
        Assert.assertTrue(statusCode.getDoubleValue() == 418f);
        Assert.assertFalse(getAttributeByName(eventAttrs, DistributedTracing.NR_GUID_ATTRIBUTE).getStringValue().isEmpty());
    }

    @Test
    public void createNetworkFailureEventWithDistributedTracing() throws Exception {
        AnalyticsControllerImpl.initialize(config, new StubAgentImpl());
        controller.getEventManager().empty();

        HttpTransaction txn = Providers.provideHttpTransaction();
        txn.setErrorCode(-1100);

        NetworkEventController.createNetworkFailureEvent(txn);
        Collection<AnalyticsEvent> events = controller.getEventManager().getQueuedEvents();
        Assert.assertEquals("Queued event collection should have a size of 1.", 1, events.size());

        Iterator<AnalyticsEvent> eventIt = events.iterator();
        Collection<AnalyticsAttribute> eventAttrs = eventIt.next().getAttributeSet();
        AnalyticsAttribute errorCode = getAttributeByName(eventAttrs, AnalyticsAttribute.NETWORK_ERROR_CODE_ATTRIBUTE);
        Assert.assertNull("Transaction status code should not exist on network failure event.", getAttributeByName(eventAttrs, AnalyticsAttribute.STATUS_CODE_ATTRIBUTE));
        Assert.assertNotNull("Transaction error code should exist.", errorCode);
        Assert.assertTrue(errorCode.getDoubleValue() == -1100f);
        Assert.assertFalse(getAttributeByName(eventAttrs, DistributedTracing.NR_GUID_ATTRIBUTE).getStringValue().isEmpty());
    }

    @Test
    public void createHttpErrorEventInvalidURLWithDistributedTracing() throws Exception {
        AnalyticsControllerImpl.initialize(config, new StubAgentImpl());
        controller.getEventManager().empty();

        HttpTransaction txn = Providers.provideHttpRequestError();
        txn.setUrl("ThisIsNotAURL");

        NetworkEventController.createHttpErrorEvent(txn);
        Collection<AnalyticsEvent> events = controller.getEventManager().getQueuedEvents();
        Assert.assertEquals("Queued event collection should have a size of 1.", 1, events.size());

        Iterator<AnalyticsEvent> eventIt = events.iterator();
        Collection<AnalyticsAttribute> eventAttrs = eventIt.next().getAttributeSet();

        AnalyticsAttribute requestDomain = getAttributeByName(eventAttrs, AnalyticsAttribute.REQUEST_DOMAIN_ATTRIBUTE);
        Assert.assertNull("Transaction provided an invalid URL and should have no domain attribute.", requestDomain);

        AnalyticsAttribute requestPath = getAttributeByName(eventAttrs, AnalyticsAttribute.REQUEST_PATH_ATTRIBUTE);
        Assert.assertNull("Transaction provided an invalid URL and should have no path attribute.", requestPath);

        AnalyticsAttribute requestUrl = getAttributeByName(eventAttrs, AnalyticsAttribute.REQUEST_URL_ATTRIBUTE);
        Assert.assertEquals("Transaction URL should match attribute requestUrl.", "ThisIsNotAURL", requestUrl.getStringValue());

        Assert.assertFalse(getAttributeByName(eventAttrs, DistributedTracing.NR_GUID_ATTRIBUTE).getStringValue().isEmpty());
    }

    @Test
    public void createNetworkFailureEventInvalidURLWithDistributedTracing() throws Exception {
        AnalyticsControllerImpl.initialize(config, new StubAgentImpl());
        controller.getEventManager().empty();

        HttpTransaction txn = Providers.provideHttpRequestFailure();
        txn.setUrl("ThisIsNotAURL");

        NetworkEventController.createNetworkFailureEvent(txn);
        Collection<AnalyticsEvent> events = controller.getEventManager().getQueuedEvents();
        Assert.assertEquals("Queued event collection should have a size of 1.", 1, events.size());

        Iterator<AnalyticsEvent> eventIt = events.iterator();
        Collection<AnalyticsAttribute> eventAttrs = eventIt.next().getAttributeSet();

        AnalyticsAttribute requestDomain = getAttributeByName(eventAttrs, AnalyticsAttribute.REQUEST_DOMAIN_ATTRIBUTE);
        Assert.assertNull("Transaction provided an invalid URL and should have no domain attribute.", requestDomain);

        AnalyticsAttribute requestPath = getAttributeByName(eventAttrs, AnalyticsAttribute.REQUEST_PATH_ATTRIBUTE);
        Assert.assertNull("Transaction provided an invalid URL and should have no path attribute.", requestPath);

        AnalyticsAttribute requestUrl = getAttributeByName(eventAttrs, AnalyticsAttribute.REQUEST_URL_ATTRIBUTE);
        Assert.assertEquals("Transaction URL should match attribute requestUrl.", "ThisIsNotAURL", requestUrl.getStringValue());

        Assert.assertFalse(getAttributeByName(eventAttrs, DistributedTracing.NR_GUID_ATTRIBUTE).getStringValue().isEmpty());
    }

    @Test
    public void testCreateNetworkEventWithDistributedTracing() throws Exception {
        AnalyticsControllerImpl.initialize(config, new StubAgentImpl());
        HttpTransaction txn = Providers.provideHttpTransaction();

        NetworkEventController.createNetworkRequestEvent(txn);
        Collection<AnalyticsEvent> events = controller.getEventManager().getQueuedEvents();
        Assert.assertEquals("Queued event collection should have a size of 1", 1, events.size());

        Iterator<AnalyticsEvent> eventIt = events.iterator();
        AnalyticsEvent event = eventIt.next();
        Assert.assertEquals("Should contain network request event", AnalyticsEvent.EVENT_TYPE_MOBILE_REQUEST, event.getEventType());

        Collection<AnalyticsAttribute> eventAttrs = event.getAttributeSet();

        Assert.assertEquals("Should contain correct number of attributes", 18, eventAttrs.size());

        Assert.assertNotNull("Should contain distributed transaction guid", getAttributeByName(eventAttrs, DistributedTracing.NR_GUID_ATTRIBUTE));

        Assert.assertNotNull("Should contain status code", getAttributeByName(eventAttrs, AnalyticsAttribute.STATUS_CODE_ATTRIBUTE));
        Assert.assertNotNull("Should contain request domain", getAttributeByName(eventAttrs, AnalyticsAttribute.REQUEST_DOMAIN_ATTRIBUTE));
        Assert.assertNotNull("Should contain path", getAttributeByName(eventAttrs, AnalyticsAttribute.REQUEST_PATH_ATTRIBUTE));
        Assert.assertNotNull("Should contain request url", getAttributeByName(eventAttrs, AnalyticsAttribute.REQUEST_URL_ATTRIBUTE));
        Assert.assertNotNull("Should contain connection type", getAttributeByName(eventAttrs, AnalyticsAttribute.CONNECTION_TYPE_ATTRIBUTE));
        Assert.assertNotNull("Should contain request method", getAttributeByName(eventAttrs, AnalyticsAttribute.REQUEST_METHOD_ATTRIBUTE));
        Assert.assertNotNull("Should contain response time", getAttributeByName(eventAttrs, AnalyticsAttribute.RESPONSE_TIME_ATTRIBUTE));
        Assert.assertNotNull("Should contain bytes sent", getAttributeByName(eventAttrs, AnalyticsAttribute.BYTES_SENT_ATTRIBUTE));
        Assert.assertNotNull("Should contain bytes received", getAttributeByName(eventAttrs, AnalyticsAttribute.BYTES_RECEIVED_ATTRIBUTE));
    }

    @Test
    public void testCreateNetworkErrorEventWithDistributedTracing() throws Exception {
        FeatureFlag.enableFeature(FeatureFlag.DistributedTracing);
        AnalyticsControllerImpl.initialize(config, new StubAgentImpl());
        HttpTransaction txn = Providers.provideHttpRequestError();

        txn.setBytesSent(-1);
        txn.setStatusCode(404);
        txn.setResponseBody("404 NOT FOUND");

        FeatureFlag.enableFeature(FeatureFlag.HttpResponseBodyCapture);

        NetworkEventController.createHttpErrorEvent(txn);
        Collection<AnalyticsEvent> events = controller.getEventManager().getQueuedEvents();
        Assert.assertEquals("Queued event collection should have a size of 1", 1, events.size());

        Iterator<AnalyticsEvent> eventIt = events.iterator();
        AnalyticsEvent event = eventIt.next();
        Assert.assertEquals("Should contain network request error event", AnalyticsEvent.EVENT_TYPE_MOBILE_REQUEST_ERROR, event.getEventType());

        Collection<AnalyticsAttribute> eventAttrs = event.getAttributeSet();

        Assert.assertNotNull("Should contain distributed transaction guid", getAttributeByName(eventAttrs, DistributedTracing.NR_GUID_ATTRIBUTE));

        Assert.assertNotNull("Should contain status code", getAttributeByName(eventAttrs, AnalyticsAttribute.STATUS_CODE_ATTRIBUTE));
        Assert.assertNotNull("Should contain request domain", getAttributeByName(eventAttrs, AnalyticsAttribute.REQUEST_DOMAIN_ATTRIBUTE));
        Assert.assertNotNull("Should contain path", getAttributeByName(eventAttrs, AnalyticsAttribute.REQUEST_PATH_ATTRIBUTE));
        Assert.assertNotNull("Should contain request url", getAttributeByName(eventAttrs, AnalyticsAttribute.REQUEST_URL_ATTRIBUTE));
        Assert.assertNotNull("Should contain connection type", getAttributeByName(eventAttrs, AnalyticsAttribute.CONNECTION_TYPE_ATTRIBUTE));
        Assert.assertNotNull("Should contain request method", getAttributeByName(eventAttrs, AnalyticsAttribute.REQUEST_METHOD_ATTRIBUTE));
        Assert.assertNotNull("Should contain response time", getAttributeByName(eventAttrs, AnalyticsAttribute.RESPONSE_TIME_ATTRIBUTE));
        Assert.assertNotNull("Should contain bytes sent", getAttributeByName(eventAttrs, AnalyticsAttribute.BYTES_SENT_ATTRIBUTE));
        Assert.assertNotNull("Should contain bytes received", getAttributeByName(eventAttrs, AnalyticsAttribute.BYTES_RECEIVED_ATTRIBUTE));
        Assert.assertNotNull("Should contain response body", getAttributeByName(eventAttrs, AnalyticsAttribute.RESPONSE_BODY_ATTRIBUTE));
    }

    @Test
    public void testDistributedTraceListener() {
        TraceListener listener = new TraceListener() {
            @Override
            public void onTraceCreated(Map<String, String> requestContext) {
                Assert.assertNotNull(requestContext);
                Assert.assertTrue(requestContext.containsKey("url"));
                Assert.assertTrue(requestContext.containsKey("threadId"));
            }

            @Override
            public void onSpanCreated(Map<String, String> requestContext) {
                Assert.assertNotNull(requestContext);
                Assert.assertTrue(requestContext.containsKey("traceId"));
            }
        };

        FeatureFlag.disableFeature(FeatureFlag.DistributedTracing);
        instance.setTraceListener(listener);
        Assert.assertNotNull(instance.traceListener.get());
        Assert.assertNotEquals(instance.traceListener.get(), listener);

        FeatureFlag.enableFeature(FeatureFlag.DistributedTracing);
        instance.setTraceListener(null);
        Assert.assertNotNull(instance.traceListener.get());
        Assert.assertEquals(instance.traceListener.get(), instance);

        instance.setTraceListener(listener);
        Assert.assertNotNull(instance.traceListener.get());
        Assert.assertEquals(instance.traceListener.get(), listener);
    }

    @Test
    public void testUUIDGenerator() {
        String longGuid = DistributedTracing.generateRandomBytes(32);
        String shortGuid = DistributedTracing.generateRandomBytes(16);
        Assert.assertEquals(32, longGuid.length());
        Assert.assertNotEquals(TraceContext.INVALID_TRACE_ID, longGuid);
        Assert.assertEquals(16, shortGuid.length());
        Assert.assertNotEquals(TraceContext.INVALID_SPAN_ID, longGuid);
    }

    static AnalyticsAttribute getAttributeByName(Collection<AnalyticsAttribute> attributes, String name) {
        for (AnalyticsAttribute eventAttr : attributes) {
            if (eventAttr.getName().equalsIgnoreCase(name)) {
                return eventAttr;
            }
        }
        return null;
    }

}
