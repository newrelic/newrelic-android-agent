/*
 * Copyright (c) 2026. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.sessionReplay;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;

import androidx.test.core.app.ApplicationProvider;

import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.background.ApplicationStateListener;
import com.newrelic.agent.android.background.ApplicationStateMonitor;
import com.newrelic.agent.android.sessionReplay.viewMapper.ComposeImageThingy;
import com.newrelic.agent.android.sessionReplay.viewMapper.SessionReplayImageViewThingy;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Verifies that the per-class image LRU caches are released when the app moves to the
 * background. Without this, a backgrounded process can retain up to ~100 MB of base64
 * image data (50 MB on each cache) until process death.
 */
@RunWith(RobolectricTestRunner.class)
public class SessionReplayLifecycleTest {

    @Before
    public void setUp() {
        SessionReplayImageViewThingy.clearImageCache();
        ComposeImageThingy.clearImageCache();
    }

    @Test
    public void applicationBackgrounded_clearsBothImageCaches() throws Exception {
        LruCache<String, String> viewCache = getViewImageCache();
        LruCache<String, String> composeCache = getComposeImageCache();

        viewCache.put("view-key", "view-base64-data");
        composeCache.put("compose-key", "compose-base64-data");
        Assert.assertEquals("precondition: view cache populated", 1, viewCache.snapshot().size());
        Assert.assertEquals("precondition: compose cache populated", 1, composeCache.snapshot().size());

        SessionReplay singleton = getSessionReplaySingleton();
        singleton.applicationBackgrounded(null);

        Assert.assertEquals("view cache must be cleared on background", 0, viewCache.snapshot().size());
        Assert.assertEquals("compose cache must be cleared on background", 0, composeCache.snapshot().size());
    }

    @Test
    public void initialize_registersApplicationStateListenerExactlyOnce() throws Exception {
        // NR-614098: SessionReplay implements ApplicationStateListener but was never registered
        // with ApplicationStateMonitor, so its applicationBackgrounded() cleanup never ran in
        // production. initialize() runs once per session bootstrap (launch, harvest-connect
        // re-arm, session restart) - registration must be idempotent, not re-added each time.
        // Reset the process-lifetime registration flag and swap in a scoped monitor so this
        // test is deterministic regardless of what earlier tests already registered.
        resetListenerRegisteredFlag();
        ApplicationStateMonitor original = ApplicationStateMonitor.getInstance();
        ApplicationStateMonitor monitor = new ApplicationStateMonitor();
        ApplicationStateMonitor.setInstance(monitor);
        try {
            Application application = (Application) ApplicationProvider.getApplicationContext();
            Handler handler = new Handler(Looper.getMainLooper());
            AgentConfiguration agentConfiguration = new AgentConfiguration();

            SessionReplay.initialize(application, handler, agentConfiguration, SessionReplayMode.OFF);
            SessionReplay.initialize(application, handler, agentConfiguration, SessionReplayMode.OFF);

            SessionReplay singleton = getSessionReplaySingleton();
            int occurrences = 0;
            for (ApplicationStateListener listener : getListeners(monitor)) {
                if (listener == singleton) {
                    occurrences++;
                }
            }
            Assert.assertEquals("SessionReplay must register with ApplicationStateMonitor exactly once",
                    1, occurrences);
        } finally {
            ApplicationStateMonitor.setInstance(original);
        }
    }

    @Test
    public void deInitialize_clearsBothImageCachesUnconditionally() throws Exception {
        // Used by both the shutdown and session-restart paths, neither of which necessarily
        // coincides with the app being backgrounded, so this can't rely on the
        // applicationBackgrounded() listener path alone.
        Application application = (Application) ApplicationProvider.getApplicationContext();
        Handler handler = new Handler(Looper.getMainLooper());
        AgentConfiguration agentConfiguration = new AgentConfiguration();
        SessionReplay.initialize(application, handler, agentConfiguration, SessionReplayMode.OFF);

        LruCache<String, String> viewCache = getViewImageCache();
        LruCache<String, String> composeCache = getComposeImageCache();
        viewCache.put("view-key", "view-base64-data");
        composeCache.put("compose-key", "compose-base64-data");

        SessionReplay.deInitialize();

        Assert.assertEquals("view cache must be cleared on deInitialize", 0, viewCache.snapshot().size());
        Assert.assertEquals("compose cache must be cleared on deInitialize", 0, composeCache.snapshot().size());
    }

    @Test
    public void deInitialize_leavesNoStaleRecordingMode() throws Exception {
        // Regression test for NR-614098: SessionReplay.getCurrentMode() previously kept
        // reporting the prior session's mode after deInitialize(), which could make
        // AndroidAgentImpl.reconcileLoggingOverride() mis-toggle the log-sampling override
        // during a background-only harvest-connect. It also exposed a latent && / NPE bug in
        // isReplayRecording() once modeManager could legitimately be null.
        Application application = (Application) ApplicationProvider.getApplicationContext();
        Handler handler = new Handler(Looper.getMainLooper());
        AgentConfiguration agentConfiguration = new AgentConfiguration();
        agentConfiguration.getSessionReplayConfiguration().setEnabled(true);
        SessionReplay.initialize(application, handler, agentConfiguration, SessionReplayMode.FULL);

        SessionReplay.deInitialize();

        Assert.assertNull("getCurrentMode() must not report a stale mode after teardown",
                SessionReplay.getCurrentMode());
        Assert.assertFalse("isReplayRecording() must not NPE or report stale state after teardown",
                SessionReplay.isReplayRecording());
    }

    private static void resetListenerRegisteredFlag() throws Exception {
        Field f = SessionReplay.class.getDeclaredField("listenerRegistered");
        f.setAccessible(true);
        ((AtomicBoolean) f.get(null)).set(false);
    }

    private static SessionReplay getSessionReplaySingleton() throws Exception {
        Field f = SessionReplay.class.getDeclaredField("instance");
        f.setAccessible(true);
        return (SessionReplay) f.get(null);
    }

    @SuppressWarnings("unchecked")
    private static ArrayList<ApplicationStateListener> getListeners(ApplicationStateMonitor monitor) throws Exception {
        Field f = ApplicationStateMonitor.class.getDeclaredField("applicationStateListeners");
        f.setAccessible(true);
        return (ArrayList<ApplicationStateListener>) f.get(monitor);
    }

    @SuppressWarnings("unchecked")
    private static LruCache<String, String> getViewImageCache() throws Exception {
        Field f = SessionReplayImageViewThingy.class.getDeclaredField("imageCache");
        f.setAccessible(true);
        return (LruCache<String, String>) f.get(null);
    }

    @SuppressWarnings("unchecked")
    private static LruCache<String, String> getComposeImageCache() throws Exception {
        Field f = ComposeImageThingy.class.getDeclaredField("imageCache");
        f.setAccessible(true);
        return (LruCache<String, String>) f.get(null);
    }
}
