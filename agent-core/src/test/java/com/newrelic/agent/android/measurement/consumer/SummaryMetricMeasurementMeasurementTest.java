/*
 * Copyright (c) 2024. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.measurement.consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.newrelic.agent.android.Agent;
import com.newrelic.agent.android.AgentImpl;
import com.newrelic.agent.android.FeatureFlag;
import com.newrelic.agent.android.Measurements;
import com.newrelic.agent.android.harvest.Harvest;
import com.newrelic.agent.android.harvest.MachineMeasurements;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.logging.ConsoleAgentLog;
import com.newrelic.agent.android.measurement.CategorizedMeasurement;
import com.newrelic.agent.android.measurement.CustomMetricMeasurement;
import com.newrelic.agent.android.measurement.HttpTransactionMeasurement;
import com.newrelic.agent.android.measurement.Measurement;
import com.newrelic.agent.android.measurement.MeasurementTest;
import com.newrelic.agent.android.measurement.MeasurementType;
import com.newrelic.agent.android.measurement.MethodMeasurement;
import com.newrelic.agent.android.metric.Metric;
import com.newrelic.agent.android.metric.MetricStore;
import com.newrelic.agent.android.test.stub.StubAgentImpl;
import com.newrelic.agent.android.tracing.ActivityTrace;
import com.newrelic.agent.android.tracing.TraceLifecycleAware;
import com.newrelic.agent.android.tracing.TraceMachine;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.stream.Collectors;

public class SummaryMetricMeasurementMeasurementTest extends MeasurementTest {
    static AgentLog log;
    static ActivityTrace activityTrace;

    AgentImpl agent;
    SummaryMetricMeasurementConsumer consumer;
    TraceLifecycleAware traceListener = Mockito.spy(new TraceLifecycleAware() {
        @Override
        public void onEnterMethod() {
            log.debug("onEnterMethod");
        }

        @Override
        public void onExitMethod() {
            log.debug("onExitMethod");
        }

        @Override
        public void onTraceStart(ActivityTrace activityTrace) {
            log.debug("onTraceStart[" + activityTrace.getActivityName() + "]");
        }

        @Override
        public void onTraceComplete(ActivityTrace activityTrace) {
            log.debug("onTraceComplete[" + activityTrace.getActivityName() + "]");
        }

        @Override
        public void onTraceRename(ActivityTrace activityTrace) {
            log.debug("onTraceRename[" + activityTrace.getActivityName() + "]");
        }
    });

    @BeforeClass
    public static void beforeClass() throws Exception {
        FeatureFlag.enableFeature(FeatureFlag.InteractionTracing);

        log = new ConsoleAgentLog();
        log.setLevel(AgentLog.DEBUG);
        AgentLogManager.setAgentLog(log);

        // Helpful while debugging...
        TraceMachine.HEALTHY_TRACE_TIMEOUT = TraceMachine.HEALTHY_TRACE_TIMEOUT * 10;
    }

    @Before
    public void setUp() throws Exception {
        Measurements.initialize();

        agent = new StubAgentImpl();
        Agent.setImpl(agent);

        consumer = Mockito.spy(new SummaryMetricMeasurementConsumer());
        Measurements.addMeasurementConsumer(consumer);

        Harvest.getInstance().createHarvester();
        TraceMachine.addTraceListener(traceListener);
    }

    @After
    public void tearDown() throws Exception {
        Harvest.shutdown();
        TraceMachine.removeTraceListener(traceListener);
        Measurements.shutdown();
    }

    @Test
    public void consumeMeasurement() {
        consumer.consumeMeasurement(factory.provideMeasurement(MeasurementType.Activity));
        consumer.consumeMeasurement(factory.provideMeasurement(MeasurementType.Network));
        consumer.consumeMeasurement(factory.provideMeasurement(MeasurementType.Method));
        consumer.consumeMeasurement(factory.provideMeasurement(MeasurementType.Machine));
        consumer.consumeMeasurement(factory.provideMeasurement(MeasurementType.Custom));
        consumer.consumeMeasurement(factory.provideMeasurement(MeasurementType.Method));

        Assert.assertFalse(consumer.metrics.getAllByScope("providedMeasurementScope").isEmpty());
        Assert.assertFalse(consumer.metrics.getAllByScope("Mobile/Activity/Background/Name/Display networkTrace").isEmpty());
        Assert.assertFalse(consumer.metrics.getAllByScope("Mobile/Activity/Background/Name/Display methodTraces").isEmpty());

        Assert.assertTrue(consumer.metrics.getAllUnscoped().isEmpty());
        Assert.assertNull(consumer.metrics.get("Mobile/Summary/activityMeasurement", "providedMeasurementScope"));
        Assert.assertNotNull(consumer.metrics.get("Mobile/Summary/Network", "Mobile/Activity/Background/Name/Display networkTrace"));
        Assert.assertNotNull(consumer.metrics.get("Mobile/Summary/Network", "Mobile/Activity/Background/Name/Display methodTraces"));
        Assert.assertNull(consumer.metrics.get("Mobile/Summary/machineMeasurement", "providedMeasurementScope"));
        Assert.assertNotNull(consumer.metrics.get("Mobile/Summary/customMeasurement", "providedMeasurementScope"));
        Assert.assertEquals(2, consumer.metrics.get("Mobile/Summary/Network", "Mobile/Activity/Background/Name/Display methodTraces").getCount());
    }

    @Test
    public void consumeMethodMeasurement() {
        for (int i = 0; i < 3; i++) {
            Measurement measurement = factory.provideMeasurement(MeasurementType.Method);
            Assert.assertTrue(measurement instanceof MethodMeasurement);
            consumer.consumeMethodMeasurement((MethodMeasurement) measurement);
        }

        Assert.assertEquals(1, consumer.metrics.getAllByScope("Mobile/Activity/Background/Name/Display methodTraces").size());
        Assert.assertEquals(3, consumer.metrics.get("Mobile/Summary/Network", "Mobile/Activity/Background/Name/Display methodTraces").getCount());
    }

    @Test
    public void consumeCustomMeasurement() {
        for (int i = 0; i < 3; i++) {
            CategorizedMeasurement measurement = factory.provideCategorizedMeasurement();
            Assert.assertTrue(measurement instanceof CustomMetricMeasurement);
            consumer.consumeCustomMeasurement((CustomMetricMeasurement) measurement);
        }

        Assert.assertTrue(consumer.metrics.getAllUnscoped().isEmpty());
        List<Metric> summaryMetrics = consumer.metrics.getAllByScope("providedCustomMeasurementScope")
                .stream()
                .filter(metric -> metric.getName().matches("Mobile/Summary/.*"))
                .collect(Collectors.toList());

        Assert.assertEquals(3, summaryMetrics.stream().mapToLong(x -> x.getCount()).sum());
        summaryMetrics.forEach(metric -> {
            Assert.assertTrue(metric.getCount() > 0);
        });
    }

    @Test
    public void consumeNetworkMeasurement() {
        for (int i = 0; i < 3; i++) {
            Measurement measurement = factory.provideMeasurement(MeasurementType.Network);
            Assert.assertTrue(measurement instanceof HttpTransactionMeasurement);
            consumer.consumeNetworkMeasurement((HttpTransactionMeasurement) measurement);
        }

        Assert.assertEquals(1, consumer.metrics.getAllByScope("Mobile/Activity/Background/Name/Display networkTrace").size());
        Assert.assertEquals(3, consumer.metrics.get("Mobile/Summary/Network", "Mobile/Activity/Background/Name/Display networkTrace").getCount());
    }

    @Test
    public void formatMetricName() {
        Assert.assertTrue(consumer.formatMetricName("provider#Scope").equals("Mobile/Summary/provider/Scope"));
    }

    @Test
    public void onHarvest() {
        consumer.onHarvest();
        Assert.assertTrue(consumer.completedTraces.isEmpty());
    }

    @Test
    public void onTraceStart() {
        final ActivityTrace activityTrace = factory.provideActivityTrace("onTraceStart", 2);
        verify(traceListener, times(1)).onTraceStart(activityTrace);
    }

    @Test
    public void onTraceComplete() {
        final ActivityTrace activityTrace = factory.provideActivityTrace("onTraceComplete", 3);
        verify(traceListener, times(1)).onTraceComplete(activityTrace);
    }

    @Test
    public void onEnterMethod() {
        factory.provideActivityTrace("onEnterMethod", 2);
        verify(traceListener, times(14)).onEnterMethod();
    }

    @Test
    public void onExitMethod() {
        factory.provideActivityTrace("onExitMethod", 3);
        verify(traceListener, times(18)).onExitMethod();
    }

    @Test
    public void onTraceRename() {
        factory.provideActivityTrace("onTraceRename", 4);
        TraceMachine.startTracing("onTraceRename");
        TraceMachine.setCurrentDisplayName("onTraceRename Test");
        verify(traceListener, times(1)).onTraceRename(any(ActivityTrace.class));
    }

    @Test
    public void summarizeActivityMetrics() {
        final ActivityTrace activityTrace = factory.provideActivityTrace("BaseUIFragment", 4);

        consumer.summarizeActivityMetrics(activityTrace);

        MachineMeasurements metrics = Harvest.getInstance().getHarvestData().getMetrics();
        Assert.assertEquals(0, consumer.completedTraces.size());
        Assert.assertFalse(metrics.isEmpty());

        Assert.assertNotNull(metrics.getMetrics());
        MetricStore metricStore = metrics.getMetrics();
        Assert.assertFalse(metricStore.isEmpty());
        Assert.assertFalse(metricStore.getAllByScope("Mobile/Activity/Summary/Name/Display BaseUIFragment").isEmpty());
        Assert.assertNotEquals(0, metricStore.get("Mobile/Summary/JSON", "Mobile/Activity/Summary/Name/Display BaseUIFragment").getCount());
    }

    @Test
    public void summarizeActivityNetworkMetrics() {
        final ActivityTrace activityTrace = factory.provideActivityTrace("BaseUIFragment", 4);

        consumer.summarizeActivityNetworkMetrics(activityTrace);

        MachineMeasurements metrics = Harvest.getInstance().getHarvestData().getMetrics();
        Assert.assertFalse(metrics.isEmpty());
        Assert.assertNotNull(metrics.getMetrics());

        MetricStore metricStore = metrics.getMetrics();
        Assert.assertFalse(metricStore.isEmpty());
        Assert.assertEquals(2, metricStore.getAll().size());
        Assert.assertEquals(1, metricStore.get("Mobile/Activity/Network/Display BaseUIFragment/Count").getCount());
        Assert.assertNotEquals(0, metricStore.get("Mobile/Activity/Network/Display BaseUIFragment/Time").getTotal());
    }

    @Test
    public void shouldNotRecordUnscopedMetrics() {
        Assert.assertFalse(consumer.recordUnscopedMetrics);
        consumer.consumeMeasurement(factory.provideMeasurement(MeasurementType.Method));
        Assert.assertFalse(consumer.metrics.isEmpty());
        Assert.assertFalse(consumer.metrics.getAll().isEmpty());
        Assert.assertTrue(consumer.metrics.getAllByScope("").isEmpty());
    }
}