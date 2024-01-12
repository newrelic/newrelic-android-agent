/*
 * Copyright (c) 2023. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.logging;

import com.newrelic.agent.android.harvest.HarvestLifecycleAware;

public class LogForwarding extends LogReporting implements HarvestLifecycleAware {

    @Override
    public void onHarvestStart() {
    }

    @Override
    public void onHarvestStop() {
    }

    @Override
    public void onHarvestBefore() {
    }

    @Override
    public void onHarvest() {
    }

    @Override
    public void onHarvestFinalize() {
    }

    @Override
    public void onHarvestError() {
    }

    @Override
    public void onHarvestSendFailed() {
    }

    @Override
    public void onHarvestComplete() {
    }

    @Override
    public void onHarvestConnected() {
    }

    @Override
    public void onHarvestDisconnected() {
    }

    @Override
    public void onHarvestDisabled() {
    }
}
