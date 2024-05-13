/*
 * Copyright (c) 2024. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android;

import com.google.gson.annotations.SerializedName;

import java.beans.FeatureDescriptor;

public class ApplicationExitConfiguration {
    @SerializedName("enabled")
    boolean enabled;

    public ApplicationExitConfiguration() {
        this.enabled = false;
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
}
