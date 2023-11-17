package com.newrelic.agent.android.instrumentation;

import android.util.Log;

import com.newrelic.agent.android.NewRelic;

import java.util.HashMap;
import java.util.Map;


public class LogInstrumentation {
    @ReplaceCallSite(isStatic = true)
    public static int d(String tag, String msg) {
        Map<String, Object> m =new HashMap<>();
        m.put("msg",msg);
        NewRelic.recordBreadcrumb("tag",m);
        return Log.d(tag,msg);
    }

    @ReplaceCallSite(isStatic = true)
    public static int w(String tag, String msg) {
        Map<String, Object> m =new HashMap<>();
        m.put("msg",msg);
        NewRelic.recordBreadcrumb("tag",m);
        return Log.w(tag,msg);
    }

    @ReplaceCallSite(isStatic = true)
    public static int i(String tag, String msg) {
        Map<String, Object> m =new HashMap<>();
        m.put("msg",msg);
        NewRelic.recordBreadcrumb("tag",m);
        return Log.i(tag,msg);
    }


    @ReplaceCallSite(isStatic = true)
    public static int v(String tag, String msg) {
        Map<String, Object> m =new HashMap<>();
        m.put("msg",msg);
        NewRelic.recordBreadcrumb("tag",m);
        return Log.v(tag,msg);
    }

    @ReplaceCallSite(isStatic = true)
    public static int e(String tag, String msg) {
        Map<String, Object> m =new HashMap<>();
        m.put("msg",msg);
        NewRelic.recordBreadcrumb("tag",m);
        return Log.e(tag,msg);
    }
}