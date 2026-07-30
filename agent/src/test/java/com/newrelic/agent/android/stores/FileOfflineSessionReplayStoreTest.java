/*
 * Copyright (c) 2026-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.stores;

import android.content.Context;

import com.newrelic.agent.android.SpyContext;
import com.newrelic.agent.android.metric.MetricNames;
import com.newrelic.agent.android.payload.AbstractFileStore;
import com.newrelic.agent.android.sessionReplay.OfflineSessionReplayPayload;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RunWith(RobolectricTestRunner.class)
public class FileOfflineSessionReplayStoreTest {

    private Context context;
    private FileOfflineSessionReplayStore store;
    private File cacheDir;

    @Before
    public void setUp() {
        context = new SpyContext().getContext();
        store = new FileOfflineSessionReplayStore(context);
        cacheDir = new File(context.getFilesDir(), FileOfflineSessionReplayStore.DIR_NAME);
        StatsEngine.get().getStatsMap().clear();
        StatsEngine.SUPPORTABILITY.getStatsMap().clear();
    }

    @After
    public void tearDown() {
        if (store != null) {
            store.clear();
        }
    }

    private static OfflineSessionReplayPayload buildPayload(String uuid, long capturedAt, byte[] body) {
        Map<String, String> attrs = new LinkedHashMap<>();
        attrs.put("entityGuid", "guid-1");
        attrs.put("sessionId", "session-" + uuid);
        attrs.put("replay.firstTimestamp", "1000");
        attrs.put("replay.lastTimestamp", "2000");
        return new OfflineSessionReplayPayload(uuid, capturedAt, capturedAt, attrs, body);
    }

    private static OfflineSessionReplayPayload buildPayload(String uuid) {
        return buildPayload(uuid, System.currentTimeMillis(),
                ("body-" + uuid).getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void store_then_fetchAll_roundTrip() {
        OfflineSessionReplayPayload a = buildPayload(UUID.randomUUID().toString());
        OfflineSessionReplayPayload b = buildPayload(UUID.randomUUID().toString());

        Assert.assertTrue(store.store(a));
        Assert.assertTrue(store.store(b));
        Assert.assertEquals(2, store.count());

        List<OfflineSessionReplayPayload> fetched = store.fetchAll();
        Assert.assertEquals(2, fetched.size());

        // round-tripped payloads must preserve all fields
        boolean foundA = false;
        boolean foundB = false;
        for (OfflineSessionReplayPayload p : fetched) {
            if (a.getUuid().equals(p.getUuid())) {
                foundA = true;
                Assert.assertArrayEquals(a.getBody(), p.getBody());
                Assert.assertEquals(a.getAttributes(), p.getAttributes());
                Assert.assertEquals(a.getCapturedAt(), p.getCapturedAt());
                Assert.assertEquals(a.getUrlTimestamp(), p.getUrlTimestamp());
            } else if (b.getUuid().equals(p.getUuid())) {
                foundB = true;
            }
        }
        Assert.assertTrue(foundA);
        Assert.assertTrue(foundB);
    }

    @Test
    public void store_atCap_evictsOldestFirst() {
        // The store has a hard-coded cap of 50; we don't override it. Instead we
        // verify the eviction path on a single-item overflow by setting mtimes.
        // For thoroughness, fill 50 then add a 51st and check count stays at 50.
        for (int i = 0; i < FileOfflineSessionReplayStore.MAX_COUNT; i++) {
            String uuid = "uuid-" + i;
            Assert.assertTrue(store.store(buildPayload(uuid)));
            // make oldest sortable: write incrementally older mtimes for first few
            File f = new File(cacheDir, uuid + AbstractFileStore.FILE_SUFFIX);
            Assert.assertTrue(f.exists());
            f.setLastModified(1000L + i);
        }
        Assert.assertEquals(FileOfflineSessionReplayStore.MAX_COUNT, store.count());

        OfflineSessionReplayPayload newest = buildPayload("uuid-newest");
        Assert.assertTrue(store.store(newest));
        Assert.assertEquals(FileOfflineSessionReplayStore.MAX_COUNT, store.count());

        // Oldest entry (uuid-0) should be gone.
        File oldest = new File(cacheDir, "uuid-0" + AbstractFileStore.FILE_SUFFIX);
        Assert.assertFalse("oldest should have been evicted", oldest.exists());

        Assert.assertTrue("eviction metric should fire",
                StatsEngine.get().getStatsMap()
                        .containsKey(MetricNames.SUPPORTABILITY_SESSION_REPLAY_OFFLINE_EVICTED));
    }

    @Test
    public void delete_removesOnlyTargetFile() {
        OfflineSessionReplayPayload a = buildPayload(UUID.randomUUID().toString());
        OfflineSessionReplayPayload b = buildPayload(UUID.randomUUID().toString());
        Assert.assertTrue(store.store(a));
        Assert.assertTrue(store.store(b));

        store.delete(a);
        Assert.assertEquals(1, store.count());

        List<OfflineSessionReplayPayload> remaining = store.fetchAll();
        Assert.assertEquals(1, remaining.size());
        Assert.assertEquals(b.getUuid(), remaining.get(0).getUuid());
    }

    @Test
    public void delete_nullIsNoOp() {
        Assert.assertTrue(store.store(buildPayload(UUID.randomUUID().toString())));
        store.delete(null);
        Assert.assertEquals(1, store.count());
    }

    @Test
    public void clear_removesEverything() throws IOException {
        Assert.assertTrue(store.store(buildPayload(UUID.randomUUID().toString())));
        Assert.assertTrue(store.store(buildPayload(UUID.randomUUID().toString())));

        // also leave a stray tmp file that clear() should wipe
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
    public void fetchAll_skipsCorruptedFiles() throws IOException {
        OfflineSessionReplayPayload good = buildPayload(UUID.randomUUID().toString());
        Assert.assertTrue(store.store(good));

        File corrupt = new File(cacheDir, "garbage" + AbstractFileStore.FILE_SUFFIX);
        try (OutputStream os = new FileOutputStream(corrupt)) {
            os.write("not-json-at-all".getBytes(StandardCharsets.UTF_8));
        }
        Assert.assertTrue(corrupt.exists());

        List<OfflineSessionReplayPayload> fetched = store.fetchAll();
        Assert.assertEquals(1, fetched.size());
        Assert.assertEquals(good.getUuid(), fetched.get(0).getUuid());
        Assert.assertFalse("corrupt file should be deleted", corrupt.exists());
        Assert.assertTrue("corruption metric should fire",
                StatsEngine.get().getStatsMap()
                        .containsKey(MetricNames.SUPPORTABILITY_SESSION_REPLAY_OFFLINE_CORRUPTED));
    }

    @Test
    public void store_rejectsNull() {
        Assert.assertFalse(store.store(null));
        Assert.assertEquals(0, store.count());
    }

    @Test
    public void fetchAll_whenEmpty_returnsEmptyList() {
        List<OfflineSessionReplayPayload> fetched = store.fetchAll();
        Assert.assertNotNull(fetched);
        Assert.assertTrue(fetched.isEmpty());
    }

    @Test
    public void fetchAll_returnsOldestFirst() {
        OfflineSessionReplayPayload first = buildPayload("first");
        OfflineSessionReplayPayload second = buildPayload("second");
        OfflineSessionReplayPayload third = buildPayload("third");

        Assert.assertTrue(store.store(first));
        Assert.assertTrue(new File(cacheDir, "first" + AbstractFileStore.FILE_SUFFIX).setLastModified(1000L));
        Assert.assertTrue(store.store(second));
        Assert.assertTrue(new File(cacheDir, "second" + AbstractFileStore.FILE_SUFFIX).setLastModified(2000L));
        Assert.assertTrue(store.store(third));
        Assert.assertTrue(new File(cacheDir, "third" + AbstractFileStore.FILE_SUFFIX).setLastModified(3000L));

        List<OfflineSessionReplayPayload> fetched = store.fetchAll();
        Assert.assertEquals(3, fetched.size());
        Assert.assertEquals("first", fetched.get(0).getUuid());
        Assert.assertEquals("second", fetched.get(1).getUuid());
        Assert.assertEquals("third", fetched.get(2).getUuid());
    }

    @Test
    public void duplicateUuid_replacesEntry() {
        String uuid = UUID.randomUUID().toString();
        OfflineSessionReplayPayload v1 = buildPayload(uuid, 1L, "v1".getBytes(StandardCharsets.UTF_8));
        OfflineSessionReplayPayload v2 = buildPayload(uuid, 2L, "v2-replaced".getBytes(StandardCharsets.UTF_8));

        Assert.assertTrue(store.store(v1));
        Assert.assertTrue(store.store(v2));
        Assert.assertEquals(1, store.count());

        OfflineSessionReplayPayload fetched = store.fetchAll().get(0);
        Assert.assertArrayEquals("v2-replaced".getBytes(StandardCharsets.UTF_8), fetched.getBody());
        Assert.assertEquals(2L, fetched.getCapturedAt());
    }

    @Test
    public void binaryBody_survivesRoundTrip() {
        byte[] binary = new byte[256];
        for (int i = 0; i < 256; i++) {
            binary[i] = (byte) i;
        }
        OfflineSessionReplayPayload p = buildPayload("bin", System.currentTimeMillis(), binary);

        Assert.assertTrue(store.store(p));
        OfflineSessionReplayPayload fetched = store.fetchAll().get(0);
        Assert.assertArrayEquals(binary, fetched.getBody());
    }
}