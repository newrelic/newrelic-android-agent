package com.newrelic.agent.android.util;

import android.content.Context;

import com.newrelic.agent.android.SpyContext;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RunWith(RobolectricTestRunner.class)
public class OfflineStorageTest {
    private Context spyContext;
    private OfflineStorage instance;

    @Before
    public void setUp() {
        spyContext = new SpyContext().getContext();
        instance = new OfflineStorage(spyContext);
        instance.cleanOfflineFiles();
    }

    @After
    public void tearDown() {
        instance.cleanOfflineFiles();
        OfflineStorage.setOfflineStorageTTL(OfflineStorage.DEFAULT_OFFLINE_STORAGE_TTL);
        instance.setOfflineStorageSize(100 * 1024 * 1024);
    }

    @Test
    public void testOfflineStorageInstance() {
        instance = new OfflineStorage(spyContext);
        Assert.assertNotNull(instance);
        instance.cleanOfflineFiles();

        File offlineFolder = instance.getOfflineStorage();
        Assert.assertNotNull(offlineFolder);
        Assert.assertTrue(offlineFolder.exists());

        instance.persistHarvestDataToDisk("{'testKey': 'testValue'}");

        File fakeFile = new File("test");
        instance.setOfflineStorage(fakeFile);
        Assert.assertFalse(fakeFile.exists());

        String offlineFilePath = instance.getOfflineFilePath();
        Assert.assertNotNull(offlineFilePath);

        File file = new File(offlineFilePath);
        Assert.assertTrue(file.exists());

        instance.setOfflineFilePath("test");
        String fakePath = instance.getOfflineFilePath();
        File fakeOfflineFile = new File(fakePath);
        Assert.assertFalse(fakeOfflineFile.exists());
    }

    @Test
    public void testPersistHarvestDataToDisk() {
        instance = new OfflineStorage(spyContext);
        instance.cleanOfflineFiles();

        instance.persistHarvestDataToDisk("{'testKey': 'testValue'}");

        Map<String, String> testKeyValue = instance.getAllOfflineData();
        Assert.assertEquals(1, testKeyValue.size());

        for (Map.Entry<String, String> entry : testKeyValue.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            Assert.assertEquals(instance.getOfflineFilePath(), key);
            Assert.assertEquals("{'testKey': 'testValue'}", value);
        }

        double totalSize = instance.getTotalFileSize();
        Assert.assertNotEquals(0, totalSize);
    }

