/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.activity;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.newrelic.agent.android.activity.config.ActivityTraceConfiguration;
import com.newrelic.agent.android.activity.config.ActivityTraceConfigurationDeserializer;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.logging.ConsoleAgentLog;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.lang.reflect.Type;

@RunWith(JUnit4.class)
public class ActivityTraceConfigurationTests {
    private Type AT_CONFIG_TYPE = new TypeToken<ActivityTraceConfiguration>() {
    }.getType();


    @BeforeClass
    public static void setLogging() {
        AgentLogManager.setAgentLog(new ConsoleAgentLog());
    }

    @Test
    public void testDeserializeValidActivityTraceConfiguration() {
        String configJson = "[1,[[\"/*\",1,[[\"Metric/Pattern\",1,2,3.0,4.0]]]]]";

        Gson gson = createGson();

        ActivityTraceConfiguration configuration = gson.fromJson(configJson, AT_CONFIG_TYPE);
        Assert.assertEquals(1, configuration.getMaxTotalTraceCount());
    }

    @Test
    public void testDeserializeInvalidActivityTraceConfiguration() {
        String negativeMaxConfigs = "[-1,[[\"/*\",1,[[\"Metric/Pattern\",0,0,0.0,0.0]]]]]";

        Gson gson = createGson();
        ActivityTraceConfiguration configuration = gson.fromJson(negativeMaxConfigs, AT_CONFIG_TYPE);
        Assert.assertNull(configuration);
    }

    @Test
    public void testDeserializeReallyBadActivityTraceConfiguration() {
        String badJson = "[[[[[2,[[0]]]],4]]]";

        Gson gson = createGson();
        ActivityTraceConfiguration configuration = gson.fromJson(badJson, AT_CONFIG_TYPE);
        Assert.assertNull(configuration);
    }

    @Test
    public void testDeserializeMinimalTraceConfiguration() {
        String minimalJson = "[1,[]]";

        Gson gson = createGson();
        ActivityTraceConfiguration configuration = gson.fromJson(minimalJson, AT_CONFIG_TYPE);

        Assert.assertNotNull(configuration);
        Assert.assertEquals(1, configuration.getMaxTotalTraceCount());
    }

    public Gson createGson() {
        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.registerTypeAdapter(ActivityTraceConfiguration.class, new ActivityTraceConfigurationDeserializer());
        return gsonBuilder.create();
    }
}
