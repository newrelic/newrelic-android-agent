/*
 * Copyright (c) 2024. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.sessionReplay;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class MobileSessionReplayConfigurationTest {

    private MobileSessionReplayConfiguration config;

    @Before
    public void setUp() {
        config = new MobileSessionReplayConfiguration();
        MobileSessionReplayConfiguration.sampleSeed = 50.0;
    }

    @Test
    public void testDefaultValues() {
        Assert.assertFalse(config.isEnabled());
        Assert.assertEquals(0.0, config.getSamplingRate(), 0.0);
        Assert.assertEquals(100.0, config.getErrorSamplingRate(), 0.0);
        Assert.assertEquals("custom", config.getMode());
        Assert.assertTrue(config.isMaskApplicationText());
        Assert.assertTrue(config.isMaskUserInputText());
        Assert.assertTrue(config.isMaskAllUserTouches());
        Assert.assertTrue(config.isMaskAllImages());
        Assert.assertNotNull(config.getCustomMaskingRules());
        Assert.assertTrue(config.getCustomMaskingRules().isEmpty());
        Assert.assertEquals(TextMaskingStrategy.MASK_ALL_TEXT, config.getTextMaskingStrategy());
    }

    @Test
    public void testSetAndGetEnabled() {
        config.setEnabled(true);
        Assert.assertTrue(config.isEnabled());

        config.setEnabled(false);
        Assert.assertFalse(config.isEnabled());
    }

    @Test
    public void testSetAndGetSamplingRate() {
        config.setSamplingRate(25.5);
        Assert.assertEquals(25.5, config.getSamplingRate(), 0.0);

        config.setSamplingRate(0.0);
        Assert.assertEquals(0.0, config.getSamplingRate(), 0.0);

        config.setSamplingRate(100.0);
        Assert.assertEquals(100.0, config.getSamplingRate(), 0.0);
    }

    @Test
    public void testSetAndGetErrorSamplingRate() {
        config.setErrorSamplingRate(50.5);
        Assert.assertEquals(50.5, config.getErrorSamplingRate(), 0.0);

        config.setErrorSamplingRate(0.0);
        Assert.assertEquals(0.0, config.getErrorSamplingRate(), 0.0);

        config.setErrorSamplingRate(100.0);
        Assert.assertEquals(100.0, config.getErrorSamplingRate(), 0.0);
    }

    @Test
    public void testSetAndGetMode() {
        config.setMode("enabled");
        Assert.assertEquals("enabled", config.getMode());

        config.setMode("disabled");
        Assert.assertEquals("disabled", config.getMode());

        config.setMode(null);
        Assert.assertNull(config.getMode());
    }

    @Test
    public void testSetMaskApplicationTextWithMaskAllTextStrategy() {
        config.setMaskApplicationText(true);
        Assert.assertEquals(TextMaskingStrategy.MASK_ALL_TEXT, config.getTextMaskingStrategy());
        Assert.assertTrue(config.isMaskApplicationText());
    }

    @Test
    public void testSetMaskApplicationTextWithMaskUserInputTextStrategy() {
        config.setTextMaskingStrategy(TextMaskingStrategy.MASK_USER_INPUT_TEXT);
        config.setMaskApplicationText(false);
        Assert.assertEquals(TextMaskingStrategy.MASK_USER_INPUT_TEXT, config.getTextMaskingStrategy());
        Assert.assertFalse(config.isMaskApplicationText());
    }

    @Test
    public void testSetMaskApplicationTextWithMaskNoTextStrategy() {
        config.setTextMaskingStrategy(TextMaskingStrategy.MASK_NO_TEXT);
        config.setMaskApplicationText(false);
        config.setMaskUserInputText(false);
        Assert.assertEquals(TextMaskingStrategy.MASK_NO_TEXT, config.getTextMaskingStrategy());
        Assert.assertFalse(config.isMaskApplicationText());
    }

    @Test
    public void testIsMaskUserInputTextWithMaskAllText() {
        config.setTextMaskingStrategy(TextMaskingStrategy.MASK_ALL_TEXT);
        Assert.assertTrue(config.isMaskUserInputText());
    }

    @Test
    public void testIsMaskUserInputTextWithMaskUserInputText() {
        config.setTextMaskingStrategy(TextMaskingStrategy.MASK_USER_INPUT_TEXT);
        Assert.assertTrue(config.isMaskUserInputText());
    }

    @Test
    public void testIsMaskUserInputTextWithMaskNoText() {
        config.setTextMaskingStrategy(TextMaskingStrategy.MASK_NO_TEXT);
        Assert.assertFalse(config.isMaskUserInputText());
    }

    @Test
    public void testSetMaskUserInputTextPreservesMaskAllText() {
        config.setTextMaskingStrategy(TextMaskingStrategy.MASK_ALL_TEXT);
        config.setMaskUserInputText(true);
        Assert.assertEquals(TextMaskingStrategy.MASK_ALL_TEXT, config.getTextMaskingStrategy());

        config.setMaskUserInputText(false);
        Assert.assertEquals(TextMaskingStrategy.MASK_ALL_TEXT, config.getTextMaskingStrategy());
    }

    @Test
    public void testSetMaskUserInputTextWithUserInputStrategy() {
        config.setTextMaskingStrategy(TextMaskingStrategy.MASK_USER_INPUT_TEXT);
        config.setMaskUserInputText(true);
        Assert.assertEquals(TextMaskingStrategy.MASK_USER_INPUT_TEXT, config.getTextMaskingStrategy());

        config.setMaskUserInputText(false);
        Assert.assertEquals(TextMaskingStrategy.MASK_NO_TEXT, config.getTextMaskingStrategy());
    }

    @Test
    public void testSetMaskUserInputTextWithNoTextStrategy() {
        config.setTextMaskingStrategy(TextMaskingStrategy.MASK_NO_TEXT);
        config.setMaskUserInputText(true);
        Assert.assertEquals(TextMaskingStrategy.MASK_USER_INPUT_TEXT, config.getTextMaskingStrategy());

        config.setMaskUserInputText(false);
        Assert.assertEquals(TextMaskingStrategy.MASK_NO_TEXT, config.getTextMaskingStrategy());
    }

    @Test
    public void testSetAndGetMaskAllUserTouches() {
        config.setMaskAllUserTouches(false);
        Assert.assertFalse(config.isMaskAllUserTouches());

        config.setMaskAllUserTouches(true);
        Assert.assertTrue(config.isMaskAllUserTouches());
    }

    @Test
    public void testSetAndGetMaskAllImages() {
        config.setMaskAllImages(false);
        Assert.assertFalse(config.isMaskAllImages());

        config.setMaskAllImages(true);
        Assert.assertTrue(config.isMaskAllImages());
    }

    @Test
    public void testSetAndGetCustomMaskingRules() {
        List<MobileSessionReplayConfiguration.CustomMaskingRule> rules = new ArrayList<>();
        MobileSessionReplayConfiguration.CustomMaskingRule rule = new MobileSessionReplayConfiguration.CustomMaskingRule();
        rule.setIdentifier("tag");
        rule.setOperator("equals");
        rule.setType("mask");
        List<String> names = new ArrayList<>();
        names.add("sensitive-tag");
        rule.setName(names);
        rules.add(rule);

        config.setCustomMaskingRules(rules);
        Assert.assertEquals(1, config.getCustomMaskingRules().size());
        Assert.assertEquals("tag", config.getCustomMaskingRules().get(0).getIdentifier());
    }

    @Test
    public void testIsSessionReplayEnabledWhenDisabled() {
        config.setEnabled(false);
        MobileSessionReplayConfiguration.sampleSeed = 5.0;
        Assert.assertFalse(config.isSessionReplayEnabled());
    }

    @Test
    public void testIsSessionReplayEnabledWhenEnabledAndSampled() {
        config.setEnabled(true);
        config.setSamplingRate(100.0);
        MobileSessionReplayConfiguration.sampleSeed = 50.0;
        Assert.assertTrue(config.isSessionReplayEnabled());
    }

    @Test
    public void testIsSessionReplayEnabledWhenEnabledButNotSampled() {
        config.setEnabled(true);
        config.setSamplingRate(10.0);
        MobileSessionReplayConfiguration.sampleSeed = 50.0;
        Assert.assertFalse(config.isSessionReplayEnabled());
    }

    @Test
    public void testIsSampled() {
        config.setSamplingRate(100.0);
        MobileSessionReplayConfiguration.sampleSeed = 50.0;
        Assert.assertTrue(config.isSampled());

        config.setSamplingRate(10.0);
        MobileSessionReplayConfiguration.sampleSeed = 50.0;
        Assert.assertFalse(config.isSampled());

        config.setSamplingRate(50.0);
        MobileSessionReplayConfiguration.sampleSeed = 50.0;
        Assert.assertTrue(config.isSampled());
    }

    @Test
    public void testGetMaskedViewTags() {
        List<MobileSessionReplayConfiguration.CustomMaskingRule> rules = new ArrayList<>();

        MobileSessionReplayConfiguration.CustomMaskingRule rule1 = new MobileSessionReplayConfiguration.CustomMaskingRule();
        rule1.setIdentifier("tag");
        rule1.setOperator("equals");
        rule1.setType("mask");
        List<String> names1 = new ArrayList<>();
        names1.add("tag1");
        names1.add("tag2");
        rule1.setName(names1);
        rules.add(rule1);

        config.setCustomMaskingRules(rules);
        Assert.assertTrue(config.getMaskedViewTags().contains("tag1"));
        Assert.assertTrue(config.getMaskedViewTags().contains("tag2"));
    }

    @Test
    public void testGetUnMaskedViewTags() {
        List<MobileSessionReplayConfiguration.CustomMaskingRule> rules = new ArrayList<>();

        MobileSessionReplayConfiguration.CustomMaskingRule rule1 = new MobileSessionReplayConfiguration.CustomMaskingRule();
        rule1.setIdentifier("tag");
        rule1.setOperator("equals");
        rule1.setType("un-mask");
        List<String> names1 = new ArrayList<>();
        names1.add("safe-tag1");
        names1.add("safe-tag2");
        rule1.setName(names1);
        rules.add(rule1);

        config.setCustomMaskingRules(rules);
        Assert.assertTrue(config.getUnMaskedViewTags().contains("safe-tag1"));
        Assert.assertTrue(config.getUnMaskedViewTags().contains("safe-tag2"));
    }

    @Test
    public void testAddMaskViewClass() {
        config.addMaskViewClass("com.example.MyView");
        Assert.assertTrue(config.getMaskedViewClasses().contains("com.example.MyView"));
    }

    @Test
    public void testAddUnmaskViewClass() {
        config.addUnmaskViewClass("com.example.SafeView");
        Assert.assertTrue(config.getUnmaskedViewClasses().contains("com.example.SafeView"));
    }

    @Test
    public void testShouldMaskViewClass() {
        config.addMaskViewClass("com.example.MaskedView");
        Assert.assertTrue(config.shouldMaskViewClass("com.example.MaskedView"));
        Assert.assertFalse(config.shouldMaskViewClass("com.example.OtherView"));
    }

    @Test
    public void testShouldUnmaskViewClass() {
        config.addUnmaskViewClass("com.example.SafeView");
        Assert.assertTrue(config.shouldUnmaskViewClass("com.example.SafeView"));
        Assert.assertFalse(config.shouldUnmaskViewClass("com.example.OtherView"));
    }

    @Test
    public void testAddMaskViewTag() {
        config.addMaskViewTag("sensitive-tag");
        Assert.assertTrue(config.getMaskedViewTags().contains("sensitive-tag"));
    }

    @Test
    public void testAddUnmaskViewTag() {
        config.addUnmaskViewTag("safe-tag");
        Assert.assertTrue(config.getUnMaskedViewTags().contains("safe-tag"));
    }

    @Test
    public void testShouldMaskViewTag() {
        config.addMaskViewTag("masked-tag");
        Assert.assertTrue(config.shouldMaskViewTag("masked-tag"));
        Assert.assertFalse(config.shouldMaskViewTag("other-tag"));
    }

    @Test
    public void testShouldUnmaskViewTag() {
        config.addUnmaskViewTag("safe-tag");
        Assert.assertTrue(config.shouldUnmaskViewTag("safe-tag"));
        Assert.assertFalse(config.shouldUnmaskViewTag("other-tag"));
    }

    @Test
    public void testReseed() {
        Double seed1 = MobileSessionReplayConfiguration.reseed();
        Assert.assertNotNull(seed1);
        Assert.assertTrue(seed1 >= 0.0 && seed1 <= 100.0);

        Double seed2 = MobileSessionReplayConfiguration.reseed();
        Assert.assertNotNull(seed2);
        Assert.assertTrue(seed2 >= 0.0 && seed2 <= 100.0);
    }

    @Test
    public void testSetConfiguration() {
        MobileSessionReplayConfiguration newConfig = new MobileSessionReplayConfiguration();
        newConfig.setEnabled(true);
        newConfig.setSamplingRate(50.0);
        newConfig.setErrorSamplingRate(75.0);
        newConfig.setMode("test-mode");
        newConfig.setMaskAllUserTouches(false);
        newConfig.setMaskAllImages(false);

        config.setConfiguration(newConfig);

        Assert.assertTrue(config.isEnabled());
        Assert.assertEquals(50.0, config.getSamplingRate(), 0.0);
        Assert.assertEquals(75.0, config.getErrorSamplingRate(), 0.0);
        Assert.assertEquals("test-mode", config.getMode());
        Assert.assertFalse(config.isMaskAllUserTouches());
        Assert.assertFalse(config.isMaskAllImages());
    }

    @Test
    public void testSetConfigurationWithSameConfiguration() {
        MobileSessionReplayConfiguration originalConfig = new MobileSessionReplayConfiguration();
        MobileSessionReplayConfiguration sameConfig = new MobileSessionReplayConfiguration();

        originalConfig.setConfiguration(sameConfig);
        // Should not throw exception and should remain unchanged
        Assert.assertEquals(originalConfig, sameConfig);
    }

    @Test
    public void testEqualsWithSameObject() {
        Assert.assertTrue(config.equals(config));
    }

    @Test
    public void testEqualsWithNull() {
        Assert.assertFalse(config.equals(null));
    }

    @Test
    public void testEqualsWithDifferentClass() {
        Assert.assertFalse(config.equals("string"));
    }

    @Test
    public void testEqualsWithEqualConfiguration() {
        MobileSessionReplayConfiguration config2 = new MobileSessionReplayConfiguration();
        Assert.assertTrue(config.equals(config2));
    }

    @Test
    public void testEqualsWithDifferentConfiguration() {
        MobileSessionReplayConfiguration config2 = new MobileSessionReplayConfiguration();
        config2.setEnabled(true);
        Assert.assertFalse(config.equals(config2));
    }

    @Test
    public void testHashCode() {
        MobileSessionReplayConfiguration config2 = new MobileSessionReplayConfiguration();
        Assert.assertEquals(config.hashCode(), config2.hashCode());

        config.setEnabled(true);
        Assert.assertNotEquals(config.hashCode(), config2.hashCode());
    }

    @Test
    public void testSetAndGetTextMaskingStrategy() {
        config.setTextMaskingStrategy(TextMaskingStrategy.MASK_USER_INPUT_TEXT);
        Assert.assertEquals(TextMaskingStrategy.MASK_USER_INPUT_TEXT, config.getTextMaskingStrategy());

        config.setTextMaskingStrategy(TextMaskingStrategy.MASK_NO_TEXT);
        Assert.assertEquals(TextMaskingStrategy.MASK_NO_TEXT, config.getTextMaskingStrategy());

        config.setTextMaskingStrategy(TextMaskingStrategy.MASK_ALL_TEXT);
        Assert.assertEquals(TextMaskingStrategy.MASK_ALL_TEXT, config.getTextMaskingStrategy());
    }

    @Test
    public void testGetMaskedViewClassesInitializesIfNull() {
        Assert.assertNotNull(config.getMaskedViewClasses());
        Assert.assertTrue(config.getMaskedViewClasses().isEmpty());
    }

    @Test
    public void testGetUnmaskedViewClassesInitializesIfNull() {
        Assert.assertNotNull(config.getUnmaskedViewClasses());
        Assert.assertTrue(config.getUnmaskedViewClasses().isEmpty());
    }

    @Test
    public void testCustomMaskingRuleGettersAndSetters() {
        MobileSessionReplayConfiguration.CustomMaskingRule rule = new MobileSessionReplayConfiguration.CustomMaskingRule();

        rule.setIdentifier("test-identifier");
        Assert.assertEquals("test-identifier", rule.getIdentifier());

        rule.setOperator("equals");
        Assert.assertEquals("equals", rule.getOperator());

        rule.setType("mask");
        Assert.assertEquals("mask", rule.getType());

        List<String> names = new ArrayList<>();
        names.add("name1");
        rule.setName(names);
        Assert.assertEquals(1, rule.getName().size());
        Assert.assertEquals("name1", rule.getName().get(0));
    }

    @Test
    public void testCustomMaskingRuleEquals() {
        MobileSessionReplayConfiguration.CustomMaskingRule rule1 = new MobileSessionReplayConfiguration.CustomMaskingRule();
        rule1.setIdentifier("id");
        rule1.setOperator("equals");
        rule1.setType("mask");

        MobileSessionReplayConfiguration.CustomMaskingRule rule2 = new MobileSessionReplayConfiguration.CustomMaskingRule();
        rule2.setIdentifier("id");
        rule2.setOperator("equals");
        rule2.setType("mask");

        Assert.assertTrue(rule1.equals(rule2));
        Assert.assertEquals(rule1.hashCode(), rule2.hashCode());
    }

    @Test
    public void testCustomMaskingRuleNotEquals() {
        MobileSessionReplayConfiguration.CustomMaskingRule rule1 = new MobileSessionReplayConfiguration.CustomMaskingRule();
        rule1.setIdentifier("id1");

        MobileSessionReplayConfiguration.CustomMaskingRule rule2 = new MobileSessionReplayConfiguration.CustomMaskingRule();
        rule2.setIdentifier("id2");

        Assert.assertFalse(rule1.equals(rule2));
    }
}