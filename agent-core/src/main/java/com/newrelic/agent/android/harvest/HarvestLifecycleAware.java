/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.harvest;

public interface HarvestLifecycleAware {

    void onHarvestStart();

    void onHarvestStop();

    void onHarvestBefore();

    void onHarvest();

    void onHarvestFinalize();

    void onHarvestError();

    void onHarvestSendFailed();

    void onHarvestComplete();

    void onHarvestConnected();

    void onHarvestDisconnected();

    void onHarvestDisabled();

    default void onHarvestConfigurationChanged() {

    }
}