    @Test
    public void testGetAllOfflineData() {
        try {
            instance = new OfflineStorage(spyContext);
            instance.cleanOfflineFiles();

            Map<String, String> dataSet1 = instance.getAllOfflineData();
            Assert.assertEquals(0, dataSet1.size());

            instance.persistHarvestDataToDisk("{'testKey': 'testValue'}");
            Thread.sleep(1000);
            instance.persistHarvestDataToDisk("{'testKey': 'testValue'}");
            Thread.sleep(1000);
            instance.persistHarvestDataToDisk("{'testKey': 'testValue'}");
            Thread.sleep(1000);
            instance.persistHarvestDataToDisk("{'testKey': 'testValue'}");
            Thread.sleep(1000);
            instance.persistHarvestDataToDisk("{'testKey': 'testValue'}");
            Thread.sleep(1000);

            Map<String, String> dataSet2 = instance.getAllOfflineData();
            Assert.assertEquals(5, dataSet2.size());

            instance.cleanOfflineFiles();
            Map<String, String> dataSet3 = instance.getAllOfflineData();
            Assert.assertEquals(0, dataSet3.size());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testGetTotalFileSize() {
        instance = new OfflineStorage(spyContext);
        instance.cleanOfflineFiles();
        double totalSize = instance.getTotalFileSize();
        Assert.assertEquals(0, totalSize, 0);

        instance.persistHarvestDataToDisk("{'testKey': 'testValue'}");

        double totalSize2 = instance.getTotalFileSize();
        Assert.assertNotEquals(0, totalSize2);
    }

    @Test
    public void testDefaultMaxSize() {
        instance = new OfflineStorage(spyContext);
        instance.cleanOfflineFiles();

        OfflineStorage.setMaxOfflineStorageSize(-1);
        int defaultSize2 = instance.getOfflineStorageSize();
        Assert.assertEquals(100 * 1024 * 1024, defaultSize2);

        instance.setOfflineStorageSize(-1);
        int defaultSize4 = instance.getOfflineStorageSize();
        Assert.assertEquals(-1, defaultSize4);

        instance.setOfflineStorageSize(10);
        int defaultSize5 = instance.getOfflineStorageSize();
        Assert.assertEquals(10, defaultSize5);


        OfflineStorage.setMaxOfflineStorageSize(10);
        int defaultSize3 = instance.getOfflineStorageSize();
        Assert.assertEquals(10, defaultSize3);


        OfflineStorage.setMaxOfflineStorageSize(-1);
        int defaultSize1 = instance.getOfflineStorageSize();
        Assert.assertEquals(100 * 1024 * 1024, defaultSize1);

    }

    @Test
    public void testThreadSafeConcurrentPersistData() {
        instance = new OfflineStorage(spyContext);
        instance.cleanOfflineFiles();

        new Thread() {
            @Override
            public void run() {
                int numPersist = 5;
                for (int i = 0; i < numPersist; i++) {
                    instance.persistHarvestDataToDisk("{'testKey" + i + "':'testValue" + i + "'}");
                    Assert.assertFalse(instance.getAllOfflineData().isEmpty());
                }
            }
        }.start();

        new Thread() {
            @Override
            public void run() {
                instance.cleanOfflineFiles();
                int numPersist = 5;
                for (int i = 0; i < numPersist; i++) {
                    instance.persistHarvestDataToDisk("{'testKey" + i + "':'testValue" + i + "'}");
                    Assert.assertFalse(instance.getAllOfflineData().isEmpty());
                }
            }
        }.start();
    }

    @Test
    public void testDefaultTTL() {
        Assert.assertEquals(TimeUnit.DAYS.toMillis(7), OfflineStorage.DEFAULT_OFFLINE_STORAGE_TTL);
        Assert.assertEquals(OfflineStorage.DEFAULT_OFFLINE_STORAGE_TTL, OfflineStorage.getOfflineStorageTTL());
    }

    @Test
    public void testSetGetTTL() {
        long customTTL = TimeUnit.DAYS.toMillis(14);
        OfflineStorage.setOfflineStorageTTL(customTTL);
        Assert.assertEquals(customTTL, OfflineStorage.getOfflineStorageTTL());
    }

    @Test
    public void testExpiredFilesAreDeletedOnRead() {
        instance.persistHarvestDataToDisk("{'expired': 'data'}");

        File[] files = instance.getOfflineStorage().listFiles();
        Assert.assertNotNull(files);
        Assert.assertEquals(1, files.length);
        Assert.assertTrue(files[0].setLastModified(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(8)));

        Map<String, String> data = instance.getAllOfflineData();
        Assert.assertEquals(0, data.size());
        Assert.assertFalse("Expired file should be deleted from disk", files[0].exists());
    }

    @Test
    public void testNonExpiredFilesAreReturned() {
        instance.persistHarvestDataToDisk("{'fresh': 'data'}");

        Map<String, String> data = instance.getAllOfflineData();
        Assert.assertEquals(1, data.size());
    }

    @Test
    public void testMixedExpiredAndValidFiles() throws InterruptedException {
        instance.persistHarvestDataToDisk("{'expired': 'data'}");
        Thread.sleep(10);
        instance.persistHarvestDataToDisk("{'fresh': 'data'}");

        File[] files = instance.getOfflineStorage().listFiles();
        Assert.assertNotNull(files);
        Assert.assertEquals(2, files.length);
        Arrays.sort(files, Comparator.comparingLong(File::lastModified));
        Assert.assertTrue(files[0].setLastModified(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(8)));

        Map<String, String> data = instance.getAllOfflineData();
        Assert.assertEquals(1, data.size());
        Assert.assertFalse("Expired file should be deleted", files[0].exists());
        Assert.assertTrue("Fresh file should be retained", files[1].exists());
    }

    @Test
    public void testLruEvictionEvictsOldestFile() throws InterruptedException {
        // 3 payloads of ~24 bytes each; cap set so 2 fit but adding a 3rd triggers eviction
        String payload = "{'k':'v','n':'12345678'}";
        instance.setOfflineStorageSize(60);

        instance.persistHarvestDataToDisk(payload);
        Thread.sleep(10);
        instance.persistHarvestDataToDisk(payload);

        File[] filesBefore = instance.getOfflineStorage().listFiles();
        Assert.assertEquals(2, filesBefore.length);
        Arrays.sort(filesBefore, Comparator.comparingLong(File::lastModified));
        File oldest = filesBefore[0];

        boolean saved = instance.persistHarvestDataToDisk(payload);
        Assert.assertTrue("New payload should be saved after eviction", saved);
        Assert.assertFalse("Oldest file should have been evicted", oldest.exists());
    }

    @Test
    public void testPersistReturnsFalseWhenEvictionCannotFreeEnoughSpace() {
        // Cap smaller than a single payload — eviction cannot help
        String payload = "{'k':'v','n':'12345678'}";
        instance.setOfflineStorageSize(10);

        boolean saved = instance.persistHarvestDataToDisk(payload);
        Assert.assertFalse("Should return false when payload exceeds cap", saved);
    }

    @Test
    public void testOversizedPayloadDoesNotEvictExistingFiles() {
        // Store a small payload that fits within cap
        instance.setOfflineStorageSize(50);
        Assert.assertTrue(instance.persistHarvestDataToDisk("{'ok':'data'}"));

        File[] filesBefore = instance.getOfflineStorage().listFiles();
        Assert.assertNotNull(filesBefore);
        Assert.assertEquals(1, filesBefore.length);

        // Payload larger than the cap — should be rejected without evicting the stored file
        String oversized = "{'k':'v','padding':'xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx'}";
        Assert.assertTrue("oversized payload must exceed cap for this test", oversized.getBytes().length > 50);

        boolean saved = instance.persistHarvestDataToDisk(oversized);
        Assert.assertFalse("Oversized payload should be rejected", saved);

        File[] filesAfter = instance.getOfflineStorage().listFiles();
        Assert.assertNotNull(filesAfter);
        Assert.assertEquals("Existing files must not be evicted by an oversized payload", 1, filesAfter.length);
    }
}
