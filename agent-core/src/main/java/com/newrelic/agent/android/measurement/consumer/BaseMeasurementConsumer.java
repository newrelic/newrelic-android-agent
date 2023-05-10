/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.measurement.consumer;

import com.newrelic.agent.android.harvest.HarvestAdapter;
import com.newrelic.agent.android.measurement.Measurement;
import com.newrelic.agent.android.measurement.MeasurementType;

import java.util.Collection;

/**
 * The base implementation of {@link MeasurementConsumer}. This class primarily implements the logic
 * behind consuming a collection of {@link Measurement}s. Its {@link #consumeMeasurement(com.newrelic.agent.android.measurement.Measurement)}
 * implementation does nothing and does not retain the {@code Measurement}.
 */
public class BaseMeasurementConsumer extends HarvestAdapter implements MeasurementConsumer {
    private final MeasurementType measurementType;

    public BaseMeasurementConsumer(MeasurementType measurementType) {
        this.measurementType = measurementType;
    }

    @Override
    public MeasurementType getMeasurementType() {
        return measurementType;
    }

    @Override
    public void consumeMeasurement(Measurement measurement) {
    }

    @Override
    public void consumeMeasurements(Collection<Measurement> measurements) {
        for (Measurement measurement : measurements) {
            consumeMeasurement(measurement);
        }
    }

}
