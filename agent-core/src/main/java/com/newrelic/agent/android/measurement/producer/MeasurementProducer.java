/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.measurement.producer;

import com.newrelic.agent.android.measurement.Measurement;
import com.newrelic.agent.android.measurement.MeasurementType;

import java.util.Collection;

/**
 * A producer of {@link Measurement}s. Implementing classes shall call {@link #produceMeasurement(com.newrelic.agent.android.measurement.Measurement)}
 * for each {@code Measurement} to be recorded.
 *
 * The produced {@code Measurements} are drained by a {@link com.newrelic.agent.android.measurement.MeasurementPool} when
 * the pool calls {@link #drainMeasurements()}.
 */
public interface MeasurementProducer {
    /**
     * The type of {@code Measurements} this Consumer can process.
     *
     * @return a MeasurementType
     */
    MeasurementType getMeasurementType();

    /**
     * Produce a {@code Measurement}.
     *
     * @param measurement the produced {@code Measurement}.
     */
    void produceMeasurement(Measurement measurement);

    /**
     * Produce a collection of {@code Measurements}
     *
     * @param measurements the collection of {@code Measurements} produced.
     */
    void produceMeasurements(Collection<Measurement> measurements);

    /**
     * Returns all produced {@code Measurements}. Implementing classes should also clear the internally
     * stored {@code Measurements} so that subsequent calls to this method return only {@code Measurements} produced since the last
     * invocation.
     *
     * @return the collection of produced {@code Measurement} since the last {@code drainMeasurements} invocation, or {@code null}
     * if no new {@code Measurements} have been produced.
     */
    Collection<Measurement> drainMeasurements();
}
