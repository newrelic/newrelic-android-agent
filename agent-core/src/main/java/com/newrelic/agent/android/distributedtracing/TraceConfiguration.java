/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.distributedtracing;

import com.newrelic.agent.android.harvest.Harvest;
import com.newrelic.agent.android.harvest.HarvestAdapter;
import com.newrelic.agent.android.harvest.HarvestConfiguration;

import java.util.concurrent.atomic.AtomicReference;

public class TraceConfiguration extends HarvestAdapter {
    public static AtomicReference<TraceConfiguration> instance = new AtomicReference<TraceConfiguration>(new TraceConfiguration());

    String accountId;
    String applicationId;
    String trustedAccountId;

    static TraceConfiguration getInstance() {
        return instance.get();
    }

    static TraceConfiguration setInstance(TraceConfiguration instance) {
        TraceConfiguration.instance.set(instance);
        return getInstance();
    }

    TraceConfiguration(String accountId, String applicationId, String trustedAccountId) {
        this.accountId = accountId;
        this.applicationId = applicationId;
        this.trustedAccountId = trustedAccountId;

        Harvest.addHarvestListener(this);
    }

    public TraceConfiguration(HarvestConfiguration harvestConfiguration) {
        this(harvestConfiguration.getAccount_id(),
                harvestConfiguration.getApplication_id(),
                harvestConfiguration.getTrusted_account_key());
    }

    public TraceConfiguration() {
        this(HarvestConfiguration.getDefaultHarvestConfiguration());
    }

    public boolean isSampled() {
        return false;   // never for Mobile
    }

    @Override
    public void onHarvestConnected() {
        setConfiguration(Harvest.getHarvestConfiguration());
    }

    void setConfiguration(HarvestConfiguration harvestConfiguration) {
        applicationId = harvestConfiguration.getApplication_id();
        accountId = harvestConfiguration.getAccount_id();
        trustedAccountId = harvestConfiguration.getTrusted_account_key();
    }
}
