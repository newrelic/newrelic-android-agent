package com.newrelic.agent.android.stores;

import androidx.datastore.core.DataStore;
import androidx.datastore.preferences.core.Preferences;

public class DataStoreSingleton {
    DataStore<Preferences> datastore = null;
    private static DataStoreSingleton ourInstance = new DataStoreSingleton();

    public static DataStoreSingleton getInstance() {
        return ourInstance;
    }

    private DataStoreSingleton() {
    }

    public void setDataStore(DataStore<Preferences> datastore) {
        this.datastore = datastore;
    }

    public DataStore<Preferences> getDataStore() {
        return datastore;
    }
}
