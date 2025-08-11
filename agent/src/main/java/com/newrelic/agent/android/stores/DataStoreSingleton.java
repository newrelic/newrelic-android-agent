package com.newrelic.agent.android.stores;

import androidx.datastore.preferences.core.Preferences;
import androidx.datastore.rxjava2.RxDataStore;

public class DataStoreSingleton {
    RxDataStore<Preferences> datastore = null;
    private static DataStoreSingleton ourInstance = new DataStoreSingleton();

    public static DataStoreSingleton getInstance() {
        return ourInstance;
    }

    private DataStoreSingleton() {
    }

    public void setDataStore(RxDataStore<Preferences> datastore) {
        this.datastore = datastore;
    }

    public RxDataStore<Preferences> getDataStore() {
        return datastore;
    }

    //For unit testing purpose ONLY
    public static void resetInstanceForTesting() {
        ourInstance = null;
    }
}
