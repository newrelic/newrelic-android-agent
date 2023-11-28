/*
 * Copyright (c) 2023. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.logging;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class LogReportingConfigurationTest {

    private LogReportingConfiguration logReportingConfig;
    private String entityGuid = UUID.randomUUID().toString();

    @Before
    public void setUp() throws Exception {
        this.logReportingConfig = new LogReportingConfiguration(entityGuid, true, LogReporting.LogLevel.DEBUG);
    }

    @Test
    public void testDefaultConfiguration() {
        Assert.assertTrue(logReportingConfig.getLoggingEnabled());
        Assert.assertEquals(LogReporting.LogLevel.DEBUG, logReportingConfig.getLogLevel());
    }

    @Test
    public void testEntityGuid() {
        Assert.assertNotNull(logReportingConfig.getEntityGuid());
        Assert.assertEquals(entityGuid, logReportingConfig.getEntityGuid());
    }

    @Test
    public void testHarvestPeriod() {
        Assert.assertEquals(TimeUnit.SECONDS.convert(30, TimeUnit.SECONDS), logReportingConfig.getHarvestPeriod());
    }

    @Test
    public void testExpirationPeriod() {
        Assert.assertEquals(TimeUnit.SECONDS.convert(2, TimeUnit.DAYS), logReportingConfig.getExpirationPeriod());
    }

    @Test
    public void testToString() {
        String loggingConfigAsJson = logReportingConfig.toString();
        Assert.assertTrue(loggingConfigAsJson.matches(".*\"enabled\"=true.*"));
        Assert.assertTrue(loggingConfigAsJson.matches(".*\"level\"=\"DEBUG\".*"));
    }

    @Test
    public void testDeserialization() {
        String loggingConfig = "{" +
                "  \"enabled\": true," +
                "  \"level\": VERBOSE," +
                "  \"entity_guid\": \"a4d39d21-588b-4342-ad87-967243533949\"," +
                "  \"data_report_period\": 15," +
                "  \"expiration_period\": 3600" +
                "}";

        Gson gson = new GsonBuilder().create();

        LogReportingConfiguration serialized = new LogReportingConfiguration(true, LogReporting.LogLevel.VERBOSE, "a4d39d21-588b-4342-ad87-967243533949", 15, 3600);
        LogReportingConfiguration deserialized = gson.fromJson(loggingConfig, LogReportingConfiguration.class);
        Assert.assertTrue(serialized.toString().equals(deserialized.toString()));

        String inflated = gson.toJson(deserialized, LogReportingConfiguration.class);
        String deflated = gson.toJson(serialized, LogReportingConfiguration.class);
        Assert.assertTrue(inflated.equals(deflated));
    }
}