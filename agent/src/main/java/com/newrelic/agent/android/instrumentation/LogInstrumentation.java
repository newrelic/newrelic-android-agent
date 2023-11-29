package com.newrelic.agent.android.instrumentation;

import android.util.Log;

import com.newrelic.agent.android.NewRelic;

import java.util.Map;

public class LogInstrumentation {
    @ReplaceCallSite(isStatic = true)
    public static int d(String tag, String msg, Throwable tr, Map<String, String> attributes) {
        NewRelic.logDebug(tag, msg, tr, attributes);
        return Log.d(tag, msg, tr);
    }

    @ReplaceCallSite(isStatic = true)
    public static int w(String tag, String msg, Throwable tr, Map<String, String> attributes) {
        NewRelic.logWarning(tag, msg, tr, attributes);
        return Log.w(tag, msg, tr);
    }

    @ReplaceCallSite(isStatic = true)
    public static int i(String tag, String msg, Throwable tr, Map<String, String> attributes) {
        NewRelic.logInfo(tag, msg, tr, attributes);
        return Log.i(tag, msg, tr);
    }


    @ReplaceCallSite(isStatic = true)
    public static int v(String tag, String msg, Throwable tr, Map<String, String> attributes) {
        NewRelic.logVerbose(tag, msg, tr, attributes);
        return Log.v(tag, msg, tr);
    }

    @ReplaceCallSite(isStatic = true)
    public static int e(String tag, String msg, Throwable tr, Map<String, String> attributes) {
        NewRelic.logError(tag, msg, tr, attributes);
        return Log.e(tag, msg, tr);
    }
}