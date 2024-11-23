package com.newrelic.agent.android.instrumentation;

import android.util.Log;

import com.newrelic.agent.android.logging.LogLevel;
import com.newrelic.agent.android.logging.LogReporting;

import java.util.HashMap;
import java.util.Map;

public class LogInstrumentation {
    @ReplaceCallSite(isStatic = true , scope = "android.util.Log")
    public static int d(String tag, String message) {
        Log.d(tag, message);
        LogReporting.getLogger().logAttributes(asAttributes(LogLevel.DEBUG, tag, message));

        return LogReporting.isLevelEnabled(LogLevel.DEBUG) ? 1 : 0;
    }

    @ReplaceCallSite(isStatic = true, scope = "android.util.Log")
    public static int w(String tag, String message) {
        Log.w(tag, message);
        LogReporting.getLogger().logAttributes(asAttributes(LogLevel.WARN, tag, message));

        return LogReporting.isLevelEnabled(LogLevel.WARN) ? 1 : 0;
    }

    @ReplaceCallSite(isStatic = true, scope = "android.util.Log")
    public static int i(String tag, String message) {
        Log.i(tag, message);
        LogReporting.getLogger().logAttributes(asAttributes(LogLevel.INFO, tag, message));
        return LogReporting.isLevelEnabled(LogLevel.INFO) ? 1 : 0;
    }

    @ReplaceCallSite(isStatic = true, scope = "android.util.Log")
    public static int v(String tag, String message) {
        Log.v(tag, message);
        LogReporting.getLogger().logAttributes(asAttributes(LogLevel.VERBOSE, tag, message));

        return LogReporting.isLevelEnabled(LogLevel.VERBOSE) ? 1 : 0;
    }

    @ReplaceCallSite(isStatic = true, scope = "android.util.Log")
    public static int e(String tag, String message) {
        Log.e(tag, message);
        LogReporting.getLogger().logAttributes(asAttributes(LogLevel.ERROR, tag, message));

        return LogReporting.isLevelEnabled(LogLevel.ERROR) ? 1 : 0;
    }

    @ReplaceCallSite(isStatic = true, scope = "android.util.Log")
    public static int e(String tag, String message, Throwable throwable) {
        Log.e(tag, message, throwable);
        LogReporting.getLogger().logAll(throwable, asAttributes(LogLevel.ERROR, tag, message));

        return LogReporting.isLevelEnabled(LogLevel.ERROR) ? 1 : 0;
    }

    @ReplaceCallSite(isStatic = true, scope = "android.util.Log")
    public static int d(String tag, String message, Throwable throwable) {
        Log.d(tag, message, throwable);
        LogReporting.getLogger().logAll(throwable, asAttributes(LogLevel.DEBUG, tag, message));

        return LogReporting.isLevelEnabled(LogLevel.DEBUG) ? 1 : 0;
    }

    @ReplaceCallSite(isStatic = true, scope = "android.util.Log")
    public static int w(String tag, String message, Throwable throwable) {
        Log.w(tag, message, throwable);
        LogReporting.getLogger().logAll(throwable, asAttributes(LogLevel.WARN, tag, message));

        return LogReporting.isLevelEnabled(LogLevel.WARN) ? 1 : 0;
    }

    @ReplaceCallSite(isStatic = true, scope = "android.util.Log")
    public static int i(String tag, String message, Throwable throwable) {
        Log.i(tag, message, throwable);
        LogReporting.getLogger().logAll(throwable, asAttributes(LogLevel.INFO, tag, message));

        return LogReporting.isLevelEnabled(LogLevel.DEBUG) ? 1 : 0;
    }

    @ReplaceCallSite(isStatic = true, scope = "android.util.Log")
    public static int v(String tag, String message, Throwable throwable) {
        Log.v(tag, message, throwable);
        LogReporting.getLogger().logAll(throwable, asAttributes(LogLevel.VERBOSE, tag, message));

        return LogReporting.isLevelEnabled(LogLevel.VERBOSE) ? 1 : 0;
    }

    private static Map<String, Object> asAttributes(LogLevel logLevel, String tag, String message) {
        HashMap<String, Object> attrs = new HashMap<>();
        attrs.put("tag", tag);
        attrs.put("message", message);
        attrs.put("level", logLevel.name().toUpperCase());
        return attrs;
    }

}