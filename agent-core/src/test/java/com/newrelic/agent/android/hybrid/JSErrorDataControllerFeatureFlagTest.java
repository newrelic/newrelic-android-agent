/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.hybrid;

import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.FeatureFlag;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class JSErrorDataControllerFeatureFlagTest {

    private CountingJSErrorStore store;

    @Before
    public void setUp() {
        FeatureFlag.resetFeatures();
        store = new CountingJSErrorStore();
        AgentConfiguration.getInstance().setJsErrorStore(store);
        JSErrorDataController.reset();
    }

    @After
    public void tearDown() {
        FeatureFlag.resetFeatures();
        JSErrorDataController.reset();
        AgentConfiguration.getInstance().setJsErrorStore(null);
    }

    @Test
    public void sendJSErrorData_returnsFalse_whenFeatureDisabled() {
        FeatureFlag.disableFeature(FeatureFlag.JSError);

        boolean queued = JSErrorDataController.getInstance().sendJSErrorData(
                "TypeError", "boom", "stack", false, null);

        Assert.assertFalse("sendJSErrorData must be a no-op when JSError is disabled", queued);
        Assert.assertEquals("No errors should be stored when JSError is disabled",
                0, store.storeCalls.get());
    }

    @Test
    public void sendJSErrorData_stillRejectsInvalidInput_whenEnabled() {
        Assert.assertTrue("JSError must be enabled by default",
                FeatureFlag.featureEnabled(FeatureFlag.JSError));

        boolean queued = JSErrorDataController.getInstance().sendJSErrorData(
                null, "boom", "stack", false, null);

        Assert.assertFalse("Null/empty error name must still be rejected when enabled", queued);
    }

    @Test
    public void sendJSErrorData_toggleBackToEnabled_noLingeringNoOp() {
        FeatureFlag.disableFeature(FeatureFlag.JSError);
        Assert.assertFalse(FeatureFlag.featureEnabled(FeatureFlag.JSError));

        FeatureFlag.enableFeature(FeatureFlag.JSError);
        Assert.assertTrue(FeatureFlag.featureEnabled(FeatureFlag.JSError));
    }

    private static final class CountingJSErrorStore implements JSErrorStore {
        final AtomicInteger storeCalls = new AtomicInteger(0);
        final Map<String, String> data = new HashMap<>();

        @Override
        public boolean store(String id, String value) {
            storeCalls.incrementAndGet();
            data.put(id, value);
            return true;
        }

        @Override
        public List<String> fetchAll() {
            return new java.util.ArrayList<>(data.values());
        }

        @Override
        public Map<String, String> fetchAllEntries() {
            return new HashMap<>(data);
        }

        @Override
        public void delete(String id) {
            data.remove(id);
        }

        @Override
        public int count() {
            return data.size();
        }

        @Override
        public void clear() {
            data.clear();
        }
    }
}
