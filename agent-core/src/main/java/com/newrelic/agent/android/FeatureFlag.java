/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android;

import com.newrelic.agent.android.logging.AgentLogManager;

import java.util.HashSet;
import java.util.Set;

public enum FeatureFlag {
    HttpResponseBodyCapture,
    CrashReporting,
    AnalyticsEvents,
    InteractionTracing,
    DefaultInteractions,
    NetworkRequests,
    NetworkErrorRequests,
    HandledExceptions,
    DistributedTracing,
    NativeReporting,
    AppStartMetrics,
    FedRampEnabled,
    Jetpack,
    OfflineStorage,
    LogReporting,
    ApplicationExitReporting,
    BackgroundReporting;

    public static final Set<FeatureFlag> enabledFeatures = new HashSet<FeatureFlag>();

    static {
        resetFeatures();
    }

    public static void enableFeature(FeatureFlag featureFlag) {
        switch (featureFlag) {
            case LogReporting:
                AgentLogManager.getAgentLog().error("LogReporting feature is disabled in this release");
                break;
            default:
                enabledFeatures.add(featureFlag);
                break;
        }
    }

    public static void disableFeature(FeatureFlag featureFlag) {
        enabledFeatures.remove(featureFlag);
    }

    public static boolean featureEnabled(FeatureFlag featureFlag) {
        return enabledFeatures.contains(featureFlag);
    }

    public static void resetFeatures() {
        enabledFeatures.clear();

        // Enabled by default
        enableFeature(HttpResponseBodyCapture);
        enableFeature(CrashReporting);
        enableFeature(AnalyticsEvents);
        enableFeature(InteractionTracing);
        enableFeature(DefaultInteractions);
        enableFeature(NetworkRequests);
        enableFeature(NetworkErrorRequests);
        enableFeature(HandledExceptions);
        enableFeature(DistributedTracing);
        enableFeature(AppStartMetrics);
        enableFeature(ApplicationExitReporting);
    }
}
