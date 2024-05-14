/*
 * Copyright (c) 2024. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android;

import com.google.gson.annotations.SerializedName;

public class ApplicationExitConfiguration {
    @SerializedName("enabled")
    boolean enabled;

    public ApplicationExitConfiguration() {
        this.enabled = true;
    }

    public ApplicationExitConfiguration(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled && FeatureFlag.featureEnabled(FeatureFlag.ApplicationExitReporting);
    }

    public void setConfiguration(ApplicationExitConfiguration applicationExitConfiguration) {
        this.enabled = applicationExitConfiguration.enabled;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ApplicationExitConfiguration) {
            ApplicationExitConfiguration rhs = (ApplicationExitConfiguration) obj;
            return this.enabled == rhs.enabled;
        }

        return false;
    }

    @Override
    public String toString() {
        return "{"
                + "\"enabled\"=" + enabled
                + "}";
    }

}
