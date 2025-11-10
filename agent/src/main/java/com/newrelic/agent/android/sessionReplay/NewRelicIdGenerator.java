package com.newrelic.agent.android.sessionReplay;



import android.util.Log;

import java.util.concurrent.atomic.AtomicInteger;

public class NewRelicIdGenerator {

    private static final String TAG = "NewRelicIdGenerator";


    /**
     * ID offset to avoid collision with Compose SemanticsNode IDs.
     * SemanticsNode IDs start at 0 and increment, so we offset our generated IDs
     * to a high value (1,000,000) to ensure no overlap between traditional View IDs
     * and Compose node IDs in the same screen.
     */
    private static final int ID_OFFSET = 1_000_000;

    /**
     * Counter for generating unique IDs for traditional Android Views.
     * Starts at ID_OFFSET to avoid collision with Compose SemanticsNode IDs (which start at 0).
     *
     * Note: This is a simple counter. In a multi-threaded environment, consider using AtomicInteger
     * for thread-safety, though current usage via setTag/getTag on views is typically main-thread only.
     */
    private static final AtomicInteger counter = new AtomicInteger(ID_OFFSET);

    /**
     * Generates a unique ID for traditional Android Views that won't collide with Compose SemanticsNode IDs.
     *
     * @return A unique ID starting from 1,000,000 to avoid collision with Compose IDs (0-999,999 range)
     */
    public static int generateId() {
        return  counter.getAndIncrement();
    }
}
