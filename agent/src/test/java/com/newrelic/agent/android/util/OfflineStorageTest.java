package com.newrelic.agent.android.util;

import android.content.Context;
import android.os.Environment;

import com.newrelic.agent.android.SpyContext;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.util.Map;

@RunWith(RobolectricTestRunner.class)
public class OfflineStorageTest {
    private static final String OFFLINE_STORAGE = "nr_offline_storage";
    private Context spyContext;
    private static File offlineStorage = new File(Environment.getDataDirectory(), OFFLINE_STORAGE);
    private static File testFile;
    private OfflineStorage instance;

    @Before
    public void setUp() {
        spyContext = new SpyContext().getContext();
    }

    @Test
    public void testOfflineStorageInstance() {
        instance = new OfflineStorage(spyContext);
        Assert.assertNotNull(instance);
        instance.cleanOfflineFiles();

        File offlineFolder = instance.getOfflineStorage();
        Assert.assertNotNull(offlineFolder);
        Assert.assertTrue(offlineFolder.exists());

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
    public void testPersistDataToDisk() {
        instance = new OfflineStorage(spyContext);
        instance.cleanOfflineFiles();

        instance.persistDataToDisk("{'testKey': 'testValue'}");

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

            instance.persistDataToDisk("{'testKey': 'testValue'}");
            Thread.sleep(1000);
            instance.persistDataToDisk("{'testKey': 'testValue'}");
            Thread.sleep(1000);
            instance.persistDataToDisk("{'testKey': 'testValue'}");
            Thread.sleep(1000);
            instance.persistDataToDisk("{'testKey': 'testValue'}");
            Thread.sleep(1000);
            instance.persistDataToDisk("{'testKey': 'testValue'}");
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

        instance.persistDataToDisk("{'testKey': 'testValue'}");

        double totalSize2 = instance.getTotalFileSize();
        Assert.assertNotEquals(0, totalSize2);
    }

    @Test
    public void testDefaultMaxSize() {
        instance = new OfflineStorage(spyContext);
        instance.cleanOfflineFiles();

        int defaultSize1 = instance.getOfflineStorageSize();
        Assert.assertEquals(100 * 1024 * 1024, defaultSize1);

        OfflineStorage.setMaxOfflineStorageSize(-1);
        int defaultSize2 = instance.getOfflineStorageSize();
        Assert.assertEquals(100 * 1024 * 1024, defaultSize2);

        OfflineStorage.setMaxOfflineStorageSize(10);
        int defaultSize3 = instance.getOfflineStorageSize();
        Assert.assertEquals(10, defaultSize3);

        instance.setOfflineStorageSize(-1);
        int defaultSize4 = instance.getOfflineStorageSize();
        Assert.assertEquals(-1, defaultSize4);

        instance.setOfflineStorageSize(10);
        int defaultSize5 = instance.getOfflineStorageSize();
        Assert.assertEquals(10, defaultSize5);
    }
}
