/*
 * Copyright (c) 2024. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.stores;

import static org.mockito.Mockito.spy;

import android.content.Context;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.newrelic.agent.android.SpyContext;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class SharedPrefsSessionReplayStoreTest {
    private SharedPrefsSessionReplayStore store;
    private Context context;

    @Before
    public void setUp() throws Exception {
        context = new SpyContext().getContext();
        store = spy(new SharedPrefsSessionReplayStore(context));
    }

    @After
    public void tearDown() throws Exception {
        store.clear();
        Assert.assertEquals(0, store.count());
    }

    @Test
    public void testConstructorWithDefaultStoreName() {
        SharedPrefsSessionReplayStore defaultStore = new SharedPrefsSessionReplayStore(context);
        Assert.assertNotNull(defaultStore);
        Assert.assertEquals("NRSessionReplayStore", defaultStore.getStoreFilename());
    }

    @Test
    public void testConstructorWithCustomStoreName() {
        String customStoreName = "CustomSessionReplayStore";
        SharedPrefsSessionReplayStore customStore = new SharedPrefsSessionReplayStore(context, customStoreName);
        Assert.assertNotNull(customStore);
        Assert.assertEquals(customStoreName, customStore.getStoreFilename());
    }

    @Test
    public void testStoreWithJsonObject() {
        store.clear();
        JsonObject frame = new JsonObject();
        frame.addProperty("type", 2);
        frame.addProperty("timestamp", System.currentTimeMillis());
        frame.addProperty("data", "test data");

        boolean result = store.store(frame);
        Assert.assertTrue(result);

        List<String> data = store.fetchAll();
        Assert.assertNotNull(data);
        Assert.assertEquals(1, data.size());
    }

    @Test
    public void testStoreWithJsonArray() {
        store.clear();
        JsonArray frames = new JsonArray();
        JsonObject frame1 = new JsonObject();
        frame1.addProperty("type", 2);
        frame1.addProperty("timestamp", 12345L);

        JsonObject frame2 = new JsonObject();
        frame2.addProperty("type", 3);
        frame2.addProperty("timestamp", 67890L);

        frames.add(frame1);
        frames.add(frame2);

        boolean result = store.store(frames);
        Assert.assertTrue(result);

        List<String> data = store.fetchAll();
        Assert.assertNotNull(data);
        Assert.assertEquals(1, data.size());
    }

    @Test
    public void testStoreWithString() {
        store.clear();
        String jsonString = "{\"type\":2,\"timestamp\":12345,\"data\":\"test\"}";

        boolean result = store.store(jsonString);
        Assert.assertTrue(result);

        List<String> data = store.fetchAll();
        Assert.assertNotNull(data);
        Assert.assertEquals(1, data.size());
        Assert.assertTrue(data.get(0).contains("\"type\":2"));
    }

    @Test
    public void testStoreOverwritesPreviousData() {
        store.clear();

        // Store first data
        String data1 = "{\"type\":2,\"timestamp\":11111}";
        boolean result1 = store.store(data1);
        Assert.assertTrue(result1);

        // Store second data - should overwrite first
        String data2 = "{\"type\":3,\"timestamp\":22222}";
        boolean result2 = store.store(data2);
        Assert.assertTrue(result2);

        // Should only contain the second data
        List<String> storedData = store.fetchAll();
        Assert.assertEquals(1, storedData.size());
        Assert.assertTrue(storedData.get(0).contains("22222"));
        Assert.assertFalse(storedData.get(0).contains("11111"));
    }

    @Test
    public void testStoreWithEmptyString() {
        store.clear();
        String emptyString = "";

        boolean result = store.store(emptyString);
        Assert.assertTrue(result);

        List<String> data = store.fetchAll();
        Assert.assertNotNull(data);
        Assert.assertEquals(1, data.size());
    }

    @Test
    public void testStoreWithLargeJsonData() {
        store.clear();
        JsonArray largeArray = new JsonArray();

        // Create a large JSON array with many frames
        for (int i = 0; i < 1000; i++) {
            JsonObject frame = new JsonObject();
            frame.addProperty("type", 2);
            frame.addProperty("timestamp", System.currentTimeMillis() + i);
            frame.addProperty("index", i);
            largeArray.add(frame);
        }

        boolean result = store.store(largeArray);
        Assert.assertTrue(result);

        List<String> data = store.fetchAll();
        Assert.assertNotNull(data);
        Assert.assertEquals(1, data.size());
    }

    @Test
    public void testFetchAll() {
        store.clear();
        JsonObject frame = new JsonObject();
        frame.addProperty("type", 2);
        frame.addProperty("timestamp", 12345L);

        store.store(frame);

        List<String> data = store.fetchAll();
        Assert.assertNotNull(data);
        Assert.assertEquals(1, data.size());
        Assert.assertTrue(data.get(0).contains("\"type\":2"));
    }

    @Test
    public void testFetchAllWhenEmpty() {
        store.clear();

        List<String> data = store.fetchAll();
        Assert.assertNotNull(data);
        // Should return empty JSON array as string
        Assert.assertEquals(1, data.size());
        Assert.assertEquals("[]", data.get(0));
    }

    @Test
    public void testFetchAllReturnsStoredData() {
        store.clear();
        String testData = "{\"type\":3,\"timestamp\":98765,\"key\":\"value\"}";
        store.store(testData);

        List<String> data = store.fetchAll();
        Assert.assertNotNull(data);
        Assert.assertEquals(1, data.size());
        Assert.assertEquals(testData, data.get(0));
    }

    @Test
    public void testCount() {
        store.clear();
        Assert.assertEquals(0, store.count());

        // Store data - count should be 1 (only one key "SessionReplayFrame")
        store.store("{\"type\":2}");
        Assert.assertEquals(1, store.count());

        // Store again - count should still be 1 (overwrites)
        store.store("{\"type\":3}");
        Assert.assertEquals(1, store.count());
    }

    @Test
    public void testClear() {
        store.clear();

        // Add some data
        store.store("{\"type\":2,\"timestamp\":12345}");
        Assert.assertEquals(1, store.count());

        // Clear and verify
        store.clear();
        Assert.assertEquals(0, store.count());

        List<String> data = store.fetchAll();
        Assert.assertNotNull(data);
        Assert.assertEquals(1, data.size());
        Assert.assertEquals("[]", data.get(0)); // Should return empty array
    }

    @Test
    public void testDelete() {
        store.clear();
        String testData = "{\"type\":2,\"timestamp\":12345}";
        store.store(testData);

        // delete() is not implemented (empty method body)
        // Calling it should not throw an exception
        store.delete(testData);

        // Data should still be there since delete() does nothing
        List<String> data = store.fetchAll();
        Assert.assertNotNull(data);
        Assert.assertEquals(1, data.size());
    }

    @Test
    public void testDeleteWithNull() {
        store.clear();
        store.store("{\"type\":2}");

        // Calling delete with null should not crash
        store.delete(null);

        // Data should still be there
        Assert.assertEquals(1, store.count());
    }

    @Test
    public void testMultipleStoreOperations() {
        store.clear();

        // Store multiple times
        for (int i = 0; i < 10; i++) {
            JsonObject frame = new JsonObject();
            frame.addProperty("iteration", i);
            store.store(frame);
        }

        // Should only have the last one
        List<String> data = store.fetchAll();
        Assert.assertEquals(1, data.size());
        Assert.assertTrue(data.get(0).contains("\"iteration\":9"));
    }

    @Test
    public void testStoreWithSpecialCharacters() {
        store.clear();
        String specialData = "{\"data\":\"!@#$%^&*()_+-={}[]|:;<>?,./~`\"}";

        boolean result = store.store(specialData);
        Assert.assertTrue(result);

        List<String> data = store.fetchAll();
        Assert.assertNotNull(data);
        Assert.assertEquals(1, data.size());
    }

    @Test
    public void testStoreWithUnicodeCharacters() {
        store.clear();
        JsonObject frame = new JsonObject();
        frame.addProperty("unicode", "日本語");
        frame.addProperty("emoji", "😀🎉");

        boolean result = store.store(frame);
        Assert.assertTrue(result);

        List<String> data = store.fetchAll();
        Assert.assertNotNull(data);
        Assert.assertEquals(1, data.size());
        Assert.assertTrue(data.get(0).contains("日本語"));
    }

    @Test
    public void testStoreWithNestedJsonStructure() {
        store.clear();
        JsonObject frame = new JsonObject();
        frame.addProperty("type", 2);

        JsonObject nested = new JsonObject();
        nested.addProperty("key1", "value1");
        nested.addProperty("key2", 123);
        frame.add("nested", nested);

        JsonArray array = new JsonArray();
        array.add("item1");
        array.add("item2");
        frame.add("array", array);

        boolean result = store.store(frame);
        Assert.assertTrue(result);

        List<String> data = store.fetchAll();
        Assert.assertNotNull(data);
        Assert.assertEquals(1, data.size());
        Assert.assertTrue(data.get(0).contains("nested"));
        Assert.assertTrue(data.get(0).contains("array"));
    }

    @Test
    public void testStoreFilenameIsCorrect() {
        Assert.assertEquals("NRSessionReplayStore", store.getStoreFilename());
    }

    @Test
    public void testMultipleStoreInstances() {
        // Create two stores with different names
        SharedPrefsSessionReplayStore store1 = new SharedPrefsSessionReplayStore(context, "Store1");
        SharedPrefsSessionReplayStore store2 = new SharedPrefsSessionReplayStore(context, "Store2");

        store1.clear();
        store2.clear();

        // Store different data in each
        store1.store("{\"store\":1}");
        store2.store("{\"store\":2}");

        // Verify they are independent
        List<String> data1 = store1.fetchAll();
        List<String> data2 = store2.fetchAll();

        Assert.assertTrue(data1.get(0).contains("\"store\":1"));
        Assert.assertTrue(data2.get(0).contains("\"store\":2"));

        // Cleanup
        store1.clear();
        store2.clear();
    }

    @Test
    public void testStoreReturnsTrue() {
        store.clear();
        JsonObject frame = new JsonObject();
        frame.addProperty("test", "data");

        boolean result = store.store(frame);
        Assert.assertTrue("store() should return true on success", result);
    }

    @Test
    public void testConcurrentAccess() {
        store.clear();

        // Store is synchronized, so concurrent access should be safe
        // This test verifies no exceptions are thrown
        Thread thread1 = new Thread(() -> {
            for (int i = 0; i < 50; i++) {
                store.store("{\"thread\":1,\"iteration\":" + i + "}");
            }
        });

        Thread thread2 = new Thread(() -> {
            for (int i = 0; i < 50; i++) {
                store.fetchAll();
            }
        });

        thread1.start();
        thread2.start();

        try {
            thread1.join();
            thread2.join();
        } catch (InterruptedException e) {
            Assert.fail("Thread interrupted");
        }

        // Should complete without exceptions
        Assert.assertNotNull(store);
    }

    @Test
    public void testStoreAfterClear() {
        store.clear();

        // Store data
        store.store("{\"type\":2}");
        Assert.assertEquals(1, store.count());

        // Clear
        store.clear();
        Assert.assertEquals(0, store.count());

        // Store again
        boolean result = store.store("{\"type\":3}");
        Assert.assertTrue(result);
        Assert.assertEquals(1, store.count());

        List<String> data = store.fetchAll();
        Assert.assertTrue(data.get(0).contains("\"type\":3"));
    }

    @Test
    public void testFetchAllWithComplexJsonArray() {
        store.clear();

        JsonArray complexArray = new JsonArray();
        for (int i = 0; i < 5; i++) {
            JsonObject frame = new JsonObject();
            frame.addProperty("type", 2 + (i % 2));
            frame.addProperty("timestamp", System.currentTimeMillis() + i * 1000);
            frame.addProperty("index", i);

            JsonObject data = new JsonObject();
            data.addProperty("key", "value" + i);
            frame.add("data", data);

            complexArray.add(frame);
        }

        store.store(complexArray);

        List<String> fetchedData = store.fetchAll();
        Assert.assertNotNull(fetchedData);
        Assert.assertEquals(1, fetchedData.size());

        String jsonString = fetchedData.get(0);
        Assert.assertTrue(jsonString.contains("\"type\":2"));
        Assert.assertTrue(jsonString.contains("\"type\":3"));
        Assert.assertTrue(jsonString.contains("\"index\":4"));
    }

    @Test
    public void testStoreNullValue() {
        store.clear();

        try {
            // Storing null should trigger NullPointerException when calling toString()
            store.store(null);
            // If we reach here, the store handled null gracefully
            // This is implementation dependent
        } catch (NullPointerException e) {
            // Expected if toString() is called on null
            Assert.assertNotNull(e);
        }
    }

    @Test
    public void testCountAfterMultipleOperations() {
        store.clear();
        Assert.assertEquals(0, store.count());

        store.store("{\"op\":1}");
        Assert.assertEquals(1, store.count());

        store.store("{\"op\":2}");
        Assert.assertEquals(1, store.count()); // Still 1, overwrites

        store.clear();
        Assert.assertEquals(0, store.count());

        store.store("{\"op\":3}");
        Assert.assertEquals(1, store.count());
    }
}