package com.newrelic.agent.android.stores;

import androidx.datastore.preferences.core.Preferences;
import androidx.datastore.rxjava2.RxDataStore;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class DataStoreSingletonTest {
    DataStoreSingleton instance;
    RxDataStore<Preferences> datastore;

    @Before
    public void setUp() {
        instance = DataStoreSingleton.getInstance();
        // Reset the singleton instance before each test
        DataStoreSingleton.resetInstanceForTesting();
    }

    @Test
    public void getSameInstance() {
        DataStoreSingleton instance1 = DataStoreSingleton.getInstance();
        DataStoreSingleton instance2 = DataStoreSingleton.getInstance();
        Assert.assertSame(instance1, instance2);
    }

    @Test
    public void dataStoreTest() {
        Assert.assertNull(instance.getDataStore());
    }
}
