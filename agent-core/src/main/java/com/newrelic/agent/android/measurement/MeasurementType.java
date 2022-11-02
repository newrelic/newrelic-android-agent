/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.measurement;

/**
 * Types of Measurements.
 */
public enum MeasurementType {
    Network,
    Method,
    Activity,
    Custom,
    Any,
    Machine
}
