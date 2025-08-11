package com.newrelic.agent.android.stores;

import com.newrelic.agent.android.SpyContext;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@RunWith(RobolectricTestRunner.class)
public class DataStoreHelperTest {
    private SpyContext spyContext;
    private DataStoreHelpler helper;


    @Before
    public void setUp() {
        spyContext = new SpyContext();
        helper = new DataStoreHelpler(spyContext.getContext(), "testDataStoreFile");
    }

    @Test
    public void testStoreFilename() {
        Assert.assertEquals("testDataStoreFile", helper.getStoreFilename());
    }

    @Test
    public void testPutStringValue() {
        boolean result = helper.putStringValue("testKey", "testValue");
        Assert.assertEquals(helper.getStringValue("testKey"), "testValue");
    }

    @Test
    public void testPutLongValue() {
        boolean result = helper.putLongValue("testKey", 123L);
        Assert.assertEquals(helper.getLongSetValue("testKey"), 123L);
    }

    @Test
    public void testPutBooleanValue() {
        boolean result = helper.putBooleanValue("testKey", true);
        Assert.assertEquals(helper.getBooleanSetValue("testKey"), true);
    }

    @Test
    public void testPreferenceFunctions() {
        helper.clear();
        Assert.assertEquals(helper.count(), 0);

        boolean resultByte = helper.store("byteKey", "byteValue".getBytes());
        Assert.assertTrue(resultByte);
        Assert.assertEquals(helper.getStringValue("byteKey"), "byteValue");

        boolean resultSet = helper.store("setKey", Set.of("set1", "set2"));
        Assert.assertTrue(resultSet);
        Assert.assertEquals(helper.getStringSetValue("setKey"), Set.of("set1", "set2"));

        boolean resultString = helper.store("stringKey", "stringValue");
        Assert.assertTrue(resultString);
        Assert.assertEquals(helper.getStringValue("stringKey"), "stringValue");

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
    }
}
