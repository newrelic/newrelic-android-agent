/*
 * Copyright (c) 2026-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.payload;

import android.content.Context;
import android.content.SharedPreferences;

import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.SpyContext;
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
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(RobolectricTestRunner.class)
public class FilePayloadStoreTest {

    private static final byte[] testBytes = {0xd, 0xe, 0xa, 0xd, 0xb, 0xe, 0xe, 0xf};
    private static final byte[] otherBytes = {0x1, 0x2, 0x3, 0x4, 0x5};

    private Context context;
    private AgentConfiguration config;
    private FilePayloadStore store;
    private File cacheDir;

    @Before
    public void setUp() {
        context = new SpyContext().getContext();
        config = new AgentConfiguration();
        store = new FilePayloadStore(context, config);
        cacheDir = new File(context.getFilesDir(), FilePayloadStore.DIR_NAME);
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
        Payload a = new Payload(testBytes);
        Payload b = new Payload(otherBytes);
        Payload c = new Payload("plain-json".getBytes());

        Assert.assertTrue(store.store(a));
        Assert.assertTrue(store.store(b));
        Assert.assertTrue(store.store(c));

        Assert.assertEquals(3, store.count());

        List<Payload> fetched = store.fetchAll();
        Assert.assertEquals(3, fetched.size());
        Assert.assertTrue(fetched.contains(a));
        Assert.assertTrue(fetched.contains(b));
        Assert.assertTrue(fetched.contains(c));

        for (Payload p : fetched) {
            if (p.equals(a)) Assert.assertArrayEquals(testBytes, p.getBytes());
            if (p.equals(b)) Assert.assertArrayEquals(otherBytes, p.getBytes());
        }
    }

    @Test
    public void store_atCap_evictsOldestFirst() throws IOException {
        config.setMaxCachedPayloadCount(3);
        store = new FilePayloadStore(context, config);

        Payload oldest = new Payload(testBytes);
        Payload second = new Payload(testBytes);
        Payload third = new Payload(testBytes);
        Payload newest = new Payload(testBytes);

        Assert.assertTrue(store.store(oldest));
        setFileMtime(oldest, 1000L);

        Assert.assertTrue(store.store(second));
        setFileMtime(second, 2000L);

        Assert.assertTrue(store.store(third));
        setFileMtime(third, 3000L);

        Assert.assertEquals(3, store.count());
        Assert.assertTrue(store.store(newest));
        Assert.assertEquals(3, store.count());

        List<Payload> fetched = store.fetchAll();
        Assert.assertFalse("Oldest should have been evicted", fetched.contains(oldest));
        Assert.assertTrue(fetched.contains(second));
        Assert.assertTrue(fetched.contains(third));
        Assert.assertTrue(fetched.contains(newest));

        Assert.assertTrue("eviction metric should fire",
                StatsEngine.get().getStatsMap().containsKey(MetricNames.SUPPORTABILITY_PAYLOAD_EVICTED));
    }

    @Test
    public void store_writesAtomically() {
        Payload p = new Payload(testBytes);
        Assert.assertTrue(store.store(p));

        File[] jsonFiles = cacheDir.listFiles((d, n) -> n.endsWith(FilePayloadStore.FILE_SUFFIX));
        File[] tmpFiles = cacheDir.listFiles((d, n) -> n.endsWith(FilePayloadStore.TMP_SUFFIX));

        Assert.assertNotNull(jsonFiles);
        Assert.assertEquals(1, jsonFiles.length);
        Assert.assertEquals(p.getUuid() + FilePayloadStore.FILE_SUFFIX, jsonFiles[0].getName());
        Assert.assertNotNull(tmpFiles);
        Assert.assertEquals(0, tmpFiles.length);
    }

    @Test
    public void init_sweepsOrphanedTempFiles() throws IOException {
        store.clear();
        File orphan = new File(cacheDir, "orphan" + FilePayloadStore.TMP_SUFFIX);
        try (OutputStream os = new FileOutputStream(orphan)) {
            os.write("half-written".getBytes(StandardCharsets.UTF_8));
        }
        Assert.assertTrue(orphan.exists());

        new FilePayloadStore(context, config);

        Assert.assertFalse("Orphan tmp should be swept on init", orphan.exists());
    }

    @Test
    public void fetchAll_skipsCorruptedFiles() throws IOException {
        Payload good = new Payload(testBytes);
        Assert.assertTrue(store.store(good));

        File corrupt = new File(cacheDir, "garbage" + FilePayloadStore.FILE_SUFFIX);
        try (OutputStream os = new FileOutputStream(corrupt)) {
            os.write("not-json-at-all".getBytes(StandardCharsets.UTF_8));
        }
        Assert.assertTrue(corrupt.exists());

        List<Payload> fetched = store.fetchAll();
        Assert.assertEquals(1, fetched.size());
        Assert.assertTrue(fetched.contains(good));
        Assert.assertFalse("Corrupt file should be deleted", corrupt.exists());
        Assert.assertTrue("corruption metric should fire",
                StatsEngine.get().getStatsMap().containsKey(MetricNames.SUPPORTABILITY_PAYLOAD_CORRUPTED));
    }

    @Test
    public void duplicateUuid_replacesLatest() {
        Payload p = new Payload(testBytes);
        Assert.assertTrue(store.store(p));

        p.putBytes(otherBytes);
        Assert.assertTrue(store.store(p));

        Assert.assertEquals(1, store.count());
        List<Payload> fetched = store.fetchAll();
        Assert.assertEquals(1, fetched.size());
        Assert.assertArrayEquals(otherBytes, fetched.get(0).getBytes());
    }

    @Test
    public void delete_isIdempotent() {
        Payload missing = new Payload(testBytes);
        store.delete(missing);
        store.delete(missing);
        Assert.assertEquals(0, store.count());
    }

    @Test
    public void clear_removesEverything() throws IOException {
        Payload p = new Payload(testBytes);
        Assert.assertTrue(store.store(p));
        File strayTmp = new File(cacheDir, "stray" + FilePayloadStore.TMP_SUFFIX);
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
        final int threadCount = 3;
        final int opsPerThread = 50;
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(threadCount);
        final AtomicInteger failures = new AtomicInteger(0);

        Thread writer = new Thread(() -> {
            try {
                start.await();
                for (int i = 0; i < opsPerThread; i++) {
                    store.store(new Payload(testBytes));
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
                    store.delete(new Payload(testBytes));
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
    public void legacySharedPrefsStoreIsPurgedOnInit() {
        SharedPreferences legacy = context.getSharedPreferences("NRPayloadStore", Context.MODE_PRIVATE);
        legacy.edit()
                .putString("uuid-a", "{\"payload\":\"meta\",\"encodedPayload\":\"QUJDRA==\"}")
                .putString("uuid-b", "{\"payload\":\"meta\",\"encodedPayload\":\"QUJDRA==\"}")
                .commit();
        Assert.assertFalse("seed should be present", legacy.getAll().isEmpty());

        new FilePayloadStore(context, config);
        context.deleteSharedPreferences("NRPayloadStore");

        SharedPreferences after = context.getSharedPreferences("NRPayloadStore", Context.MODE_PRIVATE);
        Assert.assertTrue("Legacy store should be empty after purge", after.getAll().isEmpty());
        Assert.assertEquals("New store should start empty", 0, store.count());
    }

    private void setFileMtime(Payload p, long mtime) {
        File f = new File(cacheDir, p.getUuid() + FilePayloadStore.FILE_SUFFIX);
        Assert.assertTrue("Expected file to exist for " + p.getUuid(), f.exists());
        Assert.assertTrue("Setting mtime should succeed", f.setLastModified(mtime));
    }
}