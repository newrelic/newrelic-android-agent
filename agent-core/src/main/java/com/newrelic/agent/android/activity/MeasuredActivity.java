/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.activity;

import com.newrelic.agent.android.measurement.Measurement;
import com.newrelic.agent.android.measurement.MeasurementPool;
import com.newrelic.agent.android.measurement.ThreadInfo;

/**
 * MeasuredActivity represents an Activity, either user defined or automatically instrumented.
 * Activities define scope for Scoped Metrics and Activity Traces.
 */
public interface MeasuredActivity {

    /**
     * The name of this Activity.
     * @return The activity name.
     */
    public String getName();

    /**
     * The metric name for this Activity.
     * @return The activity metric name.
     */
    public String getMetricName();

    /**
     * Set the name of this Activity.
     * @param name The activity name.
     */
    public void setName(String name);

    /**
     * The background metric name of this Activity.
     * @return The activity background metric name.
     */
    public String getBackgroundMetricName();

    /**
     * The time the Activity began.
     * @return Start time timestamp.
     */
    public long getStartTime();

    /**
     * The time the Activity ended.
     * @return End time timestamp.
     */
    public long getEndTime();

    /**
     * The thread that the Activity began execution on.
     * @return ThreadInfo for the starting thread.
     */
    public ThreadInfo getStartingThread();

    /**
     * The thread that the Activity ended on.
     * @return ThreadInfo for the ending thread.
     */
    public ThreadInfo getEndingThread();

    /**
     * Returns true if this Activity was created automatically by New Relic.
     * @return true if auto-instrumented. false if user defined.
     */
    public boolean isAutoInstrumented();

    /**
     * The first Measurement associated with this Activity.
     * @return The starting Measurement.
     */
    public Measurement getStartingMeasurement();

    /**
     * The last Measurement associated with this Activity.
     * @return The ending Measurement.
     */
    public Measurement getEndingMeasurement();

    /**
     * Get the MeasurementPool associated with this Activity. The MeasurementPool processes all Measurements
     * for the Activity.
     * @return The Activity's MeasurementPool.
     */
    public MeasurementPool getMeasurementPool();

    /**
     * Mark this Activity as finished. Once finished, the Activity cannot be updated.
     */
    public void finish();

    /**
     * Returns whether this Activity is finished or in progress.
     * @return true if the Activity is finished.
     */
    public boolean isFinished();

}
