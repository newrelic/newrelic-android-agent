package com.newrelic.agent.android.stores;

import androidx.datastore.core.DataStore;

import com.newrelic.agent.android.SpyContext;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@RunWith(RobolectricTestRunner.class)
public class DataStoreHelperTest {
    private SpyContext spyContext;
    private DataStoreHelper helper;


    @Before
    public void setUp() {
        spyContext = new SpyContext();
        helper = new DataStoreHelper(spyContext.getContext(), "testDataStoreFile");
    }

    @Test
    public void testStoreFilename() {
        Assert.assertEquals("testDataStoreFile", helper.getStoreFilename());
    }

    @Test
    public void testPutStringValue() throws ExecutionException, InterruptedException, TimeoutException {
        boolean setValue = helper.putStringValue("testKey", "testValue").get();
        String result = helper.getStringValue("testKey").get(5, TimeUnit.SECONDS);
        Assert.assertEquals(result, "testValue");
    }

    @Test
    public void testPutLongValue() throws ExecutionException, InterruptedException, TimeoutException {
        boolean setValue = helper.putLongValue("testKey", 123L).get();
        Assert.assertEquals(Long.valueOf(helper.getLongValue("testKey").get(5, TimeUnit.SECONDS)), Long.valueOf(123));
    }

    @Test
    public void testPutBooleanValue() throws ExecutionException, InterruptedException, TimeoutException {
        boolean setValue = helper.putBooleanValue("testKey", true).get();
        Assert.assertEquals(helper.getBooleanValue("testKey").get(5, TimeUnit.SECONDS), true);
    }

    @Test
    public void testPreferenceFunctions() {
        try {
            helper.clear();
            Assert.assertEquals(helper.count(), 0);

            boolean resultByte = helper.store("byteKey", "byteValue".getBytes());
            Assert.assertTrue(resultByte);
            Assert.assertEquals(helper.getStringValue("byteKey").get(), "byteValue");

            boolean resultSet = helper.store("setKey", Set.of("set1", "set2"));
            Assert.assertTrue(resultSet);
            Assert.assertEquals(helper.getStringSetValue("setKey").get(), Set.of("set1", "set2"));

            boolean resultString = helper.store("stringKey", "stringValue");
            Assert.assertTrue(resultString);
            Assert.assertEquals(helper.getStringValue("stringKey").get(), "stringValue");

            List<?> list = helper.fetchAll();
            Assert.assertTrue(list.contains("byteValue"));
            Assert.assertTrue(list.contains("stringValue"));
            Assert.assertTrue(list.contains(Set.of("set1", "set2")));
            Assert.assertEquals(helper.count(), 3);

            helper.delete("byteKey");
            list = helper.fetchAll();
            Assert.assertFalse(list.contains("byteValue"));
            Assert.assertEquals(helper.count(), 2);

            helper.delete("stringKey");
            list = helper.fetchAll();
            Assert.assertFalse(list.contains("stringValue"));
            Assert.assertEquals(helper.count(), 1);

            helper.delete("setKey");
            list = helper.fetchAll();
            Assert.assertFalse(list.contains(Set.of("set1", "set2")));
            Assert.assertEquals(helper.count(), 0);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
