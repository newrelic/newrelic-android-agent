/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static com.newrelic.agent.android.FeatureFlag.AnalyticsEvents;
import static com.newrelic.agent.android.FeatureFlag.CrashReporting;
import static com.newrelic.agent.android.FeatureFlag.DefaultInteractions;
import static com.newrelic.agent.android.FeatureFlag.DistributedTracing;
import static com.newrelic.agent.android.FeatureFlag.FedRampEnabled;
import static com.newrelic.agent.android.FeatureFlag.HandledExceptions;
import static com.newrelic.agent.android.FeatureFlag.HttpResponseBodyCapture;
import static com.newrelic.agent.android.FeatureFlag.InteractionTracing;
import static com.newrelic.agent.android.FeatureFlag.NetworkErrorRequests;
import static com.newrelic.agent.android.FeatureFlag.NetworkRequests;

public class FeatureFlagTest {
    @Before
    public void setUp() throws Exception {
        FeatureFlag.resetFeatures();
    }

    @Test
    public void enableFeature() throws Exception {
        Assert.assertTrue("HandledExceptions is disabled by default", FeatureFlag.featureEnabled(HandledExceptions));
        FeatureFlag.enableFeature(HandledExceptions);
        Assert.assertTrue("HandledExceptions is now enabled", FeatureFlag.featureEnabled(HandledExceptions));
    }

    @Test
    public void disableFeature() throws Exception {
        Assert.assertTrue("CrashReporting is enabled by default", FeatureFlag.featureEnabled(CrashReporting));
        FeatureFlag.disableFeature(CrashReporting);
        Assert.assertTrue("HandledExceptions is now enabled", FeatureFlag.featureEnabled(HandledExceptions));
    }

    @Test
    public void featureEnabled() throws Exception {
        Assert.assertTrue("HandledExceptions is disabled by default", FeatureFlag.featureEnabled(HandledExceptions));
        FeatureFlag.enableFeature(HandledExceptions);
        Assert.assertTrue("CrashReporting is now disabled", FeatureFlag.featureEnabled(CrashReporting));
    }

    @Test
    public void defaultEnabledFeatures() throws Exception {
        Assert.assertTrue("HttpResponseBodyCapture is enabled by default", FeatureFlag.featureEnabled(HttpResponseBodyCapture));
        Assert.assertTrue("CrashReporting is enabled by default", FeatureFlag.featureEnabled(CrashReporting));
        Assert.assertTrue("AnalyticsEvents is enabled by default", FeatureFlag.featureEnabled(AnalyticsEvents));
        Assert.assertTrue("InteractionTracing is enabled by default", FeatureFlag.featureEnabled(InteractionTracing));
        Assert.assertTrue("DefaultInteractions is enabled by default", FeatureFlag.featureEnabled(DefaultInteractions));
        Assert.assertTrue("NetworkErrorRequests is enabled by default", FeatureFlag.featureEnabled(NetworkErrorRequests));
        Assert.assertTrue("HandledExceptions is enabled by default", FeatureFlag.featureEnabled(HandledExceptions));
        Assert.assertTrue("NetworkRequests is enabled by default", FeatureFlag.featureEnabled(NetworkRequests));
        Assert.assertTrue("Distributed tracing is enabled by default", FeatureFlag.featureEnabled(DistributedTracing));
    }

    @Test
    public void defaultDisabledFeatures() throws Exception {
        Assert.assertFalse("FedRamp is disabled by default", FeatureFlag.featureEnabled(FedRampEnabled));
    }

    @Test
    public void resetFeatureFlags() throws Exception {
        Assert.assertTrue("NetworkRequests is enabled by default", FeatureFlag.featureEnabled(NetworkRequests));
        FeatureFlag.disableFeature(NetworkRequests);
        Assert.assertFalse("NetworkRequests is now disabled", FeatureFlag.featureEnabled(NetworkRequests));

        Assert.assertTrue("NetworkErrorRequests is enabled by default", FeatureFlag.featureEnabled(NetworkErrorRequests));
        FeatureFlag.disableFeature(NetworkErrorRequests);
        Assert.assertFalse("NetworkErrorRequests is now disabled", FeatureFlag.featureEnabled(NetworkErrorRequests));

        Assert.assertTrue("CrashReporting is enabled by default", FeatureFlag.featureEnabled(CrashReporting));
        FeatureFlag.disableFeature(CrashReporting);
        Assert.assertFalse("CrashReporting is now disabled", FeatureFlag.featureEnabled(CrashReporting));

        Assert.assertTrue("DistributedTracing is enabled by default", FeatureFlag.featureEnabled(DistributedTracing));
        FeatureFlag.disableFeature(DistributedTracing);
        Assert.assertFalse("Distributed tracing is now disabled", FeatureFlag.featureEnabled(DistributedTracing));

        FeatureFlag.resetFeatures();
        Assert.assertTrue("CrashReporting is now enabled", FeatureFlag.featureEnabled(CrashReporting));
        Assert.assertTrue("NetworkRequests is now enabled", FeatureFlag.featureEnabled(NetworkRequests));
        Assert.assertTrue("NetworkErrorRequests is now enabled", FeatureFlag.featureEnabled(NetworkErrorRequests));
        Assert.assertTrue("Distributed tracing is now enabled", FeatureFlag.featureEnabled(DistributedTracing));

        Assert.assertFalse("FedRamp is disabled by default", FeatureFlag.featureEnabled(FedRampEnabled));
    }

}