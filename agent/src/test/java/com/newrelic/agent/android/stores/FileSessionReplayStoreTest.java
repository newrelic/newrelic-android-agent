/*
 * Copyright (c) 2026-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.stores;

import android.content.Context;

import com.newrelic.agent.android.SpyContext;
import com.newrelic.agent.android.payload.AbstractFileStore;

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

@RunWith(RobolectricTestRunner.class)
public class FileSessionReplayStoreTest {

    private Context context;
    private FileSessionReplayStore store;
    private File cacheDir;

    @Before
    public void setUp() {
        context = new SpyContext().getContext();
        store = new FileSessionReplayStore(context);
        cacheDir = new File(context.getFilesDir(), FileSessionReplayStore.DIR_NAME);
    }

    @After
    public void tearDown() {
        if (store != null) {
            store.clear();
        }
    }

    @Test
    public void store_then_fetchAll_returnsLastFrame() {
        Assert.assertTrue(store.store("{\"frame\":1}"));

        List<Object> fetched = store.fetchAll();
        Assert.assertEquals(1, fetched.size());
        Assert.assertEquals("{\"frame\":1}", fetched.get(0).toString());
    }

    @Test
    public void store_overwritesPreviousFrame() {
        Assert.assertTrue(store.store("{\"frame\":1}"));
        Assert.assertTrue(store.store("{\"frame\":2}"));
        Assert.assertTrue(store.store("{\"frame\":3}"));

        Assert.assertEquals(1, store.count());
        List<Object> fetched = store.fetchAll();
        Assert.assertEquals(1, fetched.size());
        Assert.assertEquals("{\"frame\":3}", fetched.get(0).toString());
    }

    @Test
    public void store_writesAtomicallyToFixedFilename() {
        Assert.assertTrue(store.store("{\"frame\":1}"));

        File[] jsonFiles = cacheDir.listFiles((d, n) -> n.endsWith(AbstractFileStore.FILE_SUFFIX));
        File[] tmpFiles = cacheDir.listFiles((d, n) -> n.endsWith(AbstractFileStore.TMP_SUFFIX));

        Assert.assertNotNull(jsonFiles);
        Assert.assertEquals(1, jsonFiles.length);
        Assert.assertEquals(FileSessionReplayStore.FIXED_KEY + AbstractFileStore.FILE_SUFFIX, jsonFiles[0].getName());
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

        new FileSessionReplayStore(context);

        Assert.assertFalse("Orphan tmp should be swept on init", orphan.exists());
    }

    @Test
    public void delete_isNoOp() {
        Assert.assertTrue(store.store("{\"frame\":1}"));
        store.delete("{\"frame\":1}");
        Assert.assertEquals("delete() must not remove the single frame", 1, store.count());
    }

    @Test
    public void clear_removesEverything() throws IOException {
        Assert.assertTrue(store.store("{\"frame\":1}"));
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
    public void store_rejectsNull() {
        Assert.assertFalse(store.store(null));
        Assert.assertEquals(0, store.count());
    }

    @Test
    public void fetchAll_whenEmpty_returnsEmptyList() {
        List<Object> fetched = store.fetchAll();
        Assert.assertNotNull(fetched);
        Assert.assertTrue(fetched.isEmpty());
    }
}