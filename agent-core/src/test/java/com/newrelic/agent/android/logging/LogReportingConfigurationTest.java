/*
 * Copyright (c) 2023. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.logging;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.newrelic.agent.android.activity.config.ActivityTraceConfiguration;
import com.newrelic.agent.android.activity.config.ActivityTraceConfigurationDeserializer;
import com.newrelic.agent.android.harvest.HarvestConfiguration;
import com.newrelic.agent.android.test.mock.Providers;

import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

public class LogReportingConfigurationTest {
    private static AgentLog log = new ConsoleAgentLog();

    LogReportingConfiguration logReportingConfig;
    HarvestConfiguration harvestConfiguration;

    final Gson gson = new GsonBuilder()
            .registerTypeAdapter(ActivityTraceConfiguration.class, new ActivityTraceConfigurationDeserializer())
            .create();

    @BeforeClass
    public static void beforeClass() throws Exception {
        log.setLevel(AgentLog.DEBUG);
        AgentLogManager.setAgentLog(log);
    }

    @Before
    public void setUp() throws Exception {
        this.logReportingConfig = new LogReportingConfiguration(true, LogLevel.DEBUG);
        this.harvestConfiguration = gson.fromJson(
                Providers.provideJsonObject("/Connect-Spec-v5.json").toString(),
                HarvestConfiguration.class);
    }

    @Test
    public void testDefaultConfiguration() {
        this.logReportingConfig = new LogReportingConfiguration();
        Assert.assertFalse(logReportingConfig.getLoggingEnabled());
        Assert.assertEquals(LogLevel.NONE, logReportingConfig.getLogLevel());
        Assert.assertTrue(logReportingConfig.isSampled());
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
                "  \"data_report_period\": 15," +
                "  \"expiration_period\": 3600," +
                "  \"sampling_rate\": 69" +
                "}";

        Gson gson = new GsonBuilder().create();

        LogReportingConfiguration serialized = new LogReportingConfiguration(true, LogLevel.VERBOSE, 15, 3600, 69);
        LogReportingConfiguration deserialized = gson.fromJson(loggingConfig, LogReportingConfiguration.class);
        Assert.assertTrue(serialized.toString().equals(deserialized.toString()));

        String inflated = gson.toJson(deserialized, LogReportingConfiguration.class);
        String deflated = gson.toJson(serialized, LogReportingConfiguration.class);
        Assert.assertTrue(inflated.equals(deflated));
    }

    @Test
    public void testSampleRate() {
        LogReportingConfiguration config = new LogReportingConfiguration(true, LogLevel.VERBOSE, 15, 3600, 33);

        LogReportingConfiguration.sampleSeed = 10.0;
        Assert.assertTrue(config.getLoggingEnabledAndSessionSampled());

        LogReportingConfiguration.sampleSeed = 100.0;
        Assert.assertFalse(config.getLoggingEnabledAndSessionSampled());

        config.enabled = false;
        Assert.assertFalse(config.getLoggingEnabledAndSessionSampled());
    }

    @Test
    public void testSampleRateValues() {
        Assert.assertTrue(LogReportingConfiguration.sampleSeed >= 0 && LogReportingConfiguration.sampleSeed <= 100);

        Assert.assertEquals(0.0, new LogReportingConfiguration(true, LogLevel.VERBOSE, 15, 3600, -330).sampleRate, 0.0);
        Assert.assertEquals(100.0, new LogReportingConfiguration(true, LogLevel.VERBOSE, 15, 3600, 330).sampleRate, 0.0);
    }

    /**
     * Not really a test, but a check on assumptions re: sample randomness.
     */
    @Ignore("For analysis")
    @Test
    public void testRandomDistribution() {
        final int SESSIONS = 1000;
        final int CONFIG_UPDATES = 100;
        LogReportingConfiguration loggingConfiguration;

        log.debug("Assumed sample rate[" + harvestConfiguration.getRemote_configuration().getLogReportingConfiguration().sampleRate + "]");

        for (int configUpdate = 0; configUpdate < CONFIG_UPDATES; configUpdate++) {
            int heads = 0;
            int tails = 0;
            int variance = 0;

            loggingConfiguration =
                    new LogReportingConfiguration(true, LogLevel.DEBUG, 10, 10, (int) (Math.random() * 100.) + 1);

            for (int session = 0; session < SESSIONS; session++) {
                LogReportingConfiguration.reseed();
                if (loggingConfiguration.isSampled()) {
                    heads++;
                } else {
                    tails++;
                }
                variance += (loggingConfiguration.isSampled() ? 1 : -1);
            }

            log.debug("Sample rate distribution [" + loggingConfiguration.sampleRate + "%] " +
                    "effective[" + (heads * 100 / SESSIONS) + "%] " +
                    "enabled[" + heads + "] " +
                    "disabled[" + tails + "] " +
                    "variance[" + variance + "]");

            // Assert.assertFalse(loggingConfiguration.sampleRate < (heads * 100 / SESSIONS));
        }
    }

    @Ignore("For analysis")
    @Test
    public void testReseed() {
        double sampleRate = LogReportingConfiguration.sampleSeed;
        double newRate = LogReportingConfiguration.reseed();
        Assert.assertNotEquals(newRate, sampleRate);

        int timesUntilDuplicate = 1;
        while (newRate != LogReportingConfiguration.reseed()) {
            timesUntilDuplicate++;
            if (timesUntilDuplicate == Integer.MAX_VALUE) {
                break;
            }
        }
        log.debug("Reseed took [" + timesUntilDuplicate + "] calls to generate a duplicate seed.");
    }

    @Test
    public void testEquals() {
        LogReportingConfiguration remoteConfig = harvestConfiguration.getRemote_configuration().getLogReportingConfiguration();
        LogReportingConfiguration localConfiguration = new LogReportingConfiguration(true, LogLevel.DEBUG);

        Assert.assertEquals(remoteConfig, remoteConfig);
        Assert.assertEquals(localConfiguration, localConfiguration);
        Assert.assertNotEquals(remoteConfig, localConfiguration);

    }

    @Test
    public void testSamplingOverride_isSampledReturnsTrueWhenOverrideSet() {
        // Config with 0% sample rate — normally not sampled
        LogReportingConfiguration config = new LogReportingConfiguration(true, LogLevel.DEBUG, 30, 3600, 0);
        LogReportingConfiguration.sampleSeed = 50.0;

        Assert.assertFalse("Should not be sampled without override", config.isSampled());

        config.setSamplingOverride(true);
        Assert.assertTrue("Should be sampled with override", config.isSampled());
        Assert.assertTrue("isSamplingOverridden should return true", config.isSamplingOverridden());
    }

    @Test
    public void testSamplingOverride_getLoggingEnabledAndSessionSampledRespectsOverride() {
        LogReportingConfiguration config = new LogReportingConfiguration(true, LogLevel.DEBUG, 30, 3600, 0);
        LogReportingConfiguration.sampleSeed = 50.0;

        Assert.assertFalse("Logging+sampling should be disabled (not sampled)", config.getLoggingEnabledAndSessionSampled());

        config.setSamplingOverride(true);
        Assert.assertTrue("Logging+sampling should be enabled (override active)", config.getLoggingEnabledAndSessionSampled());
    }

    @Test
    public void testSamplingOverride_clearOverrideRestoresNormalBehavior() {
        LogReportingConfiguration config = new LogReportingConfiguration(true, LogLevel.DEBUG, 30, 3600, 0);
        LogReportingConfiguration.sampleSeed = 50.0;

        config.setSamplingOverride(true);
        Assert.assertTrue(config.isSampled());

        config.setSamplingOverride(false);
        Assert.assertFalse("Should revert to normal sampling after override cleared", config.isSampled());
        Assert.assertFalse(config.isSamplingOverridden());
    }

    @Test
    public void testSamplingOverride_setConfigurationDoesNotCopyOverride() {
        LogReportingConfiguration source = new LogReportingConfiguration(true, LogLevel.DEBUG, 30, 3600, 50);
        source.setSamplingOverride(true);

        LogReportingConfiguration target = new LogReportingConfiguration(true, LogLevel.DEBUG, 30, 3600, 0);
        LogReportingConfiguration.sampleSeed = 100.0;
        target.setConfiguration(source);

        Assert.assertFalse("Override should not be copied via setConfiguration", target.isSamplingOverridden());
    }

    @Test
    public void testSamplingOverride_normalSamplingStillWorksWithoutOverride() {
        LogReportingConfiguration config = new LogReportingConfiguration(true, LogLevel.DEBUG, 30, 3600, 75);

        LogReportingConfiguration.sampleSeed = 50.0;
        Assert.assertTrue("Should be sampled: seed 50 <= rate 75", config.isSampled());
        Assert.assertFalse("Override should not be set", config.isSamplingOverridden());

        LogReportingConfiguration.sampleSeed = 80.0;
        Assert.assertFalse("Should not be sampled: seed 80 > rate 75", config.isSampled());
    }
}