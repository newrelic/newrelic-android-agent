/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.harvest;

import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.Measurements;
import com.newrelic.agent.android.TaskQueue;
import com.newrelic.agent.android.metric.Metric;
import com.newrelic.agent.android.stats.StatsEngine;
import com.newrelic.agent.android.test.stub.StubAgentImpl;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

@RunWith(JUnit4.class)
public class AgentHealthExceptionTests {
    final static String TESTING = "testing";

    @Test
    public void testConstructors() {
        final String threadName = Thread.currentThread().getName();
        final Exception fail = new Exception(TESTING);
        final StackTraceElement[] list = {new StackTraceElement("Class", "method", "file.java", 1)};
        final HashMap<String, String> extras = new HashMap<String, String>();

        AgentHealthException exception = new AgentHealthException(fail);
        Assert.assertEquals(fail.getClass().getName(), exception.getExceptionClass());
        Assert.assertEquals(TESTING, exception.getMessage());
        Assert.assertEquals(threadName, exception.getThreadName());
        Assert.assertEquals(1, exception.getCount());
        Assert.assertEquals(fail.getStackTrace().length, exception.getStackTrace().length);
        Assert.assertNull(exception.getExtras());
        Assert.assertNotNull(exception.asJsonArray());

        exception = new AgentHealthException(fail, threadName);
        Assert.assertEquals(fail.getClass().getName(), exception.getExceptionClass());
        Assert.assertEquals(TESTING, exception.getMessage());
        Assert.assertEquals(threadName, exception.getThreadName());
        Assert.assertEquals(1, exception.getCount());
        Assert.assertEquals(fail.getStackTrace().length, exception.getStackTrace().length);
        Assert.assertNull(exception.getExtras());
        Assert.assertNotNull(exception.asJsonArray());

        exception = new AgentHealthException(TESTING, TESTING, TESTING, list);
        Assert.assertEquals(TESTING, exception.getExceptionClass());
        Assert.assertEquals(TESTING, exception.getMessage());
        Assert.assertEquals(TESTING, exception.getThreadName());
        Assert.assertEquals(1, exception.getCount());
        Assert.assertEquals(list.length, exception.getStackTrace().length);
        Assert.assertNull(exception.getExtras());
        Assert.assertNotNull(exception.asJsonArray());

        exception = new AgentHealthException(TESTING, TESTING, TESTING, list, extras);
        Assert.assertEquals(TESTING, exception.getExceptionClass());
        Assert.assertEquals(TESTING, exception.getMessage());
        Assert.assertEquals(TESTING, exception.getThreadName());
        Assert.assertEquals(1, exception.getCount());
        Assert.assertEquals(list.length, exception.getStackTrace().length);
        Assert.assertEquals(extras.size(), exception.getExtras().size());
        Assert.assertNotNull(exception.asJsonArray());
    }

    @Test
    public void testIncrement() {
        AgentHealthException exception = new AgentHealthException(new Exception());
        Assert.assertEquals(1, exception.getCount());

        exception.increment();
        Assert.assertEquals(2, exception.getCount());

        exception.increment(2);
        Assert.assertEquals(4, exception.getCount());
    }

    @Test
    public void testAggregation() {
        final AgentHealthException exception1 = new AgentHealthException(new Exception(TESTING + 1));
        final AgentHealthException exception2 = new AgentHealthException(new Exception(TESTING + 2));

        final AgentHealthExceptions exceptions = new AgentHealthExceptions();

        exceptions.add(exception1);
        Assert.assertEquals(1, exceptions.getAgentHealthExceptions().size());

        exceptions.add(exception1);
        Assert.assertEquals(1, exceptions.getAgentHealthExceptions().size());

        exceptions.add(exception2);
        Assert.assertEquals(2, exceptions.getAgentHealthExceptions().size());

        exceptions.add(exception2);
        Assert.assertEquals(2, exceptions.getAgentHealthExceptions().size());

        Assert.assertFalse(exceptions.isEmpty());

        Assert.assertNotNull(exceptions.asJsonObject());

        exceptions.clear();
        Assert.assertTrue(exceptions.isEmpty());
    }

    @Test
    public void testNoticeException() {
        final TestHarvest testHarvest = new TestHarvest();
        TestHarvest.setInstance(testHarvest);
        TestHarvest.initialize(new AgentConfiguration());
        Measurements.initialize();
        final StubAgentImpl agent = StubAgentImpl.install();
        final AgentHealthException exception = new AgentHealthException(new Exception(TESTING));
        final HarvestData harvestData = testHarvest.getHarvestData();

        Assert.assertEquals(0, harvestData.getAgentHealth().asJsonArray().size());

        AgentHealth.noticeException(exception);

        TaskQueue.synchronousDequeue();

        Assert.assertEquals(1, harvestData.getAgentHealth().asJsonArray().size());

        TestHarvest.shutdown();
        Measurements.shutdown();
        StubAgentImpl.uninstall();
    }

    @Test
    public void testNullExceptions() throws Exception {
        try {
            AgentHealth.noticeException((Exception) null);
            AgentHealth.noticeException((AgentHealthException) null);
            AgentHealth.noticeException((AgentHealthException) null, null);
        } catch (Exception e) {
            Assert.fail("Should not throw: noticeException should handle null values.");
        }
    }

    @Test
    public void testNullExceptionKeyNames() throws Exception {
        AgentHealthExceptions agentHealthExceptions = new AgentHealthExceptions();
        final String key = agentHealthExceptions.getKey(null);
        Assert.assertEquals(AgentHealthExceptions.class.getName(), key);
    }

    @Test
    public void testMetricKeyNames() throws Exception {
        // Recorded exception should default to 'Exception" if key is null:
        final TestHarvest testHarvest = new TestHarvest();
        TestHarvest.setInstance(testHarvest);
        TestHarvest.initialize(new AgentConfiguration());
        Measurements.initialize();
        StubAgentImpl.install();

        final AgentHealthException exception = new AgentHealthException(new Exception(TESTING));

        StatsEngine.get().getStatsMap().clear();
        AgentHealth.noticeException(exception, null);
        AgentHealth.noticeException(exception, "defaultKey");
        TaskQueue.synchronousDequeue();

        ConcurrentHashMap<String, Metric> statsMap = StatsEngine.get().getStatsMap();
        Assert.assertEquals(2, statsMap.size());
        Enumeration<String> key = statsMap.keys();
        Assert.assertTrue(key.nextElement().startsWith("Supportability/AgentHealth/Exception"));
        Assert.assertTrue(key.nextElement().startsWith("Supportability/AgentHealth/defaultKey"));

        TestHarvest.shutdown();
        Measurements.shutdown();
        StubAgentImpl.uninstall();
    }


    private class TestHarvest extends Harvest {
        public TestHarvest() {
        }

        public HarvestData getHarvestData() {
            return harvestData;
        }
    }
}
