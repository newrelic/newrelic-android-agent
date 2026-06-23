/*
 * Copyright (c) 2026-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.newrelic.agent.android.sessioncontext;

import android.content.Context;

import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.SpyContext;
import com.newrelic.agent.android.analytics.AnalyticsAttribute;
import com.newrelic.agent.android.metric.MetricNames;
import com.newrelic.agent.android.stats.StatsEngine;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RunWith(RobolectricTestRunner.class)
public class FileSessionContextStoreTest {

    private Context context;
    private AgentConfiguration config;
    private FileSessionContextStore store;
    private File cacheDir;

    private static Set<AnalyticsAttribute> attrs(String key, String value) {
        Set<AnalyticsAttribute> s = new HashSet<>();
        s.add(new AnalyticsAttribute(key, value));
        return s;
    }

    @Before
    public void setUp() {
        context = new SpyContext().getContext();
        config = new AgentConfiguration();
        store = new FileSessionContextStore(context, config);
        cacheDir = new File(context.getFilesDir(), FileSessionContextStore.DIR_NAME);
        StatsEngine.get().getStatsMap().clear();
        StatsEngine.SUPPORTABILITY.getStatsMap().clear();
    }

    @After
    public void tearDown() {
        if (store != null) {
            store.clear();
        }
    }

    @Test
    public void upsert_then_get_roundTrip() {
        SessionManifest m = new SessionManifest("S_A", 1234, 10L, 20L, attrs("checkout_step", "review"));
        Assert.assertTrue(store.upsert(m));

        SessionManifest got = store.get("S_A");
        Assert.assertNotNull(got);
        Assert.assertEquals("S_A", got.getSessionId());
        Assert.assertEquals(1234, got.getRealAgentId());
        Assert.assertEquals(1, got.getAttributes().size());
        Assert.assertEquals("review", got.getAttributes().iterator().next().getStringValue());
    }

    @Test
    public void upsert_sameSessionId_replaces() {
        store.upsert(new SessionManifest("S_A", 1, 0L, 1L, attrs("step", "one")));
        store.upsert(new SessionManifest("S_A", 1, 0L, 2L, attrs("step", "two")));

        Assert.assertEquals(1, store.count());
        Assert.assertEquals("two", store.get("S_A").getAttributes().iterator().next().getStringValue());
    }

    @Test
    public void get_unknownSessionId_returnsNull() {
        Assert.assertNull(store.get("does-not-exist"));
    }

    @Test
    public void fetchAll_and_count() {
        store.upsert(new SessionManifest("S_A", 1, 0L, 1L, attrs("k", "a")));
        store.upsert(new SessionManifest("S_B", 1, 0L, 1L, attrs("k", "b")));

        Assert.assertEquals(2, store.count());
        List<SessionManifest> all = store.fetchAll();
        Assert.assertEquals(2, all.size());
    }

    @Test
    public void delete_removesOnlyTarget() {
        store.upsert(new SessionManifest("S_A", 1, 0L, 1L, attrs("k", "a")));
        store.upsert(new SessionManifest("S_B", 1, 0L, 1L, attrs("k", "b")));

        store.delete("S_A");

        Assert.assertEquals(1, store.count());
        Assert.assertNull(store.get("S_A"));
        Assert.assertNotNull(store.get("S_B"));
    }

    @Test
    public void evictsOldestWhenOverCap() {
        config.setMaxCachedSessionContextCount(2);
        store = new FileSessionContextStore(context, config);

        store.upsert(new SessionManifest("S_1", 1, 0L, 1L, attrs("k", "1")));
        store.upsert(new SessionManifest("S_2", 1, 0L, 2L, attrs("k", "2")));
        store.upsert(new SessionManifest("S_3", 1, 0L, 3L, attrs("k", "3")));

        Assert.assertEquals(2, store.count());
        Assert.assertTrue(StatsEngine.get().getStatsMap()
                .containsKey(MetricNames.SUPPORTABILITY_SESSION_CONTEXT_EVICTED));
    }

    @Test
    public void defaultCapBoundsStore() {
        Assert.assertEquals(32, AgentConfiguration.DEFAULT_MAX_CACHED_SESSION_CONTEXT_COUNT);
        // store was created in setUp() with a default-config cap of 32.
        for (int i = 0; i < 40; i++) {
            store.upsert(new SessionManifest("S_" + i, 1, 0L, i + 1, attrs("k", String.valueOf(i))));
        }
        Assert.assertEquals(32, store.count());
    }

    @Test
    public void getOnCorruptFileDeletesAndCounts() throws Exception {
        File corrupt = new File(cacheDir,
                FileSessionContextStore.safeFilename("S_corrupt") + FileSessionContextStore.FILE_SUFFIX);
        try (OutputStream os = new FileOutputStream(corrupt, false)) {
            os.write("{not valid json".getBytes(StandardCharsets.UTF_8));
        }
        Assert.assertTrue(corrupt.exists());

        Assert.assertNull(store.get("S_corrupt"));
        Assert.assertFalse("get() must delete a corrupt entry", corrupt.exists());
        Assert.assertTrue(StatsEngine.get().getStatsMap()
                .containsKey(MetricNames.SUPPORTABILITY_SESSION_CONTEXT_CORRUPTED));
    }

    @Test
    public void corruptFileIsSkippedAndCounted() throws Exception {
        store.upsert(new SessionManifest("S_A", 1, 0L, 1L, attrs("k", "a")));

        File corrupt = new File(cacheDir, "corrupt" + FileSessionContextStore.FILE_SUFFIX);
        try (OutputStream os = new FileOutputStream(corrupt, false)) {
            os.write("{not valid json".getBytes(StandardCharsets.UTF_8));
        }

        List<SessionManifest> all = store.fetchAll();
        Assert.assertEquals(1, all.size());
        Assert.assertFalse(corrupt.exists());
        Assert.assertTrue(StatsEngine.get().getStatsMap()
                .containsKey(MetricNames.SUPPORTABILITY_SESSION_CONTEXT_CORRUPTED));
    }
}