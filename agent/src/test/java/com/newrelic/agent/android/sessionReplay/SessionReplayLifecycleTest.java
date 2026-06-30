/*
 * Copyright (c) 2026. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.sessionReplay;

import android.util.LruCache;

import com.newrelic.agent.android.sessionReplay.viewMapper.ComposeImageThingy;
import com.newrelic.agent.android.sessionReplay.viewMapper.SessionReplayImageViewThingy;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.lang.reflect.Field;

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

    private static SessionReplay getSessionReplaySingleton() throws Exception {
        Field f = SessionReplay.class.getDeclaredField("instance");
        f.setAccessible(true);
        return (SessionReplay) f.get(null);
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
