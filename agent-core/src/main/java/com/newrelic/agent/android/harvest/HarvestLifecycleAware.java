/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.harvest;

public interface HarvestLifecycleAware {

    default void onHarvestStart() {}

    default void onHarvestStop() {}

    default void onHarvestBefore() {}

    default void onHarvest() {}

    default void onHarvestFinalize() {}

    default void onHarvestError() {}

    default void onHarvestSendFailed() {}

    default void onHarvestComplete() {}

    default void onHarvestConnected() {}

    default void onHarvestDisconnected() {}

    default void onHarvestDisabled() {}

    default void onHarvestConfigurationChanged() {}
}
