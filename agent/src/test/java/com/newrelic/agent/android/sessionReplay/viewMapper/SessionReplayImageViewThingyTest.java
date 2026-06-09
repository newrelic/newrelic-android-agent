/*
 * Copyright (c) 2026. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.sessionReplay.viewMapper;

import android.util.LruCache;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.lang.reflect.Field;

@RunWith(RobolectricTestRunner.class)
public class SessionReplayImageViewThingyTest {

    @Before
    public void setUp() {
        SessionReplayImageViewThingy.clearImageCache();
        // Reset the shared ImageView so per-class static state does not leak across tests.
        sharedImageView = null;
    }

    // ==================== LRU CACHE SIZING ====================

    @Test
    public void lruCache_smallBase64String_doesNotSizeToZero() throws Exception {
        LruCache<String, String> cache = getStaticCache();
        // 50-char Base64 string — would floor to 0 under the old (length/1024) sizing.
        cache.put("k", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
        Assert.assertTrue("cache size must be > 0 for non-empty value", cache.size() > 0);
    }

    @Test
    public void lruCache_capIsAtLeast50MB() throws Exception {
        LruCache<String, String> cache = getStaticCache();
        Assert.assertTrue("max cache size should be at least 50 MB",
                cache.maxSize() >= 50 * 1024 * 1024);
    }

    // ==================== hasChanged ====================

    @Test
    public void hasChanged_identicalContent_returnsFalse() throws Exception {
        SessionReplayImageViewThingy a = newThingyWithImageData("AAA");
        SessionReplayImageViewThingy b = newThingyWithImageData("AAA");
        Assert.assertFalse("two thingies with identical content must not register as changed",
                a.hasChanged(b));
    }

    @Test
    public void hasChanged_differentImageData_returnsTrue() throws Exception {
        SessionReplayImageViewThingy a = newThingyWithImageData("AAA");
        SessionReplayImageViewThingy b = newThingyWithImageData("BBB");
        Assert.assertTrue("different imageData must register as changed",
                a.hasChanged(b));
    }

    @Test
    public void hasChanged_nonImageType_returnsTrue() throws Exception {
        SessionReplayImageViewThingy a = newThingyWithImageData("AAA");
        Assert.assertTrue("non-image other must register as changed",
                a.hasChanged(null));
    }

    /**
     * Build a SessionReplayImageViewThingy bypassing the constructor so tests do not
     * need to drive a real ImageView through the bitmap pipeline. Sets the private
     * imageData field via reflection.
     *
     * Reuses a single ImageView across calls so that hasChanged() comparisons that
     * include viewDetails (which captures a per-View stable id) only differ on the
     * fields the test is exercising. The shared view is reset in @Before so state
     * does not leak across tests.
     */
    private static android.widget.ImageView sharedImageView;

    private SessionReplayImageViewThingy newThingyWithImageData(String imageData) throws Exception {
        android.content.Context ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext();
        if (sharedImageView == null) {
            sharedImageView = new android.widget.ImageView(ctx);
            sharedImageView.layout(0, 0, 10, 10);
        }
        com.newrelic.agent.android.AgentConfiguration cfg = new com.newrelic.agent.android.AgentConfiguration();
        SessionReplayImageViewThingy thingy = new SessionReplayImageViewThingy(new ViewDetails(sharedImageView), sharedImageView, cfg);
        Field f = SessionReplayImageViewThingy.class.getDeclaredField("imageData");
        f.setAccessible(true);
        f.set(thingy, imageData);
        return thingy;
    }

    @SuppressWarnings("unchecked")
    private static LruCache<String, String> getStaticCache() throws Exception {
        Field f = SessionReplayImageViewThingy.class.getDeclaredField("imageCache");
        f.setAccessible(true);
        return (LruCache<String, String>) f.get(null);
    }
}
