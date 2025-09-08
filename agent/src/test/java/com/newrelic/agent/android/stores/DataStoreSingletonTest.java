package com.newrelic.agent.android.stores;

import androidx.datastore.core.DataStore;
import androidx.datastore.preferences.core.Preferences;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class DataStoreSingletonTest {
    DataStoreSingleton instance;
    DataStore<Preferences> datastore;

    @Before
    public void setUp() {
        instance = DataStoreSingleton.getInstance();
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
