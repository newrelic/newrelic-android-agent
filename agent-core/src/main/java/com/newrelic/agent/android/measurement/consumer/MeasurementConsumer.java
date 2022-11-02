/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.measurement.consumer;

import com.newrelic.agent.android.measurement.Measurement;
import com.newrelic.agent.android.measurement.MeasurementType;

import java.util.Collection;

/**
 * A {@link Measurement} Consumer. {@code MeasurementConsumers} are registered with and managed by a {@link com.newrelic.agent.android.measurement.MeasurementPool}.
 *
 */
public interface MeasurementConsumer {

    /**
     * The type of {@code Measurements} this Consumer can process.
     *
     * @return a MeasurementType
     */
    public MeasurementType getMeasurementType();

    /**
     * Consume a {@code Measurement}. When {@code Measurements} are available in a {@code MeasurementPool}, the {@code MeasurementPool}
     * will invoke this method for every {@code Measurement} available.
     *
     * The {@code MeasurementConsumer} can request that the {@code MeasurementPool} retain a {@code Measurement}. Retaining a {@code Measurement}
     * means that the {@code MeasurementPool} will not discard the {@code Measurement} after it is consumed and instead it will
     * be placed back into the {@code MeasurementPool}.
     *
     * Retained {@code Measurements} will be presented to the {@code MeasurementConsumer} again during the next {@code Measurement}
     * broadcast, as if they were new {@code Measurements}.
     *
     * @param measurement The new or retained {@code Measurement} to be consumed.
     *
     * @return {@code null} if the {@code Measurement} should not be retained, which is the normal case. If the {@code Measurement} should
     * be retained and presented again, the {@code measurement} parameter should be returned.
     */
    public void consumeMeasurement(Measurement measurement);

    /**
     * Consume a collection of {@code Measurements}. This method should invoke {@link #consumeMeasurements(java.util.Collection)}
     * for each {@code Measurement} in the collection.
     *
     * @return {@code null} if no {@code Measurements} in the collection should be retained, which is the normal case. If the {@code Measurements} should
     * be retained and presented again, the subset of the {@code measurements} to retain should be returned.
     */
    public void consumeMeasurements(Collection<Measurement> measurements);

}
