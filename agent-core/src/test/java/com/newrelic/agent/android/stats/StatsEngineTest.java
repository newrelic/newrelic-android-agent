/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.stats;

import com.newrelic.agent.android.Agent;
import com.newrelic.agent.android.ApplicationFramework;
import com.newrelic.agent.android.TaskQueue;
import com.newrelic.agent.android.harvest.DeviceInformation;
import com.newrelic.agent.android.metric.Metric;
import com.newrelic.agent.android.metric.MetricNames;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;


public class StatsEngineTest {

    @Before
    public void setUp() throws Exception {
        TaskQueue.clear();
        StatsEngine.reset();
        StatsEngine.enable();
    }

    @Test
    public void get() {
        Assert.assertEquals(StatsEngine.get(), StatsEngine.get());
    }

    @Test
    public void inc() {
        Metric metric = StatsEngine.get().lazyGet("metric");
        Assert.assertEquals(0, metric.getCount());

        StatsEngine.get().inc("metric");
        Assert.assertEquals(1, metric.getCount());

        StatsEngine.get().inc("metric", 2);
        Assert.assertEquals(3, metric.getCount());
    }

    @Test
    public void sample() {
        StatsEngine.get().sample("metric", 2.0f);

        Metric metric = StatsEngine.get().lazyGet("metric");
        Assert.assertEquals(2.0f, metric.getMin(), 0f);
        Assert.assertEquals(2.0f, metric.getMax(), 0f);
        Assert.assertEquals(2.0f, metric.getTotal(), 0f);

        StatsEngine.get().sample("metric", 1.0f);
        Assert.assertEquals(1.0f, metric.getMin(), 0f);
        Assert.assertEquals(3.0f, metric.getTotal(), 0f);
        Assert.assertEquals(5.0f, metric.getSumOfSquares(), 0f);
    }

    @Test
    public void sampleTimeMs() {
        Metric metric = StatsEngine.get().lazyGet("metric");

        long tStart = System.currentTimeMillis();
        StatsEngine.get().sampleTimeMs("metric", tStart);
        Assert.assertEquals(tStart / 1000f, metric.getMin(), 0);

        long tCheck = System.currentTimeMillis();
        StatsEngine.get().sampleTimeMs("metric", tCheck);

        long tEnd = System.currentTimeMillis();
        StatsEngine.get().sampleTimeMs("metric", tEnd);
        Assert.assertEquals(tEnd / 1000f, metric.getMax(), 0);
    }

    @Test
    public void sampleMetricDataUsage() {
        Metric metric = StatsEngine.get().lazyGet("metric");
        StatsEngine.get().sampleMetricDataUsage("metric", 100, 200);
        Assert.assertEquals(1, metric.getCount(), 0);
        Assert.assertEquals(100, metric.getTotal(), 0);
        Assert.assertEquals(200, metric.getExclusive(), 0);
        Assert.assertEquals(0, metric.getMin(), 0);
        Assert.assertEquals(0, metric.getMax(), 0);
        Assert.assertEquals(0, metric.getSumOfSquares(), 0);
    }

    @Test
    public void populateMetrics() {
        StatsEngine.get().lazyGet("metric1");
        StatsEngine.get().lazyGet("metric2");
        StatsEngine.get().lazyGet("metric3");

        StatsEngine.populateMetrics();
        Assert.assertEquals(3, TaskQueue.size());
    }

    @Test
    public void calculateMetricsDataUsage() {
        DeviceInformation deviceInformation = Agent.getDeviceInformation();
        String dataUsageName = MetricNames.SUPPORTABILITY_DESTINATION_OUTPUT_BYTES
                .replace(MetricNames.TAG_FRAMEWORK, deviceInformation.getApplicationFramework().name())
                .replace(MetricNames.TAG_DESTINATION, MetricNames.METRIC_DATA_USAGE_COLLECTOR);
        String COLLECTOR_CONNECT_URI = MetricNames.METRIC_DATA_USAGE_COLLECTOR + "/connect";
        String COLLECTOR_DATA_URI = MetricNames.METRIC_DATA_USAGE_COLLECTOR + "/data";
        String COLLECTOR_MOBILE_F = MetricNames.METRIC_DATA_USAGE_COLLECTOR + "/f";
        String COLLECTOR_MOBILE_CRASH = MetricNames.METRIC_DATA_USAGE_COLLECTOR + "/mobile_crash";

        Metric metric1 = StatsEngine.get().lazyGet(COLLECTOR_CONNECT_URI);
        metric1.setCount(1);
        metric1.setTotal(100.0);
        metric1.setExclusive(100.0);
        Metric metric2 = StatsEngine.get().lazyGet(COLLECTOR_DATA_URI);
        metric2.setCount(2);
        metric2.setTotal(200.0);
        metric2.setExclusive(200.0);
        Metric metric3 = StatsEngine.get().lazyGet(COLLECTOR_MOBILE_F);
        metric3.setCount(3);
        metric3.setTotal(300.0);
        metric3.setExclusive(0.0);
        Metric metric4 = StatsEngine.get().lazyGet(COLLECTOR_MOBILE_CRASH);
        metric4.setCount(4);
        metric4.setTotal(400.0);
        metric4.setExclusive(0.0);

        StatsEngine.calculateMetricsDataUseage();
        Metric metricsDataUsage = StatsEngine.get().lazyGet(dataUsageName);
        Assert.assertEquals(10, metricsDataUsage.getCount(), 0);
        Assert.assertEquals(1000, metricsDataUsage.getTotal(), 0);
        Assert.assertEquals(300, metricsDataUsage.getExclusive(), 0);
        Assert.assertEquals(0, metricsDataUsage.getMin(), 0);
        Assert.assertEquals(0, metricsDataUsage.getMax(), 0);
        Assert.assertEquals(0, metricsDataUsage.getSumOfSquares(), 0);
    }

