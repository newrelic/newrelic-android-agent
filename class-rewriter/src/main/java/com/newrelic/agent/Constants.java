/*
 * Copyright (c) 2021-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent;

public class Constants {

    public static final String TRACE_ANNOTATION_CLASSPATH = "Lcom/newrelic/agent/android/instrumentation/Trace;";
    public static final String SKIP_TRACE_ANNOTATION_CLASSPATH = "Lcom/newrelic/agent/android/instrumentation/SkipTrace;";
    public static final String REPLACE_CALLSITE_CLASSPATH = "Lcom/newrelic/agent/android/instrumentation/ReplaceCallSite;";
    public static final String TRACE_FIELD_INTERFACE_CLASSPATH = "com/newrelic/agent/android/api/v2/TraceFieldInterface";
    public static final String INSTRUMENTED_CLASSPATH = "Lcom/newrelic/agent/android/instrumentation/Instrumented;";

    public static final String NR_TRACE_FIELD = "_nr_trace";
    public static final String NR_TRACE_FIELD_TYPE = "Lcom/newrelic/agent/android/tracing/Trace;";

}
