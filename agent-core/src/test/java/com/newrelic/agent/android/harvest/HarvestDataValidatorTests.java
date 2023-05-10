/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.harvest;

import com.newrelic.agent.android.metric.Metric;
import com.newrelic.agent.android.metric.MetricStore;
import com.newrelic.agent.android.tracing.ActivityTrace;
import com.newrelic.agent.android.tracing.Trace;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;

import static org.mockito.Mockito.*;
@RunWith(JUnit4.class)
public class HarvestDataValidatorTests {

    @Test
    public void testEnsureActivityNameMetricsExistWithMetricCount() {
        Harvest mockHarvest = mock(Harvest.class);
        Harvest.setInstance(mockHarvest);

        MetricStore mockStore = mock(MetricStore.class);
        ArrayList<Metric> empty = new ArrayList<>();
        when(mockStore.getAllUnscoped()).thenReturn(empty);

        MachineMeasurements measurements = mock(MachineMeasurements.class);
        when(measurements.isEmpty()).thenReturn(false);
        when(measurements.getMetrics()).thenReturn(mockStore);


        HarvestData mockData = mock(HarvestData.class);
        when(mockData.getMetrics()).thenReturn(measurements);

        when(mockHarvest.getHarvestData()).thenReturn(mockData);

        ActivityTraces mockTraces = mock(ActivityTraces.class);
        when(mockData.getActivityTraces()).thenReturn(mockTraces);
        when(mockTraces.count()).thenReturn(1);

        ActivityTrace mockTrace = mock(ActivityTrace.class);
        Trace mockRoot = mock(Trace.class);
        mockRoot.displayName = "test_activity#blah";
        mockTrace.rootTrace = mockRoot;

        ArrayList<ActivityTrace> traces = new ArrayList<ActivityTrace>();
        traces.add(mockTrace);

        when(mockTraces.getActivityTraces()).thenReturn(traces);

        ArgumentCaptor<Metric> metricArg = ArgumentCaptor.forClass(Metric.class);

        HarvestDataValidator validator = new HarvestDataValidator();

        validator.ensureActivityNameMetricsExist();

        verify(measurements).addMetric(metricArg.capture());

        Assert.assertEquals(1, metricArg.getValue().getCount());
        Assert.assertEquals("Mobile/Activity/Name/test_activity",metricArg.getValue().getName());

    }
}
