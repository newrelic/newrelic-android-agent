package com.newrelic.agent.android.sessionReplay;

public class NewRelicIdGenerator {

    static int counter = 0;

     static int generateId() {
        return counter++;
    }
}
