/*
 * Copyright (c) 2023. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.instrumentation;

import com.newrelic.agent.android.analytics.AnalyticsControllerImpl;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.util.NamedThreadFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class InstrumentationDelegate {
    protected static final AgentLog log = AgentLogManager.getAgentLog();
    protected static ExecutorService executor;
    protected final AnalyticsControllerImpl analyticsController;

    static {
        InstrumentationDelegate.executor = Executors.newSingleThreadExecutor(new NamedThreadFactory("instrumentationDelegateWorker"));
    }

    public InstrumentationDelegate() {
        this.analyticsController = AnalyticsControllerImpl.getInstance();
    }
}
