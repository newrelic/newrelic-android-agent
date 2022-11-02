/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.harvest;

import com.newrelic.agent.android.metric.Metric;
import com.newrelic.agent.android.tracing.ActivityTrace;

import java.util.List;

public class HarvestDataValidator extends HarvestAdapter {

    @Override
    public void onHarvestFinalize() {
        if (!Harvest.isInitialized())
            return;

        ensureActivityNameMetricsExist();
    }

    public void ensureActivityNameMetricsExist() {
        final HarvestData harvestData = Harvest.getInstance().getHarvestData();

        final ActivityTraces activityTraces = harvestData.getActivityTraces();
        if (activityTraces == null || activityTraces.count() == 0)
            return;

        final MachineMeasurements metrics = harvestData.getMetrics();
        if (metrics == null || metrics.isEmpty())
            return;


        for(final ActivityTrace activityTrace : activityTraces.getActivityTraces()) {
            String activityName = activityTrace.rootTrace.displayName;
            final int hashIndex = activityName.indexOf("#");
            if (hashIndex > 0) {
                activityName = activityName.substring(0, hashIndex);
            }

            final String activityMetricRoot = "Mobile/Activity/Name/" + activityName;

            // Check all un-scoped metrics for metric names with this root.
            boolean foundMetricForActivity = false;
            final List<Metric> unScopedMetrics = metrics.getMetrics().getAllUnscoped();

            if (unScopedMetrics != null && unScopedMetrics.size() > 0) {
                for (final Metric metric : unScopedMetrics) {
                    if (metric.getName().startsWith(activityMetricRoot)) {
                        foundMetricForActivity = true;
                        break;
                    }
                }
            }

            if (foundMetricForActivity)
                continue;

            // There was no metric matching the activity name. Add one.
            final Metric activityMetric = new Metric(activityMetricRoot);
            activityMetric.sample(1.0f);
            metrics.addMetric(activityMetric);
        }
    }

}
