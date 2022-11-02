/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.analytics;

import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.logging.ConsoleAgentLog;
import com.newrelic.agent.android.test.mock.Providers;

import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.HashMap;

public class NetworkEventTransformerTest {

    private NetworkEventTransformer adapter;
    private HashMap<String, String> transforms = new HashMap<String, String>() {{
        put("^http(s{0,1}):\\/\\/(http).*/(\\d)\\d*", null);
    }};

    @BeforeClass
    public static void beforeClass() throws Exception {
        AgentLogManager.setAgentLog(new ConsoleAgentLog());
        AgentLogManager.getAgentLog().setLevel(AgentLog.DEBUG);
    }

    @Before
    public void setUp() throws Exception {
        adapter = new NetworkEventTransformer(transforms);
    }

    @Test
    public void onEventAdded() {
        NetworkRequestEvent requestEvent = NetworkRequestEvent.createNetworkEvent(Providers.provideHttpTransaction());
        NetworkRequestErrorEvent requestErrorEvent = NetworkRequestErrorEvent.createHttpErrorEvent(Providers.provideHttpRequestError());
        CustomEvent customEvent = new CustomEvent("custom", requestEvent.attributeSet);

        AnalyticsAttribute analyticsAttribute = AnalyticsAttributeTests.getAttributeByName(requestEvent.attributeSet, AnalyticsAttribute.REQUEST_URL_ATTRIBUTE);
        Assert.assertNotNull(analyticsAttribute);
        Assert.assertEquals("https://httpstat.us/200", analyticsAttribute.getStringValue());

        adapter.onEventAdded(requestEvent);
        analyticsAttribute = AnalyticsAttributeTests.getAttributeByName(requestEvent.attributeSet, AnalyticsAttribute.REQUEST_URL_ATTRIBUTE);
        Assert.assertEquals("http*://****stat.us/*00", analyticsAttribute.getStringValue());

        adapter.onEventAdded(requestErrorEvent);
        analyticsAttribute = AnalyticsAttributeTests.getAttributeByName(requestErrorEvent.attributeSet, AnalyticsAttribute.REQUEST_URL_ATTRIBUTE);
        Assert.assertEquals("http*://****stat.us/*00", analyticsAttribute.getStringValue());

        adapter.onEventAdded(customEvent);
        analyticsAttribute = AnalyticsAttributeTests.getAttributeByName(customEvent.attributeSet, AnalyticsAttribute.REQUEST_URL_ATTRIBUTE);
        Assert.assertEquals("https://httpstat.us/200", analyticsAttribute.getStringValue());
    }

    @Test
    public void withTransformPairs() {
        final NetworkRequestEvent requestEvent = NetworkRequestEvent.createNetworkEvent(Providers.provideHttpTransaction());
        final NetworkRequestErrorEvent requestErrorEvent = NetworkRequestErrorEvent.createHttpErrorEvent(Providers.provideHttpRequestError());
        final HashMap<String, String> matchReplaceStrings = new HashMap<String, String>() {{
            put(/*pattern:*/ "^http(s{0,1}):\\/\\/(http).*/(\\d)\\d*", /*replace:*/ "https://httpbin.org/status/418");
            put(/*pattern:*/ "/ciam/[^\"]*/", /*replace:*/ "/ciam/*");
            put(/*pattern:*/ "/securities-accounts/[^\\/]*/security-orders/", /*replace:*/ "/securities-accounts/:securitiesAccountId/security-orders");
            put(/*pattern:*/ "/security-portfolios/\\d{10}.{2}/", /*replace:*/ "/security-portfolios/:portfolioId");
            put(/*pattern:*/ "/payments/direct-debit-transfer/partners/[^\\/]*/direct-debit-transfers/[^\\/]+/", /*replace:*/ "/payments/direct-debit-transfer/partners/:partnerId/direct-debit-transfers/:transactionId");
            put(/*pattern:*/ "/payments/direct-debit-transfer/partners/[^\\/]*/products/[^/]*/direct-debit-transfers/validation/", /*replace:*/ "/payments/direct-debit-transfer/partners/:partnerId/products/:productId/direct-debit-transfers/validation");
            put(/*pattern:*/ "/payments/direct-debit-transfer/partners/[^\\/]*/direct-debit-transfers/[^\\/]*/validation/", /*replace:*/"/payments/direct-debit-transfer/partners/:partnerId/direct-debit-transfers/:transactionId/validation");
        }};

        adapter = new NetworkEventTransformer(matchReplaceStrings);
        Assert.assertFalse(adapter.attributeTransforms.isEmpty());

        adapter.onEventAdded(requestEvent);
        AnalyticsAttribute analyticsAttribute = AnalyticsAttributeTests.getAttributeByName(requestEvent.attributeSet, AnalyticsAttribute.REQUEST_URL_ATTRIBUTE);
        Assert.assertNotNull(analyticsAttribute);
        Assert.assertEquals("https://httpbin.org/status/418", analyticsAttribute.getStringValue());

        adapter.onEventAdded(requestErrorEvent);
        analyticsAttribute = AnalyticsAttributeTests.getAttributeByName(requestEvent.attributeSet, AnalyticsAttribute.REQUEST_URL_ATTRIBUTE);
        Assert.assertNotNull(analyticsAttribute);
        Assert.assertEquals("https://httpbin.org/status/418", analyticsAttribute.getStringValue());
    }
}