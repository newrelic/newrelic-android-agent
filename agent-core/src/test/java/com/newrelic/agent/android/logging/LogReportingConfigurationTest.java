/*
 * Copyright (c) 2023. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.logging;

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
        Assert.assertTrue(loggingConfigAsJson.matches("^\"log_reporting\" \\{.*\\}$"));
        Assert.assertTrue(loggingConfigAsJson.matches(".*\"enabled\"=true.*"));
        Assert.assertTrue(loggingConfigAsJson.matches(".*\"level\"=\"DEBUG\".*"));
    }
}