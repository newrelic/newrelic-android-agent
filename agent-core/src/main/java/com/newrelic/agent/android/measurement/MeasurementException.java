/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.measurement;

/**
 * A basic Exception thrown by the Measurement Engine when something is wrong.
 */
public class MeasurementException extends RuntimeException {
    public MeasurementException(String message) {
        super(message);
    }
}
