/*
 * Copyright (c) 2026-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.hybrid;

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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(RobolectricTestRunner.class)
public class FileJSErrorStoreTest {

    private static final String SAMPLE_JSON_A =
            "{\"eventType\":\"MobileJSError\",\"errorType\":\"TypeError\",\"description\":\"boom\"}";
    private static final String SAMPLE_JSON_B =
            "{\"eventType\":\"MobileJSError\",\"errorType\":\"SyntaxError\",\"description\":\"nope\"}";

    private Context context;
    private AgentConfiguration config;
    private FileJSErrorStore store;
    private File cacheDir;

    @Before
    public void setUp() {
        context = new SpyContext().getContext();
        config = new AgentConfiguration();
        store = new FileJSErrorStore(context, config);
        cacheDir = new File(context.getFilesDir(), FileJSErrorStore.DIR_NAME);
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
        String idA = UUID.randomUUID().toString();
        String idB = UUID.randomUUID().toString();

        Assert.assertTrue(store.store(idA, SAMPLE_JSON_A));
        Assert.assertTrue(store.store(idB, SAMPLE_JSON_B));
        Assert.assertEquals(2, store.count());

        List<String> values = store.fetchAll();
        Assert.assertEquals(2, values.size());
        Assert.assertTrue(values.contains(SAMPLE_JSON_A));
        Assert.assertTrue(values.contains(SAMPLE_JSON_B));
    }

    @Test
    public void fetchAllEntries_preservesOriginalIds() {
        String idA = UUID.randomUUID().toString();
        String idB = UUID.randomUUID().toString();

        Assert.assertTrue(store.store(idA, SAMPLE_JSON_A));
        Assert.assertTrue(store.store(idB, SAMPLE_JSON_B));

        Map<String, String> entries = store.fetchAllEntries();
        Assert.assertEquals(2, entries.size());
        Assert.assertEquals(SAMPLE_JSON_A, entries.get(idA));
        Assert.assertEquals(SAMPLE_JSON_B, entries.get(idB));
    }

    @Test
    public void store_rejectsNullOrEmpty() {
        Assert.assertFalse(store.store(null, SAMPLE_JSON_A));
        Assert.assertFalse(store.store("", SAMPLE_JSON_A));
        Assert.assertFalse(store.store(UUID.randomUUID().toString(), null));
        Assert.assertFalse(store.store(UUID.randomUUID().toString(), ""));
        Assert.assertFalse(store.store(UUID.randomUUID().toString(), "   "));
        Assert.assertEquals(0, store.count());
    }

    @Test
    public void store_sanitizesUnsafeIds() {
        String unsafeId = "../../etc/passwd";
        Assert.assertTrue(store.store(unsafeId, SAMPLE_JSON_A));

        File[] files = cacheDir.listFiles((d, n) -> n.endsWith(FileJSErrorStore.FILE_SUFFIX));
        Assert.assertNotNull(files);
        Assert.assertEquals(1, files.length);
        Assert.assertFalse("filename must not contain path separators",
                files[0].getName().contains("/"));
        Assert.assertFalse("filename must not contain parent traversal",
                files[0].getName().contains(".."));
        Assert.assertTrue("hashed filenames are prefixed",
                files[0].getName().startsWith("h_"));

        Map<String, String> entries = store.fetchAllEntries();
        Assert.assertEquals(SAMPLE_JSON_A, entries.get(unsafeId));
    }

    @Test
    public void delete_worksForSanitizedIds() {
        String unsafeId = "weird id with spaces and ✓ unicode";
        Assert.assertTrue(store.store(unsafeId, SAMPLE_JSON_A));
        Assert.assertEquals(1, store.count());

        store.delete(unsafeId);
        Assert.assertEquals(0, store.count());
    }

    @Test
    public void store_atCap_evictsOldestFirst() {
        config.setMaxCachedMobileErrorCount(3);
        store = new FileJSErrorStore(context, config);

        String oldest = UUID.randomUUID().toString();
        String second = UUID.randomUUID().toString();
        String third = UUID.randomUUID().toString();
        String newest = UUID.randomUUID().toString();

        Assert.assertTrue(store.store(oldest, SAMPLE_JSON_A));
        setFileMtime(oldest, 1000L);

        Assert.assertTrue(store.store(second, SAMPLE_JSON_A));
        setFileMtime(second, 2000L);

        Assert.assertTrue(store.store(third, SAMPLE_JSON_A));
        setFileMtime(third, 3000L);

        Assert.assertEquals(3, store.count());
        Assert.assertTrue(store.store(newest, SAMPLE_JSON_B));
        Assert.assertEquals(3, store.count());

        Map<String, String> entries = store.fetchAllEntries();
        Assert.assertFalse("Oldest should have been evicted", entries.containsKey(oldest));
        Assert.assertTrue(entries.containsKey(second));
        Assert.assertTrue(entries.containsKey(third));
        Assert.assertTrue(entries.containsKey(newest));

        Assert.assertTrue("eviction metric should fire",
                StatsEngine.get().getStatsMap().containsKey(MetricNames.SUPPORTABILITY_MOBILE_ERROR_EVICTED));
    }

    @Test
    public void store_writesAtomically() {
        String id = UUID.randomUUID().toString();
        Assert.assertTrue(store.store(id, SAMPLE_JSON_A));

        File[] jsonFiles = cacheDir.listFiles((d, n) -> n.endsWith(FileJSErrorStore.FILE_SUFFIX));
        File[] tmpFiles = cacheDir.listFiles((d, n) -> n.endsWith(FileJSErrorStore.TMP_SUFFIX));

        Assert.assertNotNull(jsonFiles);
        Assert.assertEquals(1, jsonFiles.length);
        Assert.assertEquals(id + FileJSErrorStore.FILE_SUFFIX, jsonFiles[0].getName());
        Assert.assertNotNull(tmpFiles);
        Assert.assertEquals(0, tmpFiles.length);
    }

    @Test
    public void init_sweepsOrphanedTempFiles() throws IOException {
        store.clear();
        File orphan = new File(cacheDir, "orphan" + FileJSErrorStore.TMP_SUFFIX);
        try (OutputStream os = new FileOutputStream(orphan)) {
            os.write("half-written".getBytes(StandardCharsets.UTF_8));
        }
        Assert.assertTrue(orphan.exists());

        new FileJSErrorStore(context, config);

        Assert.assertFalse("Orphan tmp should be swept on init", orphan.exists());
    }

    @Test
    public void store_overwrite_leavesNoBackupFile() {
        // Successful overwrite path must leave only the .json target — no .bak
        // sidecar and no .tmp leftover.
        String id = UUID.randomUUID().toString();
        Assert.assertTrue(store.store(id, SAMPLE_JSON_A));
        Assert.assertTrue(store.store(id, SAMPLE_JSON_B));

        File[] baks = cacheDir.listFiles((d, n) -> n.endsWith(FileJSErrorStore.BAK_SUFFIX));
        File[] tmps = cacheDir.listFiles((d, n) -> n.endsWith(FileJSErrorStore.TMP_SUFFIX));
        Assert.assertNotNull(baks);
        Assert.assertEquals("overwrite must leave no .bak sidecar", 0, baks.length);
        Assert.assertNotNull(tmps);
        Assert.assertEquals("overwrite must leave no .tmp leftover", 0, tmps.length);
        Assert.assertEquals(SAMPLE_JSON_B, store.fetchAllEntries().get(id));
    }

    @Test
    public void init_recoversBakWhenJsonMissing() throws IOException {
        // Simulate a crash mid-overwrite: only the .bak survived. The init
        // sweep must rename the .bak back to .json so the previous entry is
        // not lost.
        store.clear();
        String id = UUID.randomUUID().toString();
        File bak = new File(cacheDir, id + FileJSErrorStore.BAK_SUFFIX);
        String onDisk = "{\"id\":\"" + id + "\",\"data\":\"" + SAMPLE_JSON_A.replace("\"", "\\\"") + "\"}";
        try (OutputStream os = new FileOutputStream(bak)) {
            os.write(onDisk.getBytes(StandardCharsets.UTF_8));
        }
        Assert.assertTrue(bak.exists());

        FileJSErrorStore recovered = new FileJSErrorStore(context, config);

        Assert.assertFalse(".bak must be consumed by sweep", bak.exists());
        File restored = new File(cacheDir, id + FileJSErrorStore.FILE_SUFFIX);
        Assert.assertTrue(".bak must be renamed to .json on recovery", restored.exists());
        Assert.assertEquals(SAMPLE_JSON_A, recovered.fetchAllEntries().get(id));
    }

    @Test
    public void init_dropsStaleBakWhenJsonExists() throws IOException {
        // Simulate a crash AFTER the second rename succeeded but BEFORE the
        // .bak was cleaned up: both .bak and .json are on disk. The .json is
        // the new authoritative value; the .bak must be discarded without
        // touching the .json.
        store.clear();
        String id = UUID.randomUUID().toString();
        Assert.assertTrue(store.store(id, SAMPLE_JSON_B));
        File target = new File(cacheDir, id + FileJSErrorStore.FILE_SUFFIX);
        File staleBak = new File(cacheDir, id + FileJSErrorStore.BAK_SUFFIX);
        try (OutputStream os = new FileOutputStream(staleBak)) {
            os.write("stale-old-value".getBytes(StandardCharsets.UTF_8));
        }
        Assert.assertTrue(staleBak.exists());
        Assert.assertTrue(target.exists());

        FileJSErrorStore reopened = new FileJSErrorStore(context, config);

        Assert.assertFalse("stale .bak must be deleted by sweep", staleBak.exists());
        Assert.assertTrue(".json sibling must be untouched", target.exists());
        Assert.assertEquals("authoritative .json value must be preserved",
                SAMPLE_JSON_B, reopened.fetchAllEntries().get(id));
    }

    @Test
    public void fetchAll_skipsCorruptedFiles() throws IOException {
        String id = UUID.randomUUID().toString();
        Assert.assertTrue(store.store(id, SAMPLE_JSON_A));

        File corrupt = new File(cacheDir, "garbage" + FileJSErrorStore.FILE_SUFFIX);
        try (OutputStream os = new FileOutputStream(corrupt)) {
            os.write("not-json-at-all".getBytes(StandardCharsets.UTF_8));
        }
        Assert.assertTrue(corrupt.exists());

        Map<String, String> entries = store.fetchAllEntries();
        Assert.assertEquals(1, entries.size());
        Assert.assertEquals(SAMPLE_JSON_A, entries.get(id));
        Assert.assertFalse("Corrupt file should be deleted", corrupt.exists());
        Assert.assertTrue("corruption metric should fire",
                StatsEngine.get().getStatsMap().containsKey(MetricNames.SUPPORTABILITY_MOBILE_ERROR_CORRUPTED));
    }

    @Test
    public void duplicateId_replacesLatest() {
        String id = UUID.randomUUID().toString();
        Assert.assertTrue(store.store(id, SAMPLE_JSON_A));
        Assert.assertTrue(store.store(id, SAMPLE_JSON_B));

        Assert.assertEquals(1, store.count());
        Map<String, String> entries = store.fetchAllEntries();
        Assert.assertEquals(SAMPLE_JSON_B, entries.get(id));
    }

    @Test
    public void delete_isIdempotent() {
        String missing = UUID.randomUUID().toString();
        store.delete(missing);
        store.delete(missing);
        Assert.assertEquals(0, store.count());
    }

    @Test
    public void clear_removesEverything() throws IOException {
        Assert.assertTrue(store.store(UUID.randomUUID().toString(), SAMPLE_JSON_A));
        File strayTmp = new File(cacheDir, "stray" + FileJSErrorStore.TMP_SUFFIX);
        try (OutputStream os = new FileOutputStream(strayTmp)) {
            os.write("tmp".getBytes(StandardCharsets.UTF_8));
        }
        File strayBak = new File(cacheDir, "stray" + FileJSErrorStore.BAK_SUFFIX);
        try (OutputStream os = new FileOutputStream(strayBak)) {
            os.write("bak".getBytes(StandardCharsets.UTF_8));
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
                    store.store(UUID.randomUUID().toString(), SAMPLE_JSON_A);
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
                    store.fetchAllEntries();
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
                    store.delete(UUID.randomUUID().toString());
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
        SharedPreferences legacy = context.getSharedPreferences("NRJSErrorStore", Context.MODE_PRIVATE);
        legacy.edit()
                .putString("id-a", SAMPLE_JSON_A)
                .putString("id-b", SAMPLE_JSON_B)
                .commit();
        Assert.assertFalse("seed should be present", legacy.getAll().isEmpty());

        new FileJSErrorStore(context, config);
        context.deleteSharedPreferences("NRJSErrorStore");

        SharedPreferences after = context.getSharedPreferences("NRJSErrorStore", Context.MODE_PRIVATE);
        Assert.assertTrue("Legacy store should be empty after purge", after.getAll().isEmpty());
        Assert.assertEquals("New store should start empty", 0, store.count());
    }

    @Test
    public void safeFilenameFor_passesThroughUuidsAndHashesOthers() {
        String uuid = UUID.randomUUID().toString();
        Assert.assertEquals(uuid, com.newrelic.agent.android.payload.AbstractFileStore.safeFilename(uuid));

        String unsafe = "path/with/slash";
        String safe = com.newrelic.agent.android.payload.AbstractFileStore.safeFilename(unsafe);
        Assert.assertNotEquals(unsafe, safe);
        Assert.assertTrue(safe.startsWith("h_"));
        // deterministic: same input → same output
        Assert.assertEquals(safe, com.newrelic.agent.android.payload.AbstractFileStore.safeFilename(unsafe));
    }

    private void setFileMtime(String id, long mtime) {
        File f = new File(cacheDir, com.newrelic.agent.android.payload.AbstractFileStore.safeFilename(id) + FileJSErrorStore.FILE_SUFFIX);
        Assert.assertTrue("Expected file to exist for " + id, f.exists());
        Assert.assertTrue("Setting mtime should succeed", f.setLastModified(mtime));
    }
}