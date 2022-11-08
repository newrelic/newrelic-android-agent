/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.activity;

import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.measurement.Measurement;
import com.newrelic.agent.android.measurement.MeasurementException;
import com.newrelic.agent.android.measurement.MeasurementPool;
import com.newrelic.agent.android.measurement.ThreadInfo;
import com.newrelic.agent.android.tracing.TraceMachine;

/**
 * Base implementation of the MeasuredActivity interface. Provides members for storing the Activity's state.
 * If the {@link #finish()} method is called on an object of this class, the fields become immutable and will
 * throw a {@link MeasurementException}.
 */
public class BaseMeasuredActivity implements MeasuredActivity {
    private static final AgentLog log = AgentLogManager.getAgentLog();

    private String name;
    private long startTime;
    private long endTime;
    private ThreadInfo startingThread;
    private ThreadInfo endingThread;
    private boolean autoInstrumented;
    private Measurement startingMeasurement;
    private Measurement endingMeasurement;
    private MeasurementPool measurementPool;
    private boolean finished;

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getMetricName() {
        return TraceMachine.formatActivityMetricName(name);
    }

    @Override
    public String getBackgroundMetricName() {
        return TraceMachine.formatActivityBackgroundMetricName(name);
    }

    @Override
    public long getStartTime() {
        return startTime;
    }

    @Override
    public long getEndTime() {
        return endTime;
    }

    @Override
    public ThreadInfo getStartingThread() {
        return startingThread;
    }

    @Override
    public ThreadInfo getEndingThread() {
        return endingThread;
    }

    @Override
    public boolean isAutoInstrumented() {
        return autoInstrumented;
    }

    @Override
    public Measurement getStartingMeasurement() {
        return startingMeasurement;
    }

    @Override
    public Measurement getEndingMeasurement() {
        return endingMeasurement;
    }

    @Override
    public MeasurementPool getMeasurementPool() {
        return measurementPool;
    }

    /**
     * Sets the Activity name.
     *
     * @param name Activity name
     */
    @Override
    public void setName(String name) {
        if (!logIfFinished()) {
            this.name = name;
        }
    }

    /**
     * Sets the Activity start time.
     *
     * @param startTime Start time
     */
    public void setStartTime(long startTime) {
        if (!logIfFinished()) {
            this.startTime = startTime;
        }
    }

    /**
     * Sets the Activity end time.
     *
     * @param endTime End time
     */
    public void setEndTime(long endTime) {
        if (!logIfFinished()) {
            this.endTime = endTime;
        }
    }

    /**
     * Sets the Activity starting thread information.
     *
     * @param startingThread Starting ThreadInfo
     */
    public void setStartingThread(ThreadInfo startingThread) {
        if (!logIfFinished()) {
            this.startingThread = startingThread;
        }
    }

    /**
     * Sets the Activity ending thread information
     *
     * @param endingThread Ending ThreadInfo
     */
    public void setEndingThread(ThreadInfo endingThread) {
        if (!logIfFinished()) {
            this.endingThread = endingThread;
        }
    }

    /**
     * Mark this Activity as autoInstrumented or User Defined.
     *
     * @param autoInstrumented true if auto-instrumented, false if User Defined.
     */
    public void setAutoInstrumented(boolean autoInstrumented) {
        if (!logIfFinished()) {
            this.autoInstrumented = autoInstrumented;
        }
    }

    /**
     * Sets the Activity starting Measurement
     *
     * @param startingMeasurement Starting measurement
     */
    public void setStartingMeasurement(Measurement startingMeasurement) {
        if (!logIfFinished()) {
            this.startingMeasurement = startingMeasurement;
        }
    }

    /**
     * Sets the Activity ending Measurement
     *
     * @param endingMeasurement Ending measurement
     */
    public void setEndingMeasurement(Measurement endingMeasurement) {
        if (!logIfFinished()) {
            this.endingMeasurement = endingMeasurement;
        }
    }

    /**
     * Set the MeasurementPool for the Activity.
     *
     * @param measurementPool Activity's MeasurementPool
     */
    public void setMeasurementPool(MeasurementPool measurementPool) {
        if (!logIfFinished()) {
            this.measurementPool = measurementPool;
        }
    }

    /**
     * Mark this Activity as finished. Once this method is invoked, Activity's fields may not be updated.
     */
    @Override
    public void finish() {
        finished = true;
    }

    @Override
    public boolean isFinished() {
        return finished;
    }

    private void throwIfFinished() {
        if (finished) {
            throw new MeasurementException("Attempted to modify finished Measurement");
        }
    }

    private boolean logIfFinished() {
        if (finished) {
            log.warning("BaseMeasuredActivity: cannot modify finished Activity");
        }
        return finished;
    }
}
