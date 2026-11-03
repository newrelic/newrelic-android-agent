/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.mobileview;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ComposeNavHostRegistryTests {

    @Test
    public void registerThenIsRegisteredReturnsTrue() {
        Object activity = new Object();
        ComposeNavHostRegistry.getInstance().register(activity);

        Assert.assertTrue("Registered activity should report as registered.",
                ComposeNavHostRegistry.getInstance().isRegistered(activity));
    }

    @Test
    public void unregisterRemovesRegistration() {
        Object activity = new Object();
        ComposeNavHostRegistry.getInstance().register(activity);
        ComposeNavHostRegistry.getInstance().unregister(activity);

        Assert.assertFalse("Unregistered activity should no longer report as registered.",
                ComposeNavHostRegistry.getInstance().isRegistered(activity));
    }

    @Test
    public void isRegisteredReturnsFalseForNeverRegisteredKey() {
        Object activity = new Object();

        Assert.assertFalse("A never-registered key should not be reported as registered.",
                ComposeNavHostRegistry.getInstance().isRegistered(activity));
    }

    @Test
    public void isRegisteredReturnsFalseForNullKey() {
        Assert.assertFalse("A null key should not be reported as registered.",
                ComposeNavHostRegistry.getInstance().isRegistered(null));
    }

    @Test
    public void registerAndUnregisterNullAreNoops() {
        try {
            ComposeNavHostRegistry.getInstance().register(null);
            ComposeNavHostRegistry.getInstance().unregister(null);
        } catch (Exception e) {
            Assert.fail("register(null)/unregister(null) should not throw: " + e.getMessage());
        }
    }
}
