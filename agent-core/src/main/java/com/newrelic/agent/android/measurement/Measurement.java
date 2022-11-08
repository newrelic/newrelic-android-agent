/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.measurement;

/**
 * The fundamental {@code Measurement} interface.
 */
public interface Measurement {
    /**
     * The {@link MeasurementType} of this {@code Measurement}.
     *
     * @return {@link MeasurementType} of this {@code Measurement}.
     */
    public MeasurementType getType();

    /**
     * The name of this {@code Measurement}.
     *
     * @return name of this {@code Measurement}.
     */
    public String getName();

    /**
     * The scope of this {@code Measurement}.
     *
     * @return scope of this {@code Measurement}.
     */
    public String getScope();

    /**
     * The start time of this {@code Measurement} in milliseconds.
     *
     * @return start time of this {@code Measurement} in milliseconds.
     */
    public long getStartTime();

    /**
     * The start time of this {@code Measurement} in seconds.
     *
     * @return start time of this {@code Measurement} in seconds.
     */
    public double getStartTimeInSeconds();

    /**
     * The end time of this {@code Measurement} in milliseconds.
     *
     * @return end time of this {@code Measurement} in milliseconds.
     */
    public long getEndTime();

    /**
     * The end time of this {@code Measurement} in seconds.
     *
     * @return end time of this {@code Measurement} in seconds.
     */
    public double getEndTimeInSeconds();

    /**
     * The exclusive time of this {@code Measurement} in milliseconds.
     *
     * @return exclusive time of this {@code Measurement} in milliseconds.
     */
    public long getExclusiveTime();

    /**
     * The exclusive time of this {@code Measurement} in seconds.
     *
     * @return exclusive time of this {@code Measurement} in seconds.
     */
    public double getExclusiveTimeInSeconds();

    /**
     * The thread information associated with this {@code Measurement}.
     *
     * @return thread information associated with this {@code Measurement}.
     */
    public ThreadInfo getThreadInfo();

    /**
     * True if this is an instantaneous {@code Measurement}. These {@code Measurements} have a start time and no end time, thus no
     * duration.
     *
     * @return true if the {@code Measurement} has a start time and no end time
     */
    public boolean isInstantaneous();

    /**
     * A {@code Measurement} may be created and then subsequently updated. A {@code Measurement} becomes immutable upon calling
     * finish.
     */
    public void finish();

    /**
     * Returns true if this {@code Measurement} is completed and immutable.
     *
     * @return true if finished
     */
    public boolean isFinished();

    public double asDouble();
}
