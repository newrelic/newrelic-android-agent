/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.metric;

public enum MetricUnit {
    PERCENT("%"),
    BYTES("bytes"),
    SECONDS("sec"),
    BYTES_PER_SECOND("bytes/second"),
    OPERATIONS("op");

    private String label;

    MetricUnit(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }
}
