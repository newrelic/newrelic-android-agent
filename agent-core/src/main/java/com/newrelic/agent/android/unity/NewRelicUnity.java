package com.newrelic.agent.android.unity;

import com.newrelic.agent.android.crash.CrashReporter;

public class NewRelicUnity {

    /**
     * Accepts a UnityException from the Unity plugin and re-throws it in the JVM.
     *
     * @param ex
     */
    static void handleUnityCrash(UnityException ex) {
        java.lang.Thread.UncaughtExceptionHandler currentExceptionHandler = Thread.getDefaultUncaughtExceptionHandler();
        if (currentExceptionHandler != null) {
                currentExceptionHandler.uncaughtException(Thread.currentThread(), ex);
        }
    }
}
