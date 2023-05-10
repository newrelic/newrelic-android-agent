/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.measurement.producer;

import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.measurement.Measurement;
import com.newrelic.agent.android.measurement.MeasurementType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

/**
 * The base implementation of the {@link MeasurementProducer} interface. This implementation maintains
 * an internal collection of produced {@code Measurements} which is returned and cleared upon calls to {@link #drainMeasurements()}.
 */
public class BaseMeasurementProducer implements MeasurementProducer {
    private static final AgentLog log = AgentLogManager.getAgentLog();
    private final MeasurementType producedMeasurementType;
    private final ArrayList<Measurement> producedMeasurements = new ArrayList<Measurement>();

    public BaseMeasurementProducer(MeasurementType measurementType) {
        producedMeasurementType = measurementType;
    }

    @Override
    public MeasurementType getMeasurementType() {
        return producedMeasurementType;
    }

    @Override
    public void produceMeasurement(Measurement measurement) {
        synchronized (producedMeasurements) {
            if (measurement != null) {
                producedMeasurements.add(measurement);
            }
        }
    }

    public void produceMeasurements(Collection<Measurement> measurements) {
        synchronized (producedMeasurements) {
            if (measurements != null) {
                producedMeasurements.addAll(measurements);
                // filter out any null measurements
                while (producedMeasurements.remove(null))
                    ;
            }
        }
    }

    @Override
    public Collection<Measurement> drainMeasurements() {
        synchronized (producedMeasurements) {
            if (producedMeasurements.size() == 0)
                return Collections.emptyList();
            Collection<Measurement> measurements = new ArrayList<Measurement>(producedMeasurements);
            producedMeasurements.clear();
            return measurements;
        }
    }

}

