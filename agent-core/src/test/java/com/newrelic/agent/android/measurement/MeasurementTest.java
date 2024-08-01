/*
 * Copyright (c) 2024. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.measurement;

import org.junit.Before;

import java.util.Collection;
import java.util.HashSet;


public abstract class MeasurementTest {
    protected static MetricMeasurementFactory factory = new MetricMeasurementFactory();

    protected Collection<Measurement> measurementPool;

    @Before
    public void setUp() throws Exception {
        measurementPool = new HashSet<>();
    }

}
