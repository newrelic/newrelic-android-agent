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

    @SuppressWarnings("unchecked")
    private static LruCache<String, String> getStaticCache() throws Exception {
        Field f = SessionReplayImageViewThingy.class.getDeclaredField("imageCache");
        f.setAccessible(true);
        return (LruCache<String, String>) f.get(null);
    }
}
