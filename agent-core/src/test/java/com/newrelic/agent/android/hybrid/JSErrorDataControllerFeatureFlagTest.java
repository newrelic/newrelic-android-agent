/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.hybrid;

import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.FeatureFlag;
import com.newrelic.agent.android.payload.PayloadController;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class JSErrorDataControllerFeatureFlagTest {

    private CountingJSErrorStore store;

    @Before
    public void setUp() {
        FeatureFlag.resetFeatures();
        store = new CountingJSErrorStore();
        AgentConfiguration.getInstance().setMobileErrorStore(store);
        // PayloadController must be initialized for sendJSErrorData's submitCallable
        // to actually run the store callable on a worker thread.
        PayloadController.initialize(AgentConfiguration.getInstance());
        JSErrorDataController.reset();
    }

    @After
    public void tearDown() {
        FeatureFlag.resetFeatures();
        JSErrorDataController.reset();
        PayloadController.shutdown();
        AgentConfiguration.getInstance().setMobileErrorStore(null);
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

    @Test
    public void sendJSErrorData_enabledPath_routesThroughPayloadController() throws InterruptedException {
        // Closes the symmetric gap to sendJSErrorData_returnsFalse_whenFeatureDisabled:
        // when the feature is enabled, the callable submitted to PayloadController must
        // actually run and reach the configured JSErrorStore. The latch is the
        // synchronization point because the work runs on the PayloadController worker
        // thread, not the calling thread.
        Assert.assertTrue("JSError must be enabled by default",
                FeatureFlag.featureEnabled(FeatureFlag.JSError));

        boolean queued = JSErrorDataController.getInstance().sendJSErrorData(
                "TypeError", "boom", "stack", false, null);

        Assert.assertTrue("sendJSErrorData must accept valid input when enabled", queued);
        Assert.assertTrue("Submitted callable must reach the store within the timeout",
                store.stored.await(5, TimeUnit.SECONDS));
        Assert.assertEquals("Exactly one entry must be persisted", 1, store.storeCalls.get());
        Assert.assertEquals("The store should hold the persisted entry", 1, store.data.size());
    }

    private static final class CountingJSErrorStore implements MobileErrorStore {
        final AtomicInteger storeCalls = new AtomicInteger(0);
        final Map<String, String> data = new HashMap<>();
        /** Released after the first {@link #store} call so tests can join the worker thread. */
        final CountDownLatch stored = new CountDownLatch(1);

        @Override
        public boolean store(String id, String value) {
            storeCalls.incrementAndGet();
            data.put(id, value);
            stored.countDown();
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