    @Test
    public void onHarvest() {
        StatsEngine.get().lazyGet("metric1");
        StatsEngine.get().lazyGet("metric2");
        StatsEngine.get().lazyGet("metric3");
        Assert.assertEquals(3, StatsEngine.get().getStatsMap().size());

        StatsEngine.get().onHarvest();
        Assert.assertEquals(4, TaskQueue.size());
        Assert.assertEquals(4, StatsEngine.get().getStatsMap().size());
    }

    @Test
    public void onHarvestComplete() {
        StatsEngine.get().lazyGet("metric1");
        StatsEngine.get().lazyGet("metric2");
        StatsEngine.get().lazyGet("metric3");
        Assert.assertEquals(3, StatsEngine.get().getStatsMap().size());

        StatsEngine.get().onHarvestComplete();
        Assert.assertEquals(0, StatsEngine.get().getStatsMap().size());
    }

    @Test
    public void reset() {
        StatsEngine.get().lazyGet("metric1");
        StatsEngine.get().lazyGet("metric2");
        StatsEngine.get().lazyGet("metric3");
        Assert.assertEquals(3, StatsEngine.get().getStatsMap().size());

        StatsEngine.reset();
        Assert.assertEquals(0, TaskQueue.size());
        Assert.assertEquals(0, StatsEngine.get().getStatsMap().size());
    }

    @Test
    public void disable() {
        StatsEngine.disable();

        StatsEngine.get().lazyGet("metric1");
        StatsEngine.get().lazyGet("metric2");
        StatsEngine.get().lazyGet("metric3");
        Assert.assertEquals(0, StatsEngine.get().getStatsMap().size());
        StatsEngine.get().onHarvest();

        Assert.assertEquals(0, TaskQueue.size());
        Assert.assertEquals(0, StatsEngine.get().getStatsMap().size());
    }

    @Test
    public void enable() {
        StatsEngine.disable();

        StatsEngine.get().lazyGet("metric1");
        StatsEngine.get().lazyGet("metric2");
        StatsEngine.get().lazyGet("metric3");
        Assert.assertEquals(0, StatsEngine.get().getStatsMap().size());
        StatsEngine.get().onHarvest();

        Assert.assertEquals(0, TaskQueue.size());
        Assert.assertEquals(0, StatsEngine.get().getStatsMap().size());

        StatsEngine.enable();
        StatsEngine.get().lazyGet("metric1");
        StatsEngine.get().lazyGet("metric2");
        StatsEngine.get().lazyGet("metric3");
        Assert.assertEquals(3, StatsEngine.get().getStatsMap().size());
        StatsEngine.get().onHarvest();

        Assert.assertEquals(4, TaskQueue.size());
        Assert.assertEquals(0, StatsEngine.get().getStatsMap().size());
    }

    @Test
    public void getStatsMap() {
        Assert.assertNotNull(StatsEngine.get().getStatsMap());
    }

    @Test
    public void lazyGet() {
        Metric metric = StatsEngine.get().lazyGet("metric1");
        Assert.assertTrue(StatsEngine.get().getStatsMap().contains(metric));

        StatsEngine.disable();
        Metric metric2 = StatsEngine.get().lazyGet("metric2");
        Assert.assertFalse(StatsEngine.get().getStatsMap().contains(metric2));

        StatsEngine.enable();
        Metric metric3 = StatsEngine.get().lazyGet("metric2");
        Assert.assertTrue(StatsEngine.get().getStatsMap().contains(metric3));
    }

    @Test
    public void testSupportabiltyNamespace() {
        String metricName = MetricNames.SUPPORTABILITY_API.replace(MetricNames.TAG_NAME, "NamespaceTest");
        DeviceInformation deviceInfo = Agent.getDeviceInformation();

        Metric metric = StatsEngine.notice().lazyGet(metricName);
        Assert.assertEquals("Supportability/Mobile/Android/API/NamespaceTest", metric.getName());

        deviceInfo.setApplicationFramework(ApplicationFramework.Cordova);
        metric = StatsEngine.notice().lazyGet(metricName);
        Assert.assertEquals("Supportability/Mobile/Android/Cordova/API/NamespaceTest", metric.getName());

        deviceInfo.setApplicationFrameworkVersion("1.2.3.4");
        metric = StatsEngine.notice().lazyGet(metricName);
        Assert.assertEquals("Supportability/Mobile/Android/Cordova/1.2.3.4/API/NamespaceTest", metric.getName());

        deviceInfo.setApplicationFramework(ApplicationFramework.Native);
        metric = StatsEngine.notice().lazyGet(metricName);
        Assert.assertEquals("Supportability/Mobile/Android/Native/1.2.3.4/API/NamespaceTest", metric.getName());

        deviceInfo.setApplicationFrameworkVersion("");
        metric = StatsEngine.notice().lazyGet(metricName);
        Assert.assertEquals("Supportability/Mobile/Android/API/NamespaceTest", metric.getName());
    }

}