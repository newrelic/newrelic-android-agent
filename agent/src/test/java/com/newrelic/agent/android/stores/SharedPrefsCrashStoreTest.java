/**
 * Copyright 2021 New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.stores;

import android.content.Context;

import com.newrelic.agent.android.Agent;
import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.SpyContext;
import com.newrelic.agent.android.crash.Crash;
import com.newrelic.agent.android.crash.CrashReporter;
import com.google.gson.JsonObject;

import org.junit.Assert;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Spy;
import org.robolectric.RobolectricTestRunner;

import java.util.UUID;

import static org.mockito.Mockito.spy;

@RunWith(RobolectricTestRunner.class)
public class SharedPrefsCrashStoreTest {
    private static final String UUID_STATIC = "ab20dfe5-96d2-4c6d-b975-3fe9d8778dfc";

    @Spy
    private SharedPrefsCrashStore crashStore;
    private Context context = new SpyContext().getContext();
    private AgentConfiguration agentConfiguration;

    @Before
    public void setUp() throws Exception {
        crashStore = spy(new SharedPrefsCrashStore(context));

        agentConfiguration = new AgentConfiguration();
        agentConfiguration.setApplicationToken(SharedPrefsCrashStoreTest.class.getSimpleName());
        agentConfiguration.setCrashStore(crashStore);
        agentConfiguration.setReportCrashes(true);
        agentConfiguration.setEnableAnalyticsEvents(true);

        TestCrashReporter.initialize(agentConfiguration);
    }

    @After
    public void tearDown() throws Exception {
        crashStore.clear();
        Assert.assertTrue(crashStore.count() == 0);
    }

    @Test
    public void testStoreLotsOfCrashes() throws Exception {
        int reps = 100;
        for (Integer i = 0; i < reps; i++) {
            Crash crash = new Crash(new RuntimeException("testStoreLotsOfCrashes" + i.toString()));
            crashStore.store(crash);
        }
        // crashStore.store() is an async call, so it's possible the last element was not written
        // by the time the assert is hit. Use a delta of 1 in the assertion.
        Assert.assertEquals("Should contain " + Integer.valueOf(reps).toString() + " crashes", reps, crashStore.count(), 1);
    }

    @Test
    public void testStoreLotsOfTheSameCrash() throws Exception {
        int reps = 100;
        long timestamp = System.currentTimeMillis();
        Crash crash = new Crash(UUID.fromString(UUID_STATIC), Agent.getBuildId(), timestamp);
        for (Integer i = 0; i < reps; i++) {
            crashStore.store(crash);
        }
        Assert.assertEquals("Should contain 1 crash", 1, crashStore.count());
    }

    @Test
    public void testJsonSerializer() throws Exception {
        Crash crash = new Crash(new RuntimeException("testJsonSerializer"));
        final JsonObject jsonObj = crash.asJsonObject();
        Assert.assertEquals(crash.toJsonString(), jsonObj.toString());
    }


    private static class TestCrashReporter extends CrashReporter {
        public TestCrashReporter(AgentConfiguration agentConfiguration) {
            super(agentConfiguration);
        }

        public static CrashReporter initialize(AgentConfiguration agentConfiguration) {
            CrashReporter.initialize(agentConfiguration);
            instance.set(new TestCrashReporter(agentConfiguration));
            return instance.get();
        }
    }
}
