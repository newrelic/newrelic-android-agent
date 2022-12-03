/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.measurement;

import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;

/**
 * The base implementation of the {@link Measurement} interface. This class holds {@code Measurement} state and enforces
 * immutability after {@link #finish()} has been called.
 */
public class BaseMeasurement implements Measurement {
    private static final AgentLog log = AgentLogManager.getAgentLog();

    private MeasurementType type;
    private String name;
    private String scope;
    private long startTime;
    private long endTime;
    private long exclusiveTime;
    private ThreadInfo threadInfo;
    private boolean finished;

    public BaseMeasurement(MeasurementType measurementType) {
        setType(measurementType);
    }

    public BaseMeasurement(Measurement measurement) {
        setType(measurement.getType());
        setName(measurement.getName());
        setScope(measurement.getScope());
        setStartTime(measurement.getStartTime());
        setEndTime(measurement.getEndTime());
        setExclusiveTime(measurement.getExclusiveTime());
        setThreadInfo(measurement.getThreadInfo());
        finished = measurement.isFinished();
    }

    void setType(MeasurementType type) {
        if (!logIfFinished()) {
            this.type = type;
        }
    }

    public void setName(String name) {
        if (!logIfFinished()) {
            this.name = name;
        }
    }

    public void setScope(String scope) {
        if (!logIfFinished()) {
            this.scope = scope;
        }
    }

    public void setStartTime(long startTime) {
        if (!logIfFinished()) {
            this.startTime = startTime;
        }
    }

    public void setEndTime(long endTime) {
        if (!logIfFinished()) {
            if (endTime < startTime) {
                log.error("Measurement end time must not precede start time - startTime: " + startTime + " endTime: " + endTime);
                return;
            }
            this.endTime = endTime;
        }
    }

    public void setExclusiveTime(long exclusiveTime) {
        if (!logIfFinished()) {
            this.exclusiveTime = exclusiveTime;
        }
    }

    @Override
    public MeasurementType getType() {
        return type;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getScope() {
        return scope;
    }

    @Override
    public long getStartTime() {
        return startTime;
    }

    @Override
    public double getStartTimeInSeconds() {
        return startTime / 1000.0;
    }

    @Override
    public long getEndTime() {
        return endTime;
    }

    @Override
    public double getEndTimeInSeconds() {
        return endTime / 1000.0;
    }

    @Override
    public long getExclusiveTime() {
        return exclusiveTime;
    }

    @Override
    public double getExclusiveTimeInSeconds() {
        return exclusiveTime / 1000.0;
    }

    @Override
    public double asDouble() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ThreadInfo getThreadInfo() {
        return threadInfo;
    }

    public void setThreadInfo(ThreadInfo threadInfo) {
        this.threadInfo = threadInfo;
    }

    @Override
    public boolean isInstantaneous() {
        return endTime == 0;
    }

    @Override
    public void finish() {
        if (finished) {
            throw new MeasurementException("Finish called on already finished Measurement");
        }
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
            log.warn("BaseMeasuredActivity: cannot modify finished Activity");
        }
        return finished;
    }

    @Override
    public String toString() {
        return "BaseMeasurement{" +
                "type=" + type +
                ", name='" + name + '\'' +
                ", scope='" + scope + '\'' +
                ", startTime=" + startTime +
                ", endTime=" + endTime +
                ", exclusiveTime=" + exclusiveTime +
                ", threadInfo=" + threadInfo +
                ", finished=" + finished +
                '}';
    }
}
