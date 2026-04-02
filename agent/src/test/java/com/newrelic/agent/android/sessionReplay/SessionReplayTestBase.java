///*
// * Copyright (c) 2024. New Relic Corporation. All rights reserved.
// * SPDX-License-Identifier: Apache-2.0
// */
//
//package com.newrelic.agent.android.sessionReplay;
//
//import android.content.Context;
//import android.graphics.Bitmap;
//import android.graphics.Color;
//
//import androidx.test.core.app.ApplicationProvider;
//
//import com.newrelic.agent.android.AgentConfiguration;
//
//import org.junit.After;
//import org.junit.Before;
//import org.junit.runner.RunWith;
//import org.robolectric.RobolectricTestRunner;
//
///**
// * Base class for Session Replay unit tests providing common setup and utilities
// */
//@RunWith(RobolectricTestRunner.class)
//public abstract class SessionReplayTestBase {
//
//    protected Context context;
//    protected AgentConfiguration agentConfiguration;
//    protected SessionReplayConfiguration sessionReplayConfiguration;
//    protected SessionReplayLocalConfiguration sessionReplayLocalConfiguration;
//
//    @Before
//    public void setUp() {
//        context = ApplicationProvider.getApplicationContext();
//
//        // Setup default configuration
//        agentConfiguration = new AgentConfiguration();
//
//        sessionReplayConfiguration = new SessionReplayConfiguration();
//        sessionReplayConfiguration.setEnabled(true);
//        sessionReplayConfiguration.setMode("full");
//        sessionReplayConfiguration.setMaskUserInputText(false);
//        sessionReplayConfiguration.setMaskApplicationText(false);
//        sessionReplayConfiguration.setMaskAllImages(false);
//        sessionReplayConfiguration.setMaskTouches(false);
//
//        sessionReplayLocalConfiguration = new SessionReplayLocalConfiguration();
//
//        agentConfiguration.setSessionReplayConfiguration(sessionReplayConfiguration);
//        agentConfiguration.setSessionReplayLocalConfiguration(sessionReplayLocalConfiguration);
//    }
//
//    @After
//    public void tearDown() {
//        if (sessionReplayLocalConfiguration != null) {
//            sessionReplayLocalConfiguration.clearAllViewMasks();
//        }
//    }
//
//    /**
//     * Creates a test bitmap with specified dimensions
//     */
//    protected Bitmap createTestBitmap(int width, int height) {
//        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
//    }
//
//    /**
//     * Creates a small test bitmap (10x10)
//     */
//    protected Bitmap createSmallTestBitmap() {
//        return createTestBitmap(10, 10);
//    }
//
//    /**
//     * Creates a colored test bitmap
//     */
//    protected Bitmap createColoredBitmap(int width, int height, int color) {
//        Bitmap bitmap = createTestBitmap(width, height);
//        bitmap.eraseColor(color);
//        return bitmap;
//    }
//
//    /**
//     * Creates a red test bitmap
//     */
//    protected Bitmap createRedBitmap() {
//        return createColoredBitmap(10, 10, Color.RED);
//    }
//
//    /**
//     * Creates a configuration with all masking enabled
//     */
//    protected AgentConfiguration createMaskingEnabledConfiguration() {
//        AgentConfiguration config = new AgentConfiguration();
//        SessionReplayConfiguration srConfig = new SessionReplayConfiguration();
//        srConfig.setEnabled(true);
//        srConfig.setMaskUserInputText(true);
//        srConfig.setMaskApplicationText(true);
//        srConfig.setMaskAllImages(true);
//        srConfig.setMaskTouches(true);
//
//        SessionReplayLocalConfiguration localConfig = new SessionReplayLocalConfiguration();
//        config.setSessionReplayConfiguration(srConfig);
//        config.setSessionReplayLocalConfiguration(localConfig);
//
//        return config;
//    }
//
//    /**
//     * Creates a configuration with all masking disabled
//     */
//    protected AgentConfiguration createMaskingDisabledConfiguration() {
//        AgentConfiguration config = new AgentConfiguration();
//        SessionReplayConfiguration srConfig = new SessionReplayConfiguration();
//        srConfig.setEnabled(true);
//        srConfig.setMaskUserInputText(false);
//        srConfig.setMaskApplicationText(false);
//        srConfig.setMaskAllImages(false);
//        srConfig.setMaskTouches(false);
//
//        SessionReplayLocalConfiguration localConfig = new SessionReplayLocalConfiguration();
//        config.setSessionReplayConfiguration(srConfig);
//        config.setSessionReplayLocalConfiguration(localConfig);
//
//        return config;
//    }
//}