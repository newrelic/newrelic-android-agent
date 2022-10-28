/*
 * Copyright (c) 2021-2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

public class Constants {

    public static final String NR_PACKAGE_NAME = "com/newrelic/agent/android";
    public static final String NR_AGENT_ARGS_KEY = "newrelic.agent.args";
    public static final String NR_DISABLE_INSTRUMENTATION_KEY = "newrelic.instrumentation.disabled";
    public static final String NR_INSTRUMENTATION_DISABLED_FLAG = "SET_INSTRUMENTATION_DISABLED_FLAG";
    public static final String NR_PRINT_INFO_FLAG = "PRINT_TO_INFO_LOG";

    public final static Pattern NR_CLASS_PATTERN = Pattern.compile("^(com/newrelic/agent/.*).class");

    public static final String TRACE_ANNOTATION_CLASS_NAME = "Lcom/newrelic/agent/android/instrumentation/Trace;";
    public static final String SKIP_TRACE_ANNOTATION_CLASS_NAME = "Lcom/newrelic/agent/android/instrumentation/SkipTrace;";
    public static final String REPLACE_CALLSITE_CLASS_NAME = "Lcom/newrelic/agent/android/instrumentation/ReplaceCallSite;";
    public static final String TRACE_FIELD_INTERFACE_CLASS_NAME = "com/newrelic/agent/android/api/v2/TraceFieldInterface";
    public static final String INSTRUMENTED_CLASS_NAME = "Lcom/newrelic/agent/android/instrumentation/Instrumented;";

    public static final String TRACE_CLASS_NAME = "Lcom/newrelic/agent/android/tracing/Trace;";
    public static final String TRACE_SIGNATURE = "(Lcom/newrelic/agent/android/tracing/Trace;)V";
    public static final String TRACE_SETTRACE_METHOD_NAME = "_nr_setTrace";
    public static final String TRACE_FIELD = "_nr_trace";

    public static final String INTF_SIGNATURE = "(Lcom/newrelic/agent/compile/visitor/Intf;)V";

    public static final String TRACEMACHINE_CLASS_NAME = "com/newrelic/agent/android/tracing/TraceMachine";
    public static final String TRACEMACHINE_ENTER_METHOD_NAME = "enterMethod";
    public static final String TRACEMACHINE_ENTER_METHOD_SIGNATURE = "(Lcom/newrelic/agent/android/tracing/Trace;Ljava/lang/String;Ljava/util/ArrayList;)V";

    public static final String NEWRELIC_CLASS_NAME = "com/newrelic/agent/android/NewRelic";
    public static final String ASM_CLASS_NAME = "com/newrelic/agent/android/background/ApplicationStateMonitor";
    public static final String CRASH_CLASS_NAME = "com/newrelic/agent/android/crash/Crash";
    public static final String AGENT_CLASS_NAME = "com/newrelic/agent/android/Agent";

    /**
     * The New Relic android agent jar names:
     */
    private static final Set<String> AGENT_AR_NAMES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList("android-agent-\\d\\.\\d\\.\\d\\.jar", "android-agent-release.aar")));

    /**
     * This includes those package names we *expressly* should not instrument
     */
    public static final HashSet<String> ANDROID_EXCLUDED_PACKAGES = new HashSet<String>() {{
        // agent exclusions
        add("com/newrelic/agent/android");
        add("com/newrelic/mobile");
        add("com/newrelic/com");

        // 3rd party exclusions
        add("com/google/firebase/perf/network");    // Firebase network instrumentation
        add("com/here/sdk/hacwrapper");             // HERE sdk
    }};

    /**
     * This includes those package names we are very interested in
     */
    public static final HashSet<String> ANDROID_INCLUDED_PACKAGES = new HashSet<String>() {{
        add("androidx/appcompat/app/AppCompatActivity");
        add("androidx/core/app/ActivityCompat");
        add("androidx/fragment/app/");
        add("androidx/fragment/app/Fragment");
        add("androidx/fragment/app/FragmentActivity");
        add("androidx/leanback/app/Fragment");
        add("androidx/legacy/app/ActivityCompat");
        add("androidx/legacy/app/FragmentCompat");
        add("androidx/preference/Fragment");
        add("androidx/sqlite/");
        add("com/google/gson/");
        add("org/json/");
    }};

    /**
     *  A regex to sniff out Android package names
     */
    public static final String ANDROID_PACKAGE_RE = "^android[x]{0,1}/.*";

    /**
     *  A regex to sniff out Kotlin package names
     */
    public static final String ANDROID_KOTLIN_PACKAGE_RE = "^kotlin[x]{0,1}/.*";

}
