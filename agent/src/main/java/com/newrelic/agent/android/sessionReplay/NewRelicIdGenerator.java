package com.newrelic.agent.android.sessionReplay;



import android.util.Log;

public class NewRelicIdGenerator {

    private static final String TAG = "NewRelicIdGenerator";

     // This is a simple counter to generate unique IDs.
     // In a real-world scenario, you might want to use a more robust method to ensure uniqueness,
     // especially in a multi-threaded environment.
     // For example, you could use AtomicInteger or UUIDs.
    static int counter = 0;

     static int generateId() {
         Log.d(TAG, "generateId: " + counter);
        return counter++;
    }
}
