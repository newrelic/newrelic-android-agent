/*
 * Copyright (c) 2023. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.instrumentation;

import com.newrelic.agent.android.FeatureFlag;
import com.newrelic.agent.android.analytics.AnalyticsControllerImpl;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.util.NamedThreadFactory;

import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class InstrumentationDelegate {
    protected static final AgentLog log = AgentLogManager.getAgentLog();

    protected static ExecutorService executor;
    protected static AnalyticsControllerImpl analyticsController;

    protected static final Set<FeatureFlag> enabledFeatures = FeatureFlag.enabledFeatures;

    static {
        InstrumentationDelegate.executor = Executors.newSingleThreadExecutor(new NamedThreadFactory("instrumentationDelegateWorker"));
        InstrumentationDelegate.analyticsController = AnalyticsControllerImpl.getInstance();
    }

    public static Future<?> submit(Set<FeatureFlag> requestedFeatures, Runnable runner) {
        if (enabledFeatures == null || enabledFeatures.isEmpty() || enabledFeatures.containsAll(requestedFeatures)) {
            return executor.submit(runner);
        }
        return executor.submit(() -> {
            // no-op
        });
    }
}
