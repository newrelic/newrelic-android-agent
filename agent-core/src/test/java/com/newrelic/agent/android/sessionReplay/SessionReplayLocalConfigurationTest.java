/*
 * Copyright (c) 2024. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.sessionReplay;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Set;

public class SessionReplayLocalConfigurationTest {

    private SessionReplayLocalConfiguration config;

    @Before
    public void setUp() {
        config = new SessionReplayLocalConfiguration();
    }

    @Test
    public void testDefaultValues() {
        Assert.assertFalse(config.isMaskApplicationText());
        Assert.assertFalse(config.isMaskUserInputText());
        Assert.assertNotNull(config.getMaskedViewClasses());
        Assert.assertNotNull(config.getUnmaskedViewClasses());
        Assert.assertNotNull(config.getMaskedViewTags());
        Assert.assertNotNull(config.getUnmaskedViewTags());
        Assert.assertTrue(config.getMaskedViewClasses().isEmpty());
        Assert.assertTrue(config.getUnmaskedViewClasses().isEmpty());
        Assert.assertTrue(config.getMaskedViewTags().isEmpty());
        Assert.assertTrue(config.getUnmaskedViewTags().isEmpty());
    }

    @Test
    public void testAddMaskViewClass() {
        Assert.assertTrue(config.getMaskedViewClasses().isEmpty());

        config.addMaskViewClass("com.example.MyView");
        Assert.assertEquals(1, config.getMaskedViewClasses().size());
        Assert.assertTrue(config.getMaskedViewClasses().contains("com.example.MyView"));

        config.addMaskViewClass("com.example.AnotherView");
        Assert.assertEquals(2, config.getMaskedViewClasses().size());
        Assert.assertTrue(config.getMaskedViewClasses().contains("com.example.MyView"));
        Assert.assertTrue(config.getMaskedViewClasses().contains("com.example.AnotherView"));
    }

    @Test
    public void testAddMaskViewClassWithDuplicates() {
        config.addMaskViewClass("com.example.MyView");
        config.addMaskViewClass("com.example.MyView");

        // Set should not contain duplicates
        Assert.assertEquals(1, config.getMaskedViewClasses().size());
        Assert.assertTrue(config.getMaskedViewClasses().contains("com.example.MyView"));
    }

    @Test
    public void testAddUnmaskViewClass() {
        Assert.assertTrue(config.getUnmaskedViewClasses().isEmpty());

        config.addUnmaskViewClass("com.example.SafeView");
        Assert.assertEquals(1, config.getUnmaskedViewClasses().size());
        Assert.assertTrue(config.getUnmaskedViewClasses().contains("com.example.SafeView"));

        config.addUnmaskViewClass("com.example.AnotherSafeView");
        Assert.assertEquals(2, config.getUnmaskedViewClasses().size());
        Assert.assertTrue(config.getUnmaskedViewClasses().contains("com.example.SafeView"));
        Assert.assertTrue(config.getUnmaskedViewClasses().contains("com.example.AnotherSafeView"));
    }

    @Test
    public void testAddMaskViewTag() {
        Assert.assertTrue(config.getMaskedViewTags().isEmpty());

        config.addMaskViewTag("sensitive-tag");
        Assert.assertEquals(1, config.getMaskedViewTags().size());
        Assert.assertTrue(config.getMaskedViewTags().contains("sensitive-tag"));

        config.addMaskViewTag("another-sensitive-tag");
        Assert.assertEquals(2, config.getMaskedViewTags().size());
        Assert.assertTrue(config.getMaskedViewTags().contains("sensitive-tag"));
        Assert.assertTrue(config.getMaskedViewTags().contains("another-sensitive-tag"));
    }

    @Test
    public void testAddUnmaskViewTag() {
        Assert.assertTrue(config.getUnmaskedViewTags().isEmpty());

        config.addUnmaskViewTag("safe-tag");
        Assert.assertEquals(1, config.getUnmaskedViewTags().size());
        Assert.assertTrue(config.getUnmaskedViewTags().contains("safe-tag"));

        config.addUnmaskViewTag("another-safe-tag");
        Assert.assertEquals(2, config.getUnmaskedViewTags().size());
        Assert.assertTrue(config.getUnmaskedViewTags().contains("safe-tag"));
        Assert.assertTrue(config.getUnmaskedViewTags().contains("another-safe-tag"));
    }

    @Test
    public void testShouldMaskViewTag() {
        Assert.assertFalse(config.shouldMaskViewTag("any-tag"));

        config.addMaskViewTag("sensitive-tag");
        Assert.assertTrue(config.shouldMaskViewTag("sensitive-tag"));
        Assert.assertFalse(config.shouldMaskViewTag("other-tag"));
    }

    @Test
    public void testShouldUnmaskViewTag() {
        Assert.assertFalse(config.shouldUnmaskViewTag("any-tag"));

        config.addUnmaskViewTag("safe-tag");
        Assert.assertTrue(config.shouldUnmaskViewTag("safe-tag"));
        Assert.assertFalse(config.shouldUnmaskViewTag("other-tag"));
    }

    @Test
    public void testShouldMaskViewTagWithNull() {
        config.addMaskViewTag("sensitive-tag");
        Assert.assertFalse(config.shouldMaskViewTag(null));
    }

    @Test
    public void testShouldUnmaskViewTagWithNull() {
        config.addUnmaskViewTag("safe-tag");
        Assert.assertFalse(config.shouldUnmaskViewTag(null));
    }

    @Test
    public void testGetMaskedViewClassesInitializesIfNull() {
        // Ensure getting masked view classes never returns null
        Set<String> maskedClasses = config.getMaskedViewClasses();
        Assert.assertNotNull(maskedClasses);
        Assert.assertTrue(maskedClasses.isEmpty());
    }

    @Test
    public void testGetUnmaskedViewClassesInitializesIfNull() {
        // Ensure getting unmasked view classes never returns null
        Set<String> unmaskedClasses = config.getUnmaskedViewClasses();
        Assert.assertNotNull(unmaskedClasses);
        Assert.assertTrue(unmaskedClasses.isEmpty());
    }

    @Test
    public void testGetMaskedViewTagsInitializesIfNull() {
        // Ensure getting masked view tags never returns null
        Set<String> maskedTags = config.getMaskedViewTags();
        Assert.assertNotNull(maskedTags);
        Assert.assertTrue(maskedTags.isEmpty());
    }

    @Test
    public void testGetUnmaskedViewTagsInitializesIfNull() {
        // Ensure getting unmasked view tags never returns null
        Set<String> unmaskedTags = config.getUnmaskedViewTags();
        Assert.assertNotNull(unmaskedTags);
        Assert.assertTrue(unmaskedTags.isEmpty());
    }

    @Test
    public void testMixedViewClassAndTagOperations() {
        // Add various masked and unmasked items
        config.addMaskViewClass("com.example.MaskedView");
        config.addUnmaskViewClass("com.example.UnmaskedView");
        config.addMaskViewTag("masked-tag");
        config.addUnmaskViewTag("unmasked-tag");

        // Verify all collections are independent
        Assert.assertEquals(1, config.getMaskedViewClasses().size());
        Assert.assertEquals(1, config.getUnmaskedViewClasses().size());
        Assert.assertEquals(1, config.getMaskedViewTags().size());
        Assert.assertEquals(1, config.getUnmaskedViewTags().size());

        Assert.assertTrue(config.getMaskedViewClasses().contains("com.example.MaskedView"));
        Assert.assertTrue(config.getUnmaskedViewClasses().contains("com.example.UnmaskedView"));
        Assert.assertTrue(config.shouldMaskViewTag("masked-tag"));
        Assert.assertTrue(config.shouldUnmaskViewTag("unmasked-tag"));
    }

    @Test
    public void testAddViewClassWithEmptyString() {
        config.addMaskViewClass("");
        Assert.assertEquals(1, config.getMaskedViewClasses().size());
        Assert.assertTrue(config.getMaskedViewClasses().contains(""));
    }

    @Test
    public void testAddViewTagWithEmptyString() {
        config.addMaskViewTag("");
        Assert.assertEquals(1, config.getMaskedViewTags().size());
        Assert.assertTrue(config.getMaskedViewTags().contains(""));
    }

    @Test
    public void testMultipleAdditionsToSameSet() {
        // Add multiple classes
        for (int i = 0; i < 10; i++) {
            config.addMaskViewClass("com.example.View" + i);
        }
        Assert.assertEquals(10, config.getMaskedViewClasses().size());

        // Add multiple tags
        for (int i = 0; i < 10; i++) {
            config.addMaskViewTag("tag-" + i);
        }
        Assert.assertEquals(10, config.getMaskedViewTags().size());
    }

    @Test
    public void testRemoteMaskAppTextTrueLocalFalseDefaultShouldMask() {
        // Remote enables application text masking; unset local must not cancel it.
        SessionReplayConfiguration remoteConfig = new SessionReplayConfiguration();
        remoteConfig.setMaskApplicationText(true);

        boolean shouldMaskApp = remoteConfig.isMaskApplicationText() || config.isMaskApplicationText();

        Assert.assertTrue("Remote maskApplicationText=true must result in masking when local is unset", shouldMaskApp);
    }

    @Test
    public void testRemoteMaskUserInputTrueLocalFalseDefaultShouldMask() {
        // Remote enables user input masking; unset local must not cancel it.
        SessionReplayConfiguration remoteConfig = new SessionReplayConfiguration();
        remoteConfig.setMaskApplicationText(false);
        remoteConfig.setMaskUserInputText(true);

        boolean shouldMaskApp = remoteConfig.isMaskApplicationText() || config.isMaskApplicationText();
        boolean shouldMaskInput = remoteConfig.isMaskUserInputText() || config.isMaskUserInputText();

        Assert.assertFalse("Application text should not be masked when remote=false and local is unset", shouldMaskApp);
        Assert.assertTrue("Remote maskUserInputText=true must result in masking when local is unset", shouldMaskInput);
    }

    @Test
    public void testRemoteMaskBothTrueLocalFalseDefaultShouldMaskBoth() {
        // Remote masks both; unset local must not prevent either from being masked.
        SessionReplayConfiguration remoteConfig = new SessionReplayConfiguration();
        remoteConfig.setMaskApplicationText(true);
        remoteConfig.setMaskUserInputText(true);

        boolean shouldMaskApp = remoteConfig.isMaskApplicationText() || config.isMaskApplicationText();
        boolean shouldMaskInput = remoteConfig.isMaskUserInputText() || config.isMaskUserInputText();

        Assert.assertTrue("Application text must be masked when remote=true and local is unset", shouldMaskApp);
        Assert.assertTrue("User input text must be masked when remote=true and local is unset", shouldMaskInput);
    }

    @Test
    public void testDefaultsDoNotOverrideRemoteConfigMaskingDisabled() {
        // Remote says don't mask; unset local config should not force masking.
        // With default local config (false/false), OR-ing with remote=false yields false.
        SessionReplayConfiguration remoteConfig = new SessionReplayConfiguration();
        remoteConfig.setMaskApplicationText(false);
        remoteConfig.setMaskUserInputText(false);

        boolean shouldMaskApp = remoteConfig.isMaskApplicationText() || config.isMaskApplicationText();
        boolean shouldMaskInput = remoteConfig.isMaskUserInputText() || config.isMaskUserInputText();

        Assert.assertFalse("Local defaults must not force application text masking when remote disables it", shouldMaskApp);
        Assert.assertFalse("Local defaults must not force input text masking when remote disables it", shouldMaskInput);
    }

}