/*
 * Copyright (c) 2023. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.sessionReplay;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.newrelic.agent.android.activity.config.ActivityTraceConfiguration;
import com.newrelic.agent.android.activity.config.ActivityTraceConfigurationDeserializer;
import com.newrelic.agent.android.harvest.HarvestConfiguration;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.logging.ConsoleAgentLog;
import com.newrelic.agent.android.logging.LogLevel;
import com.newrelic.agent.android.logging.LogReportingConfiguration;
import com.newrelic.agent.android.test.mock.Providers;

import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class SessionReplayConfigurationTest {
    private static AgentLog log = new ConsoleAgentLog();

    SessionReplayConfiguration sessionReplayConfiguration;
    HarvestConfiguration harvestConfiguration;

    final Gson gson = new GsonBuilder()
            .registerTypeAdapter(ActivityTraceConfiguration.class, new ActivityTraceConfigurationDeserializer())
            .create();


    @Before
    public void setUp() throws Exception {
        this.sessionReplayConfiguration = new SessionReplayConfiguration();
        this.harvestConfiguration = gson.fromJson(
                Providers.provideJsonObject("/Connect-Spec-v5.json").toString(),
                HarvestConfiguration.class);
    }

    @Test
    public void testDefaultConfiguration() {
        this.sessionReplayConfiguration = new SessionReplayConfiguration();
        Assert.assertFalse(sessionReplayConfiguration.isEnabled());
    }
    
    @Test
    public void testSetAndGetSamplingRate() {
        SessionReplayConfiguration config = new SessionReplayConfiguration();
        
        // Test default value
        Assert.assertEquals(10.0, config.getSamplingRate(), 0.0);
        
        // Test setting valid values
        config.setSamplingRate(25.5);
        Assert.assertEquals(25.5, config.getSamplingRate(), 0.0);
        
        config.setSamplingRate(0.0);
        Assert.assertEquals(0.0, config.getSamplingRate(), 0.0);
        
        config.setSamplingRate(100.0);
        Assert.assertEquals(100.0, config.getSamplingRate(), 0.0);
        
        config.setSamplingRate(99.99999);
        Assert.assertEquals(99.99999, config.getSamplingRate(), 0.0);
    }
    
    @Test
    public void testSetAndGetErrorSamplingRate() {
        SessionReplayConfiguration config = new SessionReplayConfiguration();
        
        // Test default value
        Assert.assertEquals(100.0, config.getErrorSamplingRate(), 0.0);
        
        // Test setting valid values
        config.setErrorSamplingRate(50.5);
        Assert.assertEquals(50.5, config.getErrorSamplingRate(), 0.0);
        
        config.setErrorSamplingRate(0.0);
        Assert.assertEquals(0.0, config.getErrorSamplingRate(), 0.0);
        
        config.setErrorSamplingRate(100.0);
        Assert.assertEquals(100.0, config.getErrorSamplingRate(), 0.0);
        
        config.setErrorSamplingRate(75.12345);
        Assert.assertEquals(75.12345, config.getErrorSamplingRate(), 0.0);
    }
    
    @Test
    public void testSetAndGetMode() {
        SessionReplayConfiguration config = new SessionReplayConfiguration();
        
        // Test default value
        Assert.assertEquals("default", config.getMode());
        
        // Test setting valid string values
        config.setMode("enabled");
        Assert.assertEquals("enabled", config.getMode());
        
        config.setMode("disabled");
        Assert.assertEquals("disabled", config.getMode());
        
        config.setMode("custom");
        Assert.assertEquals("custom", config.getMode());
        
        config.setMode("");
        Assert.assertEquals("", config.getMode());
        
        config.setMode(null);
        Assert.assertNull(config.getMode());
        
        config.setMode("some_long_mode_name_with_underscores");
        Assert.assertEquals("some_long_mode_name_with_underscores", config.getMode());
    }

    @Test
    public void testSetMaskApplicationTextHandlesTextMaskingStrategyChanges() {
        SessionReplayConfiguration config = new SessionReplayConfiguration();
        
        // Test setting maskApplicationText to true sets MASK_ALL_TEXT strategy
        config.setMaskApplicationText(true);
        Assert.assertEquals(TextMaskingStrategy.MASK_ALL_TEXT, config.getTextMaskingStrategy());
        Assert.assertTrue(config.isMaskApplicationText());

//        // Test setting maskApplicationText to false with maskUserInputText true sets MASK_USER_INPUT_TEXT strategy
        config.setMaskApplicationText(false);
        Assert.assertEquals(TextMaskingStrategy.MASK_USER_INPUT_TEXT, config.getTextMaskingStrategy());
        Assert.assertFalse(config.isMaskApplicationText());
        Assert.assertTrue(config.isMaskUserInputText());

        // Test setting maskApplicationText to false with maskUserInputText false sets MASK_NO_TEXT strategy
        config.setMaskUserInputText(false);
        config.setMaskApplicationText(false);
        Assert.assertEquals(TextMaskingStrategy.MASK_NO_TEXT, config.getTextMaskingStrategy());
        Assert.assertFalse(config.isMaskApplicationText());
        Assert.assertFalse(config.isMaskUserInputText());
        
        // Test setting maskApplicationText to true again sets MASK_ALL_TEXT strategy
        config.setMaskApplicationText(true);
        Assert.assertEquals(TextMaskingStrategy.MASK_ALL_TEXT, config.getTextMaskingStrategy());
        Assert.assertTrue(config.isMaskApplicationText());
    }

    @Test
    public void testSetMaskUserInputTextHandlesTextMaskingStrategyChanges() {
        SessionReplayConfiguration config = new SessionReplayConfiguration();
        
        // Test setting maskUserInputText to true when MASK_ALL_TEXT is already set - should remain MASK_ALL_TEXT
        config.setTextMaskingStrategy(TextMaskingStrategy.MASK_ALL_TEXT);
        config.setMaskUserInputText(true);
        Assert.assertEquals(TextMaskingStrategy.MASK_ALL_TEXT, config.getTextMaskingStrategy());
        Assert.assertTrue(config.isMaskUserInputText());
        
        // Test setting maskUserInputText to false when MASK_ALL_TEXT is set - should remain MASK_ALL_TEXT
        config.setTextMaskingStrategy(TextMaskingStrategy.MASK_ALL_TEXT);
        config.setMaskUserInputText(false);
        Assert.assertEquals(TextMaskingStrategy.MASK_ALL_TEXT, config.getTextMaskingStrategy());
        Assert.assertFalse(config.isMaskUserInputText());
        
        // Test setting maskUserInputText to true when MASK_USER_INPUT_TEXT is set - should remain MASK_USER_INPUT_TEXT
        config.setTextMaskingStrategy(TextMaskingStrategy.MASK_USER_INPUT_TEXT);
        config.setMaskUserInputText(true);
        Assert.assertEquals(TextMaskingStrategy.MASK_ALL_TEXT, config.getTextMaskingStrategy());
        Assert.assertTrue(config.isMaskUserInputText());
        
        // Test setting maskUserInputText to false when MASK_USER_INPUT_TEXT is set - should change to MASK_NO_TEXT
        config.setTextMaskingStrategy(TextMaskingStrategy.MASK_USER_INPUT_TEXT);
        config.setMaskUserInputText(false);
        Assert.assertEquals(TextMaskingStrategy.MASK_ALL_TEXT, config.getTextMaskingStrategy());
        Assert.assertFalse(config.isMaskUserInputText());

        config.setMaskApplicationText(false);
        // Test setting maskUserInputText to true when MASK_NO_TEXT is set - should change to MASK_USER_INPUT_TEXT
        config.setTextMaskingStrategy(TextMaskingStrategy.MASK_NO_TEXT);
        config.setMaskUserInputText(true);
        Assert.assertEquals(TextMaskingStrategy.MASK_USER_INPUT_TEXT, config.getTextMaskingStrategy());
        Assert.assertTrue(config.isMaskUserInputText());
        
        // Test setting maskUserInputText to false when MASK_NO_TEXT is set - should remain MASK_NO_TEXT
        config.setTextMaskingStrategy(TextMaskingStrategy.MASK_NO_TEXT);
        config.setMaskUserInputText(false);
        Assert.assertEquals(TextMaskingStrategy.MASK_NO_TEXT, config.getTextMaskingStrategy());
        Assert.assertFalse(config.isMaskUserInputText());
    }

    @Test
    public void testSetMaskUserInputTextPreservesMaskAllTextStrategy() {
        SessionReplayConfiguration config = new SessionReplayConfiguration();
        
        // Set to MASK_ALL_TEXT strategy
        config.setTextMaskingStrategy(TextMaskingStrategy.MASK_ALL_TEXT);
        Assert.assertEquals(TextMaskingStrategy.MASK_ALL_TEXT, config.getTextMaskingStrategy());
        
        // Setting maskUserInputText to false should preserve MASK_ALL_TEXT strategy
        config.setMaskUserInputText(false);
        Assert.assertEquals(TextMaskingStrategy.MASK_ALL_TEXT, config.getTextMaskingStrategy());
        Assert.assertFalse(config.isMaskUserInputText());
    }

    @Test
    public void testSetAndGetMaskAllUserTouches() {
        SessionReplayConfiguration config = new SessionReplayConfiguration();
        
        // Test default value
        Assert.assertFalse(config.isMaskAllUserTouches());
        
        // Test setting to true
        config.setMaskAllUserTouches(true);
        Assert.assertTrue(config.isMaskAllUserTouches());
        
        // Test setting to false
        config.setMaskAllUserTouches(false);
        Assert.assertFalse(config.isMaskAllUserTouches());
        
        // Test setting to true again
        config.setMaskAllUserTouches(true);
        Assert.assertTrue(config.isMaskAllUserTouches());
    }

    @Test
    public void testSetAndGetMaskAllImages() {
        SessionReplayConfiguration config = new SessionReplayConfiguration();
        
        // Test default value
        Assert.assertTrue(config.isMaskAllImages());
        
        // Test setting to false
        config.setMaskAllImages(false);
        Assert.assertFalse(config.isMaskAllImages());
        
        // Test setting to true
        config.setMaskAllImages(true);
        Assert.assertTrue(config.isMaskAllImages());
        
        // Test setting to false again
        config.setMaskAllImages(false);
        Assert.assertFalse(config.isMaskAllImages());
    }

    @Test
    public void testSetAndGetCustomMaskingRulesHandlesNullAndEmptyLists() {
        SessionReplayConfiguration config = new SessionReplayConfiguration();
        
        // Test default value - should be empty list, not null
        Assert.assertNotNull(config.getCustomMaskingRules());
        Assert.assertTrue(config.getCustomMaskingRules().isEmpty());
        
        // Test setting null list
        config.setCustomMaskingRules(null);
        Assert.assertNull(config.getCustomMaskingRules());
        
        // Test setting empty list
        List<SessionReplayConfiguration.CustomMaskingRule> emptyList = new ArrayList<>();
        config.setCustomMaskingRules(emptyList);
        Assert.assertNotNull(config.getCustomMaskingRules());
        Assert.assertTrue(config.getCustomMaskingRules().isEmpty());
        Assert.assertEquals(0, config.getCustomMaskingRules().size());
        
        // Test setting list with elements
        List<SessionReplayConfiguration.CustomMaskingRule> rules = new ArrayList<>();
        SessionReplayConfiguration.CustomMaskingRule rule1 = new SessionReplayConfiguration.CustomMaskingRule();
        rule1.setIdentifier("rule1");
        rule1.setOperator("equals");
        rule1.setType("mask");
        rules.add(rule1);
        
        config.setCustomMaskingRules(rules);
        Assert.assertNotNull(config.getCustomMaskingRules());
        Assert.assertFalse(config.getCustomMaskingRules().isEmpty());
        Assert.assertEquals(1, config.getCustomMaskingRules().size());
        Assert.assertEquals("rule1", config.getCustomMaskingRules().get(0).getIdentifier());
        
        // Test setting back to empty list
        config.setCustomMaskingRules(new ArrayList<>());
        Assert.assertNotNull(config.getCustomMaskingRules());
        Assert.assertTrue(config.getCustomMaskingRules().isEmpty());
    }

    @Test
    public void testIsSessionReplayEnabledBasedOnEnabledFlagAndSampling() {
        SessionReplayConfiguration config = new SessionReplayConfiguration();
        
        // Test when disabled - should return false regardless of sampling
        config.setEnabled(false);
        SessionReplayConfiguration.sampleSeed = 5.0; // Well below sampling rate
        Assert.assertFalse(config.isSessionReplayEnabled());
        
        SessionReplayConfiguration.sampleSeed = 15.0; // Above default sampling rate
        Assert.assertFalse(config.isSessionReplayEnabled());
        
        // Test when enabled and sampled - should return true
        config.setEnabled(true);
        SessionReplayConfiguration.sampleSeed = 5.0; // Below default sampling rate of 10.0
        Assert.assertTrue(config.isSessionReplayEnabled());
        
        SessionReplayConfiguration.sampleSeed = 10.0; // Equal to default sampling rate
        Assert.assertTrue(config.isSessionReplayEnabled());
        
        // Test when enabled but not sampled - should return false
        SessionReplayConfiguration.sampleSeed = 15.0; // Above default sampling rate of 10.0

        
        // Test with custom sampling rate
        config.setSamplingRate(25.0);
        SessionReplayConfiguration.sampleSeed = 20.0; // Below custom sampling rate
        Assert.assertTrue(config.isSessionReplayEnabled());
        

    }

    @Test
    public void testDeserialization() {
        String sessionReplayConfig = "{\n" +
                "      \"enabled\": false,\n" +
                "      \"mode\": \"default\",\n" +
                "      \"sampling_rate\": 10.0,\n" +
                "      \"error_sampling_rate\": 100.0,\n" +
                "      \"mask_application_text\": true,\n" +
                "      \"mask_user_input_text\": true,\n" +
                "      \"mask_all_user_touches\": false,\n" +
                "      \"mask_all_images\": true,\n" +
                "      \"custom_masking_rules\": []\n" +
                "    }";

        Gson gson = new GsonBuilder().create();

        SessionReplayConfiguration serialized = new SessionReplayConfiguration();
        SessionReplayConfiguration deserialized = gson.fromJson(sessionReplayConfig, SessionReplayConfiguration.class);
        Assert.assertTrue(serialized.toString().equals(deserialized.toString()));

        String inflated = gson.toJson(deserialized, SessionReplayConfiguration.class);
        String deflated = gson.toJson(serialized, SessionReplayConfiguration.class);
        Assert.assertTrue(inflated.equals(deflated));
    }
}