/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.payload;

import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;

import java.util.concurrent.atomic.AtomicBoolean;

public abstract class PayloadReporter {
    protected static final AgentLog log = AgentLogManager.getAgentLog();

    protected final AtomicBoolean isEnabled;
    protected final AtomicBoolean isStarted;
    protected final AgentConfiguration agentConfiguration;

    public PayloadReporter(AgentConfiguration agentConfiguration) {
        this.isEnabled = new AtomicBoolean(false);  // set by impl according to feature flag
        this.isStarted = new AtomicBoolean(false);  // set by impl during start
        this.agentConfiguration = agentConfiguration;
    }

    protected abstract void start();    // subclasses must start their reporter
    protected abstract void stop(); 	// subclasses must stop their reporter

    public boolean isEnabled() {
        return isEnabled.get();
    }

    public void setEnabled(boolean enabled) {
        isEnabled.set(enabled);
    }

    public AgentConfiguration getAgentConfiguration() {
        return agentConfiguration;
    }

}
