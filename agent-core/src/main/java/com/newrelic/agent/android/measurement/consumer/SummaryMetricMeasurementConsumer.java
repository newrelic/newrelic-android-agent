/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.measurement.consumer;

import com.newrelic.agent.android.harvest.Harvest;
import com.newrelic.agent.android.instrumentation.MetricCategory;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.measurement.BaseMeasurement;
import com.newrelic.agent.android.measurement.CustomMetricMeasurement;
import com.newrelic.agent.android.measurement.Measurement;
import com.newrelic.agent.android.measurement.MeasurementType;
import com.newrelic.agent.android.measurement.MethodMeasurement;
import com.newrelic.agent.android.measurement.http.HttpTransactionMeasurement;
import com.newrelic.agent.android.metric.Metric;
import com.newrelic.agent.android.tracing.ActivityTrace;
import com.newrelic.agent.android.tracing.Trace;
import com.newrelic.agent.android.tracing.TraceLifecycleAware;
import com.newrelic.agent.android.tracing.TraceMachine;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public class SummaryMetricMeasurementConsumer extends MetricMeasurementConsumer implements TraceLifecycleAware {
    private static final String METRIC_PREFIX = "Mobile/Summary/";
    private static final String ACTIVITY_METRIC_PREFIX = "Mobile/Activity/Summary/Name/";

    private static final AgentLog log = AgentLogManager.getAgentLog();

    private final List<ActivityTrace> completedTraces = new CopyOnWriteArrayList<ActivityTrace>();

    public SummaryMetricMeasurementConsumer() {
        super(MeasurementType.Any);
        recordUnscopedMetrics = false;

        TraceMachine.addTraceListener(this);
    }

    @Override
    public void consumeMeasurement(Measurement measurement) {
        if (measurement == null)
            return;

        switch (measurement.getType()) {
            case Method:
                consumeMethodMeasurement((MethodMeasurement) measurement);
                break;
            case Network:
                consumeNetworkMeasurement((HttpTransactionMeasurement) measurement);
                break;
            case Custom:
                consumeCustomMeasurement((CustomMetricMeasurement) measurement);
                break;
        }
        return;
    }

    private void consumeMethodMeasurement(MethodMeasurement methodMeasurement) {
        if (methodMeasurement.getCategory() == null || methodMeasurement.getCategory() == MetricCategory.NONE) {
            methodMeasurement.setCategory(MetricCategory.categoryForMethod(methodMeasurement.getName()));
            if (methodMeasurement.getCategory() == MetricCategory.NONE) {
                return;
            }
        }

        final BaseMeasurement summary = new BaseMeasurement(methodMeasurement);
        summary.setName(methodMeasurement.getCategory().getCategoryName());

        super.consumeMeasurement(summary);
    }

    private void consumeCustomMeasurement(CustomMetricMeasurement customMetricMeasurement) {
        if (customMetricMeasurement.getCategory() == null || customMetricMeasurement.getCategory() == MetricCategory.NONE)
            return;

        final BaseMeasurement summary = new BaseMeasurement(customMetricMeasurement);
        summary.setName(customMetricMeasurement.getCategory().getCategoryName());
        super.consumeMeasurement(summary);
    }

    private void consumeNetworkMeasurement(HttpTransactionMeasurement networkMeasurement) {
        final BaseMeasurement summary = new BaseMeasurement(networkMeasurement);
        summary.setName(MetricCategory.NETWORK.getCategoryName());
        super.consumeMeasurement(summary);
    }

    @Override
    protected String formatMetricName(String name) {
        return METRIC_PREFIX + name.replace("#", "/");
    }

    @Override
    public void onHarvest() {
        if (metrics.getAll().size() == 0) {
            return;
        }

        if (completedTraces.size() == 0) {
            return;
        }

        for (final ActivityTrace trace : completedTraces) {
            summarizeActivityMetrics(trace);
        }

        if (metrics.getAll().size() != 0) {
            log.debug("Not all metrics were summarized!");
        }

        completedTraces.clear();
    }

    private void summarizeActivityNetworkMetrics(ActivityTrace activityTrace) {
        final String activityName = activityTrace.getActivityName();

        if (activityTrace.networkCountMetric.getCount() > 0) {
            final String name = activityTrace.networkCountMetric.getName();
            activityTrace.networkCountMetric.setName(name.replace("<activity>", activityName));
            activityTrace.networkCountMetric.setCount(1);
            activityTrace.networkCountMetric.setMinFieldValue(activityTrace.networkCountMetric.getTotal());
            activityTrace.networkCountMetric.setMaxFieldValue(activityTrace.networkCountMetric.getTotal());
            Harvest.addMetric(activityTrace.networkCountMetric);
        }

        if (activityTrace.networkTimeMetric.getCount() > 0) {
            final String name = activityTrace.networkTimeMetric.getName();
            activityTrace.networkTimeMetric.setName(name.replace("<activity>", activityName));
            activityTrace.networkTimeMetric.setCount(1);
            activityTrace.networkTimeMetric.setMinFieldValue(activityTrace.networkTimeMetric.getTotal());
            activityTrace.networkTimeMetric.setMaxFieldValue(activityTrace.networkTimeMetric.getTotal());
            Harvest.addMetric(activityTrace.networkTimeMetric);
        }
    }

    private void summarizeActivityMetrics(ActivityTrace activityTrace) {
        final Trace trace = activityTrace.rootTrace;

        // Gather all UI and background metrics associated with this Activity Trace.
        final List<Metric> activityMetrics = metrics.removeAllWithScope(trace.metricName);
        final List<Metric> backgroundMetrics = metrics.removeAllWithScope(trace.metricBackgroundName);
        final Map<String, Metric> summaryMetrics = new HashMap<String, Metric>();

        // Roll up both foreground and background metrics into a single metric set.  Taking care not
        // to add the same metric from the fore and background twice.

        // First, add all the foreground metrics.
        for (final Metric activityMetric : activityMetrics) {
            summaryMetrics.put(activityMetric.getName(), activityMetric);
        }

        // Now, the background metrics.  If the metric is already present, aggregate it.  Otherwise, add it.
        // This covers the case where a metric is only present in the background.
        for (final Metric backgroundMetric : backgroundMetrics) {
            if (summaryMetrics.containsKey(backgroundMetric.getName())) {
                summaryMetrics.get(backgroundMetric.getName()).aggregate(backgroundMetric);
            } else {
                summaryMetrics.put(backgroundMetric.getName(), backgroundMetric);
            }
        }

        // Do a pass through the metrics to compute total exclusive time.
        double totalExclusiveTime = 0.0;
        for (final Metric metric : summaryMetrics.values()) {
            totalExclusiveTime += metric.getExclusive();
        }

        final double traceTime = (trace.exitTimestamp - trace.entryTimestamp) / 1000.0;

        // Do another pass through the metrics, normalizing, scaling, and otherwise fixing them up.
        for (final Metric metric : summaryMetrics.values()) {
            double normalizedTime = 0.0;

            if (metric.getExclusive() != 0.0 && totalExclusiveTime != 0.0) {
                normalizedTime = metric.getExclusive() / totalExclusiveTime;
            }

            final double scaledTime = normalizedTime * traceTime;

            metric.setTotal(scaledTime);
            metric.setExclusive(scaledTime);
            metric.setMinFieldValue(0.0);
            metric.setMaxFieldValue(0.0);
            metric.setSumOfSquares(0.0);
            metric.setScope(ACTIVITY_METRIC_PREFIX + trace.displayName);


            // Send scoped and un-scoped metrics to the Harvester.
            Harvest.addMetric(metric);

            Metric unScoped = new Metric(metric);
            unScoped.setScope(null);
            Harvest.addMetric(unScoped);
        }

        // Now process network requests and condense into single metric
        summarizeActivityNetworkMetrics(activityTrace);

    }

    @Override
    public void onHarvestError() {
        // Override behavior: don't automatically clear out on error. Handle in onHarvest.
    }

    @Override
    public void onHarvestComplete() {
        // Override behavior: don't automatically clear out on complete. Handle in onHarvest.
    }

    @Override
    public void onTraceStart(ActivityTrace activityTrace) {
    }

    @Override
    public void onTraceComplete(ActivityTrace activityTrace) {
        if (!completedTraces.contains(activityTrace))
            completedTraces.add(activityTrace);
    }

    @Override
    public void onEnterMethod() {
    }

    @Override
    public void onExitMethod() {
    }

    @Override
    public void onTraceRename(ActivityTrace activityTrace) {
    }

}
