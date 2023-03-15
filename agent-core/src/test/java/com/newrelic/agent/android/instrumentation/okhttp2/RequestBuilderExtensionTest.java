/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.instrumentation.okhttp2;

import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.Measurements;
import com.newrelic.agent.android.harvest.Harvest;
import com.newrelic.agent.android.harvest.HarvestData;
import com.newrelic.agent.android.test.stub.StubAgentImpl;
import com.newrelic.agent.android.util.Constants;
import com.squareup.okhttp.Request;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class RequestBuilderExtensionTest {
    TestHarvest testHarvest = new TestHarvest();

    @Before
    public void beforeTests() {
        testHarvest = new TestHarvest();
        TestHarvest.setInstance(testHarvest);
        TestHarvest.initialize(new AgentConfiguration());
        Measurements.initialize();
    }

    @After
    public void uninstallAgent() {
        TestHarvest.shutdown();
        Measurements.shutdown();
        StubAgentImpl.uninstall();
    }

    @Test
    public void testBuildRequest() {
        final String responseData = "Hello, World";
        final String requestUrl = "http://www.foo.com/";
        final String appId = "some-app-id";

        final StubAgentImpl agent = StubAgentImpl.install();

        assertEquals(0, agent.getTransactionData().size());

        final Request.Builder builder = new Request.Builder().
                url(requestUrl).
                header(Constants.Network.APPLICATION_ID_HEADER, appId).
                get();

        final Request request = OkHttp2Instrumentation.build(builder);

        assertEquals(request.urlString(), requestUrl);
        assertEquals(request.header(Constants.Network.APPLICATION_ID_HEADER), appId);
        assertNotNull("Cross-Process ID should not be NULL", request.header(Constants.Network.CROSS_PROCESS_ID_HEADER));
        assertEquals(request.header(Constants.Network.CROSS_PROCESS_ID_HEADER), "TEST_CROSS_PROCESS_ID");
    }

    @Test
    public void testDuplicateCATHeaders() {
        final String requestUrl = "http://www.foo.com/";
        final Request.Builder builder = new Request.Builder().
                url(requestUrl).
                header(Constants.Network.CROSS_PROCESS_ID_HEADER, "TEST_CROSS_PROCESS_ID").
                header(Constants.Network.CROSS_PROCESS_ID_HEADER, "TEST_CROSS_PROCESS_ID").
                header(Constants.Network.CROSS_PROCESS_ID_HEADER, "TEST_CROSS_PROCESS_ID").
                get();

        final Request request = OkHttp2Instrumentation.build(builder);

        assertEquals(request.urlString(), requestUrl);
        assertNotNull("Cross-process ID should not be NULL", request.header(Constants.Network.CROSS_PROCESS_ID_HEADER));
        assertEquals(request.header(Constants.Network.CROSS_PROCESS_ID_HEADER), "TEST_CROSS_PROCESS_ID");
        assertEquals("Should record ONE cross-process ID", 1, request.headers().values(Constants.Network.CROSS_PROCESS_ID_HEADER).size());
    }

    public void testSetCrossProcessHeader() {
    }

    private class TestHarvest extends Harvest {

        public TestHarvest() {
        }

        public HarvestData getHarvestData() {
            return harvestData;
        }
    }
}
