/*
 * Copyright (c) 2024. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.harvest;

public interface HarvestConfigurable {
    default void setConfiguration(HarvestConfiguration harvestConfiguration) {}

    default HarvestConfiguration getConfiguration() {
        return HarvestConfiguration.getDefaultHarvestConfiguration();
    }

    default void updateConfiguration(HarvestConfiguration newConfiguration) {}
}
