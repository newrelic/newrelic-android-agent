/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.analytics;

import static com.newrelic.agent.android.analytics.AnalyticsAttributeTests.getAttributeByName;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;

import com.newrelic.agent.android.test.mock.Providers;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class EventTransformAdapterTest {

    EventManager eventMgr;
    EventTransformAdapter adapter;
    NetworkRequestEvent requestEvent;
    NetworkRequestErrorEvent requestErrorEvent;

    @Before
    public void setUp() throws Exception {
        eventMgr = Mockito.spy(new EventManagerImpl());
        adapter = Mockito.spy(new EventTransformAdapter());
        requestEvent = NetworkRequestEvent.createNetworkEvent(Providers.provideHttpTransaction());
        requestErrorEvent = NetworkRequestErrorEvent.createHttpErrorEvent(Providers.provideHttpRequestError());

        adapter.onStart(eventMgr);
    }

    @Test
    public void onEventAdded() {
        Assert.assertTrue(adapter.onEventAdded(requestEvent));
        Mockito.verify(adapter.em, times(1)).onEventAdded(requestEvent);
    }

    @Test
    public void onEventOverflow() {
        Assert.assertFalse(adapter.onEventOverflow(requestEvent));
        Mockito.verify(adapter.em, times(1)).onEventOverflow(requestEvent);
    }

    @Test
    public void onEventEvicted() {
        Assert.assertTrue(adapter.onEventEvicted(requestEvent));
        Mockito.verify(adapter.em, times(1)).onEventEvicted(requestEvent);
    }

    @Test
    public void onEventQueueSizeExceeded() {
        adapter.onEventQueueSizeExceeded(1000);
        Mockito.verify(adapter.em, times(1)).onEventQueueSizeExceeded(1000);
    }

    @Test
    public void onEventQueueTimeExceeded() {
        adapter.onEventQueueTimeExceeded(1000);
        Mockito.verify(adapter.em, times(1)).onEventQueueTimeExceeded(1000);
    }

    @Test
    public void onEventFlush() {
        adapter.onEventFlush();
        Mockito.verify(adapter, times(1)).onEventFlush();
    }

    @Test
    public void onStart() {
        adapter.onStart(eventMgr);
        Mockito.verify(adapter.em, atLeastOnce()).onStart(eventMgr);
        Assert.assertNotNull(adapter.em);
        Assert.assertEquals(adapter.em, eventMgr);
    }

    @Test
    public void onShutdown() {
        adapter.onShutdown();
        Assert.assertNull(adapter.em);
    }

    @Test
    public void withAttributeTransform() {
        adapter.withAttributeTransform(AnalyticsAttribute.REQUEST_URL_ATTRIBUTE, null);
        Assert.assertFalse(adapter.attributeTransforms.containsKey(AnalyticsAttribute.REQUEST_URL_ATTRIBUTE));

        adapter.withAttributeTransform(AnalyticsAttribute.REQUEST_URL_ATTRIBUTE, new HashMap<String, String>());
        Assert.assertFalse(adapter.attributeTransforms.containsKey(AnalyticsAttribute.REQUEST_URL_ATTRIBUTE));

        adapter.withAttributeTransform(AnalyticsAttribute.REQUEST_URL_ATTRIBUTE, new HashMap<String, String>() {{
            put(/*match:*/ "^http(s{0,1}):\\/\\/(http).*/(\\d)\\d*", /*replace:*/ "https://httpbin.org/status/418");
        }});
        Assert.assertTrue(adapter.attributeTransforms.containsKey(AnalyticsAttribute.REQUEST_URL_ATTRIBUTE));
    }

    @Test
    public void testUrls() {
        final String mask = "dead-beef";

        adapter.withAttributeTransform(AnalyticsAttribute.REQUEST_URL_ATTRIBUTE,
                readTransforms("/NetworkRequestTransforms.txt"));

        Assert.assertTrue(adapter.attributeTransforms.containsKey(AnalyticsAttribute.REQUEST_URL_ATTRIBUTE));
        try (InputStream is = EventTransformAdapter.class.getResourceAsStream("/NetworkRequests.txt")) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String url;
                while ((url = reader.readLine()) != null) {
                    AnalyticsAttribute attr = getAttributeByName(requestEvent.getAttributeSet(), AnalyticsAttribute.REQUEST_URL_ATTRIBUTE);
                    attr.setStringValue(url.strip());

                    adapter.onEventTransform(requestEvent);
                    System.out.println("Transformed [" + url + "]->[" + attr.getStringValue() + "]");

                    Assert.assertTrue(attr.getStringValue().contains(mask));
                    if (url.contains("payments")) {
                        Assert.assertTrue(attr.getStringValue().contains("repayments"));
                    }
                    if (url.contains("postbank.de")) {
                        Assert.assertTrue(attr.getStringValue().contains("postbank.gr"));
                    }
                }
            }
        } catch (IOException e) {
            Assert.fail("URL resources not loaded");
        }
    }

    Map<String, String> readTransforms(final String resPath) {
        HashMap<String, String> transforms = new HashMap<String, String>();
        try (InputStream is = EventTransformAdapter.class.getResourceAsStream(resPath)) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] kvPair = line.split(",");
                    System.out.println("Match: [" + kvPair[0].strip() + "] Replace: [" + kvPair[1].strip() + "]");
                    transforms.put(kvPair[0].strip(), kvPair[1].strip());
                }
            }
        } catch (IOException e) {
            Assert.fail("URL resources not loaded");
        }
        return transforms;
    }
}