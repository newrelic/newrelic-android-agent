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

public class MobileErrorDataControllerFeatureFlagTest {

    private CountingMobileErrorStore store;

    @Before
    public void setUp() {
        FeatureFlag.resetFeatures();
        store = new CountingMobileErrorStore();
        AgentConfiguration.getInstance().setMobileErrorStore(store);
        // PayloadController must be initialized for sendMobileErrorData's submitCallable
        // to actually run the store callable on a worker thread.
        PayloadController.initialize(AgentConfiguration.getInstance());
        MobileErrorDataController.reset();
    }

    @After
    public void tearDown() {
        FeatureFlag.resetFeatures();
        MobileErrorDataController.reset();
        PayloadController.shutdown();
        AgentConfiguration.getInstance().setMobileErrorStore(null);
    }

    @Test
    public void sendMobileErrorData_returnsFalse_whenFeatureDisabled() {
        FeatureFlag.disableFeature(FeatureFlag.MobileError);

        boolean queued = MobileErrorDataController.getInstance().sendMobileErrorData(
                "TypeError", "boom", "stack", false, null);

        Assert.assertFalse("sendMobileErrorData must be a no-op when MobileError is disabled", queued);
        Assert.assertEquals("No errors should be stored when MobileError is disabled",
                0, store.storeCalls.get());
    }

    @Test
    public void sendMobileErrorData_stillRejectsInvalidInput_whenEnabled() {
        Assert.assertTrue("MobileError must be enabled by default",
                FeatureFlag.featureEnabled(FeatureFlag.MobileError));

        boolean queued = MobileErrorDataController.getInstance().sendMobileErrorData(
                null, "boom", "stack", false, null);

        Assert.assertFalse("Null/empty error name must still be rejected when enabled", queued);
    }

    @Test
    public void sendMobileErrorData_toggleBackToEnabled_noLingeringNoOp() {
        FeatureFlag.disableFeature(FeatureFlag.MobileError);
        Assert.assertFalse(FeatureFlag.featureEnabled(FeatureFlag.MobileError));

        FeatureFlag.enableFeature(FeatureFlag.MobileError);
        Assert.assertTrue(FeatureFlag.featureEnabled(FeatureFlag.MobileError));
    }

    @Test
    public void sendMobileErrorData_enabledPath_routesThroughPayloadController() throws InterruptedException {
        // Closes the symmetric gap to sendMobileErrorData_returnsFalse_whenFeatureDisabled:
        // when the feature is enabled, the callable submitted to PayloadController must
        // actually run and reach the configured MobileErrorStore. The latch is the
        // synchronization point because the work runs on the PayloadController worker
        // thread, not the calling thread.
        Assert.assertTrue("MobileError must be enabled by default",
                FeatureFlag.featureEnabled(FeatureFlag.MobileError));

        boolean queued = MobileErrorDataController.getInstance().sendMobileErrorData(
                "TypeError", "boom", "stack", false, null);

        Assert.assertTrue("sendMobileErrorData must accept valid input when enabled", queued);
        Assert.assertTrue("Submitted callable must reach the store within the timeout",
                store.stored.await(5, TimeUnit.SECONDS));
        Assert.assertEquals("Exactly one entry must be persisted", 1, store.storeCalls.get());
        Assert.assertEquals("The store should hold the persisted entry", 1, store.data.size());
    }

    private static final class CountingMobileErrorStore implements MobileErrorStore {
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
