/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.harvest;

public interface HarvestLifecycleAware {

    public void onHarvestStart();

    public void onHarvestStop();

    public void onHarvestBefore();

    public void onHarvest();

    public void onHarvestFinalize();

    public void onHarvestError();

    public void onHarvestSendFailed();

    public void onHarvestComplete();

    public void onHarvestConnected();

    public void onHarvestDisconnected();

    public void onHarvestDisabled();
}
