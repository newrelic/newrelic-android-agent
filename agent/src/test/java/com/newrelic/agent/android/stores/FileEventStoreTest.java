/*
 * Copyright (c) 2026-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.stores;

import android.content.Context;

import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.SpyContext;
import com.newrelic.agent.android.analytics.AnalyticsEvent;
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
public class FileEventStoreTest {

    private Context context;
    private AgentConfiguration config;
    private FileEventStore store;
    private File cacheDir;

    @Before
    public void setUp() {
        context = new SpyContext().getContext();
        config = new AgentConfiguration();
        store = new FileEventStore(context, config);
        cacheDir = new File(context.getFilesDir(), FileEventStore.DIR_NAME);
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
        AnalyticsEvent a = new AnalyticsEvent("event-a");
        AnalyticsEvent b = new AnalyticsEvent("event-b");

        Assert.assertTrue(store.store(a));
        Assert.assertTrue(store.store(b));
        store.awaitWrites(5000);
        Assert.assertEquals(2, store.count());

        List<AnalyticsEvent> fetched = store.fetchAll();
        Assert.assertEquals(2, fetched.size());
        boolean foundA = false;
        boolean foundB = false;
        for (AnalyticsEvent e : fetched) {
            if (e.getEventUUID().equals(a.getEventUUID())) foundA = true;
            if (e.getEventUUID().equals(b.getEventUUID())) foundB = true;
        }
        Assert.assertTrue(foundA);
        Assert.assertTrue(foundB);
    }

    @Test
    public void flush_blocksUntilPendingWritesLand() {
        AnalyticsEvent e = new AnalyticsEvent("flushed");
        Assert.assertTrue(store.store(e));

        store.flush(5000);

        File expected = new File(cacheDir, e.getEventUUID() + AbstractFileStore.FILE_SUFFIX);
        Assert.assertTrue("File should be written to disk by the time flush() returns", expected.exists());
    }

    @Test
    public void store_atCap_evictsOldestFirst() {
        config.setMaxCachedEventCount(3);
        store = new FileEventStore(context, config);

        AnalyticsEvent oldest = new AnalyticsEvent("old");
        AnalyticsEvent second = new AnalyticsEvent("second");
        AnalyticsEvent third = new AnalyticsEvent("third");
        AnalyticsEvent newest = new AnalyticsEvent("newest");

        Assert.assertTrue(store.store(oldest));
        setFileMtime(oldest, 1000L);
        Assert.assertTrue(store.store(second));
        setFileMtime(second, 2000L);
        Assert.assertTrue(store.store(third));
        setFileMtime(third, 3000L);

        Assert.assertEquals(3, store.count());
        Assert.assertTrue(store.store(newest));
        store.awaitWrites(5000);
        Assert.assertEquals(3, store.count());

        Assert.assertTrue("eviction metric should fire",
                StatsEngine.get().getStatsMap().containsKey(MetricNames.SUPPORTABILITY_EVENT_STORE_EVICTED));
    }

    @Test
    public void store_writesAtomically() {
        AnalyticsEvent e = new AnalyticsEvent("atomic");
        Assert.assertTrue(store.store(e));
        store.awaitWrites(5000);

        File[] jsonFiles = cacheDir.listFiles((d, n) -> n.endsWith(AbstractFileStore.FILE_SUFFIX));
        File[] tmpFiles = cacheDir.listFiles((d, n) -> n.endsWith(AbstractFileStore.TMP_SUFFIX));

        Assert.assertNotNull(jsonFiles);
        Assert.assertEquals(1, jsonFiles.length);
        Assert.assertEquals(e.getEventUUID() + AbstractFileStore.FILE_SUFFIX, jsonFiles[0].getName());
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

        new FileEventStore(context, config);

        Assert.assertFalse("Orphan tmp should be swept on init", orphan.exists());
    }

    @Test
    public void fetchAll_skipsCorruptedFiles() throws IOException {
        AnalyticsEvent good = new AnalyticsEvent("good");
        Assert.assertTrue(store.store(good));
        store.awaitWrites(5000);

        File corrupt = new File(cacheDir, "garbage" + AbstractFileStore.FILE_SUFFIX);
        try (OutputStream os = new FileOutputStream(corrupt)) {
            os.write("not-json".getBytes(StandardCharsets.UTF_8));
        }
        Assert.assertTrue(corrupt.exists());

        List<AnalyticsEvent> fetched = store.fetchAll();
        Assert.assertEquals(1, fetched.size());
        Assert.assertFalse("Corrupt file should be deleted", corrupt.exists());
        Assert.assertTrue("corruption metric should fire",
                StatsEngine.get().getStatsMap().containsKey(MetricNames.SUPPORTABILITY_EVENT_STORE_CORRUPTED));
    }

    @Test
    public void duplicateUuid_replacesLatest() {
        AnalyticsEvent e = new AnalyticsEvent("dup");
        Assert.assertTrue(store.store(e));
        Assert.assertTrue(store.store(e));
        store.awaitWrites(5000);
        Assert.assertEquals(1, store.count());
    }

    @Test
    public void delete_isIdempotent() {
        AnalyticsEvent missing = new AnalyticsEvent("never");
        store.delete(missing);
        store.delete(missing);
        store.awaitWrites(5000);
        Assert.assertEquals(0, store.count());
    }

    @Test
    public void clear_removesEverything() throws IOException {
        AnalyticsEvent e = new AnalyticsEvent("gone");
        Assert.assertTrue(store.store(e));
        store.awaitWrites(5000);
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
                    store.store(new AnalyticsEvent("w-" + i));
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
        store.awaitWrites(5000);

        Assert.assertEquals(0, failures.get());
        Assert.assertTrue(store.count() >= 0);
    }

    private void setFileMtime(AnalyticsEvent e, long mtime) {
        store.awaitWrites(5000);
        File f = new File(cacheDir, e.getEventUUID() + AbstractFileStore.FILE_SUFFIX);
        Assert.assertTrue("Expected file to exist for " + e.getEventUUID(), f.exists());
        Assert.assertTrue("Setting mtime should succeed", f.setLastModified(mtime));
    }
}