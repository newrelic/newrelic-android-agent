package com.newrelic.agent.android.instrumentation;

import android.util.Log;

public class LogInstrumentation {

    @ReplaceCallSite(isStatic = true)
    public static int d(String tag, String msg) {
        return Log.d(tag, msg);
    }

    @ReplaceCallSite(isStatic = true)
    public static int w(String tag, String msg) {
        return Log.w(tag, msg);
    }

    @ReplaceCallSite(isStatic = true)
    public static int i(String tag, String msg) {
        return Log.i(tag, msg);
    }


    @ReplaceCallSite(isStatic = true)
    public static int v(String tag, String msg) {
        return Log.v(tag, msg);
    }

    @ReplaceCallSite(isStatic = true)
    public static int e(String tag, String msg) {
        return Log.e(tag, msg);
    }

    @ReplaceCallSite(isStatic = true)
    public static int d(String tag, String msg, Throwable tr) {
        //create own map
        //nrRemoteLogger.logAttributes(map)
        return Log.d(tag, msg, tr);
    }

    @ReplaceCallSite(isStatic = true)
    public static int w(String tag, String msg, Throwable tr) {
        return Log.w(tag, msg, tr);
    }

    @ReplaceCallSite(isStatic = true)
    public static int i(String tag, String msg, Throwable tr) {
        return Log.i(tag, msg, tr);
    }


    @ReplaceCallSite(isStatic = true)
    public static int v(String tag, String msg, Throwable tr) {
        return Log.v(tag, msg, tr);
    }

    @ReplaceCallSite(isStatic = true)
    public static int e(String tag, String msg, Throwable tr) {
        return Log.e(tag, msg, tr);
    }
}