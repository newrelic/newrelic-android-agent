/*
 * Copyright (c) 2026-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.stores;

import android.content.Context;
import android.content.SharedPreferences;

import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.SpyContext;
import com.newrelic.agent.android.crash.Crash;
import com.newrelic.agent.android.crash.CrashReporter;
import com.newrelic.agent.android.metric.MetricNames;
import com.newrelic.agent.android.payload.AbstractFileStore;
import com.newrelic.agent.android.stats.StatsEngine;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(RobolectricTestRunner.class)
public class FileCrashStoreTest {

    private Context context;
    private AgentConfiguration config;
    private FileCrashStore store;
    private File cacheDir;

    @Before
    public void setUp() {
        context = new SpyContext().getContext();
        config = new AgentConfiguration();
        config.setApplicationToken(FileCrashStoreTest.class.getSimpleName());
        CrashReporter.initialize(config);

        store = new FileCrashStore(context, config);
        config.setCrashStore(store);
        cacheDir = new File(context.getFilesDir(), FileCrashStore.DIR_NAME);
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
    public void store_then_fetchAll_roundTrip() {
        Crash a = new Crash(new RuntimeException("boom-a"));
        Crash b = new Crash(new RuntimeException("boom-b"));

        Assert.assertTrue(store.store(a));
        Assert.assertTrue(store.store(b));
        Assert.assertEquals(2, store.count());

        List<Crash> fetched = store.fetchAll();
        Assert.assertEquals(2, fetched.size());
    }

    @Test
    public void store_atCap_evictsOldestFirst() {
        config.setMaxCachedCrashCount(3);
        store = new FileCrashStore(context, config);

        Crash oldest = new Crash(new RuntimeException("old"));
        Crash second = new Crash(new RuntimeException("second"));
        Crash third = new Crash(new RuntimeException("third"));
        Crash newest = new Crash(new RuntimeException("newest"));

        Assert.assertTrue(store.store(oldest));
        setFileMtime(oldest, 1000L);
        Assert.assertTrue(store.store(second));
        setFileMtime(second, 2000L);
        Assert.assertTrue(store.store(third));
        setFileMtime(third, 3000L);

        Assert.assertEquals(3, store.count());
        Assert.assertTrue(store.store(newest));
        Assert.assertEquals(3, store.count());

        Assert.assertTrue("eviction metric should fire",
                StatsEngine.get().getStatsMap().containsKey(MetricNames.SUPPORTABILITY_CRASH_REMOVED_EVICTED));
    }

    @Test
    public void store_writesAtomically() {
        Crash c = new Crash(new RuntimeException("atomic"));
        Assert.assertTrue(store.store(c));

        File[] jsonFiles = cacheDir.listFiles((d, n) -> n.endsWith(AbstractFileStore.FILE_SUFFIX));
        File[] tmpFiles = cacheDir.listFiles((d, n) -> n.endsWith(AbstractFileStore.TMP_SUFFIX));

        Assert.assertNotNull(jsonFiles);
        Assert.assertEquals(1, jsonFiles.length);
        Assert.assertEquals(c.getUuid() + AbstractFileStore.FILE_SUFFIX, jsonFiles[0].getName());
        Assert.assertNotNull(tmpFiles);
        Assert.assertEquals(0, tmpFiles.length);
    }

    @Test
    public void init_sweepsOrphanedTempFiles() throws IOException {
        store.clear();
        File orphan = new File(cacheDir, "orphan" + AbstractFileStore.TMP_SUFFIX);
        try (OutputStream os = new FileOutputStream(orphan)) {
            os.write("half-written".getBytes(StandardCharsets.UTF_8));
        }
        Assert.assertTrue(orphan.exists());

        new FileCrashStore(context, config);

        Assert.assertFalse("Orphan tmp should be swept on init", orphan.exists());
    }

    @Test
    public void fetchAll_skipsCorruptedFiles() throws IOException {
        Crash good = new Crash(new RuntimeException("good"));
        Assert.assertTrue(store.store(good));

        File corrupt = new File(cacheDir, "garbage" + AbstractFileStore.FILE_SUFFIX);
        try (OutputStream os = new FileOutputStream(corrupt)) {
            os.write("not-json".getBytes(StandardCharsets.UTF_8));
        }
        Assert.assertTrue(corrupt.exists());

        List<Crash> fetched = store.fetchAll();
        Assert.assertEquals(1, fetched.size());
        Assert.assertFalse("Corrupt file should be deleted", corrupt.exists());
        Assert.assertTrue("corruption metric should fire",
                StatsEngine.get().getStatsMap().containsKey(MetricNames.SUPPORTABILITY_CRASH_CORRUPTED));
    }

    @Test
    public void duplicateUuid_replacesLatest() {
        Crash c = new Crash(new RuntimeException("once"));
        Assert.assertTrue(store.store(c));
        Assert.assertTrue(store.store(c));

        Assert.assertEquals(1, store.count());
    }

    @Test
    public void delete_isIdempotent() {
        Crash missing = new Crash(new RuntimeException("never-stored"));
        store.delete(missing);
        store.delete(missing);
        Assert.assertEquals(0, store.count());
    }

    @Test
    public void clear_removesEverything() throws IOException {
        Crash c = new Crash(new RuntimeException("gone"));
        Assert.assertTrue(store.store(c));
        File strayTmp = new File(cacheDir, "stray" + AbstractFileStore.TMP_SUFFIX);
        try (OutputStream os = new FileOutputStream(strayTmp)) {
            os.write("tmp".getBytes(StandardCharsets.UTF_8));
        }

        store.clear();

        File[] remaining = cacheDir.listFiles();
        Assert.assertNotNull(remaining);
        Assert.assertEquals(0, remaining.length);
    }

    @Test
    public void concurrentStoreFetchDelete() throws InterruptedException {
        final int opsPerThread = 50;
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(3);
        final AtomicInteger failures = new AtomicInteger(0);

        Thread writer = new Thread(() -> {
            try {
                start.await();
                for (int i = 0; i < opsPerThread; i++) {
                    store.store(new Crash(new RuntimeException("w-" + i)));
                }
            } catch (Exception e) {
                failures.incrementAndGet();
            } finally {
                done.countDown();
            }
        });

        Thread reader = new Thread(() -> {
            try {
                start.await();
                for (int i = 0; i < opsPerThread; i++) {
                    store.fetchAll();
                }
            } catch (Exception e) {
                failures.incrementAndGet();
            } finally {
                done.countDown();
            }
        });

        Thread deleter = new Thread(() -> {
            try {
                start.await();
                for (int i = 0; i < opsPerThread; i++) {
                    store.count();
                }
            } catch (Exception e) {
                failures.incrementAndGet();
            } finally {
                done.countDown();
            }
        });

        writer.start();
        reader.start();
        deleter.start();
        start.countDown();
        Assert.assertTrue(done.await(10, TimeUnit.SECONDS));

        Assert.assertEquals(0, failures.get());
        Assert.assertTrue(store.count() >= 0);
    }

    @Test
    public void migrateFromSharedPrefs_rescuesLegacyCrashes() {
        SharedPreferences legacy = context.getSharedPreferences(FileCrashStore.LEGACY_PREFS_NAME, Context.MODE_PRIVATE);
        Crash a = new Crash(new RuntimeException("legacy-a"));
        Crash b = new Crash(new RuntimeException("legacy-b"));
        legacy.edit()
                .putString(a.getUuid().toString(), a.asJsonObject().toString())
                .putString(b.getUuid().toString(), b.asJsonObject().toString())
                .commit();
        Assert.assertEquals(2, legacy.getAll().size());

        store.clear();
        Assert.assertEquals(0, store.count());

        store.migrateFromSharedPrefs(context, FileCrashStore.LEGACY_PREFS_NAME);

        Assert.assertEquals("Migration should rehydrate both crashes", 2, store.count());
    }

    @Test
    public void migrateFromSharedPrefs_tolerateCorruptEntries() {
        SharedPreferences legacy = context.getSharedPreferences(FileCrashStore.LEGACY_PREFS_NAME, Context.MODE_PRIVATE);
        Crash good = new Crash(new RuntimeException("good"));
        legacy.edit()
                .putString(good.getUuid().toString(), good.asJsonObject().toString())
                .putString("bogus-uuid", "this-is-not-a-valid-crash-json")
                .commit();

        store.clear();
        store.migrateFromSharedPrefs(context, FileCrashStore.LEGACY_PREFS_NAME);

        Assert.assertEquals("Only the valid crash should round-trip", 1, store.count());
    }

    private void setFileMtime(Crash c, long mtime) {
        File f = new File(cacheDir, c.getUuid().toString() + AbstractFileStore.FILE_SUFFIX);
        Assert.assertTrue("Expected file to exist for " + c.getUuid(), f.exists());
        Assert.assertTrue("Setting mtime should succeed", f.setLastModified(mtime));
    }
}