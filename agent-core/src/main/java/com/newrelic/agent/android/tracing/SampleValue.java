/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.tracing;

public class SampleValue {
    private Double value = 0.0;
    private boolean isDouble;

    public SampleValue(double value) {
        setValue(value);
    }

    public SampleValue(long value) {
        setValue(value);
    }

    public Number getValue() {
        if (isDouble)
            return asDouble();
        return asLong();
    }

    public Double asDouble() {
        return value;
    }

    public Long asLong() {
        return value.longValue();
    }

    public void setValue(double value) {
        this.value = value;
        isDouble = true;
    }

    public void setValue(long value) {
        this.value = (double) value;
        isDouble = false;
    }

    public boolean isDouble() {
        return isDouble;
    }

    public void setDouble(boolean aDouble) {
        isDouble = aDouble;
    }
}
