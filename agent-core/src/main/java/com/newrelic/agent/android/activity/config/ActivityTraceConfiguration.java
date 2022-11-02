/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.activity.config;

public class ActivityTraceConfiguration {
    private int maxTotalTraceCount;

    public static ActivityTraceConfiguration defaultActivityTraceConfiguration() {
        ActivityTraceConfiguration configuration = new ActivityTraceConfiguration();
        configuration.setMaxTotalTraceCount(1);
        return configuration;
    }

    public int getMaxTotalTraceCount() {
        return maxTotalTraceCount;
    }

    public void setMaxTotalTraceCount(int maxTotalTraceCount) {
        this.maxTotalTraceCount = maxTotalTraceCount;
    }

    @Override
    public String toString() {
        return "ActivityTraceConfiguration{maxTotalTraceCount=" + maxTotalTraceCount + '}';
    }
}
