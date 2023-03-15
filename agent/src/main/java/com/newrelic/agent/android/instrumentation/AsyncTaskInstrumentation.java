/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.instrumentation;

import android.annotation.TargetApi;
import android.os.AsyncTask;
import android.os.Build;

import com.newrelic.agent.android.api.v2.TraceFieldInterface;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.tracing.TraceMachine;
import com.newrelic.agent.android.tracing.TracingInactiveException;
import com.newrelic.agent.android.util.ExceptionHelper;

import java.util.concurrent.Executor;

@SuppressWarnings({"unused", "unchecked"})
public class AsyncTaskInstrumentation {

    private static final AgentLog log = AgentLogManager.getAgentLog();

    protected AsyncTaskInstrumentation() {
    }

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    @ReplaceCallSite
    public final static <Params, Progress, Result> AsyncTask execute(AsyncTask<Params, Progress, Result> task, Params... params) {
        try {
            // if (task instanceof TraceFieldInterface) {
            TraceFieldInterface tfi = (TraceFieldInterface) task;
            // Copy over the trace context into a field created by instrumentation.
            tfi._nr_setTrace(TraceMachine.getCurrentTrace());
            // }
        } catch (ClassCastException e) {
            ExceptionHelper.recordSupportabilityMetric(e, "TraceFieldInterface");
            log.error("Not a TraceFieldInterface: " + e.getMessage());
        } catch (TracingInactiveException e) {
        } catch (NoSuchFieldError e) {
        }
        return task.execute(params);
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    @ReplaceCallSite
    public final static <Params, Progress, Result> AsyncTask executeOnExecutor
            (AsyncTask<Params, Progress, Result> task, Executor exec, Params... params) {
        try {
            // if (task instanceof TraceFieldInterface) {
            TraceFieldInterface tfi = (TraceFieldInterface) task;
            // Copy over the trace context into a field created by instrumentation.
            tfi._nr_setTrace(TraceMachine.getCurrentTrace());
            // }
        } catch (ClassCastException e) {
            ExceptionHelper.recordSupportabilityMetric(e, "TraceFieldInterface");
            log.error("Not a TraceFieldInterface: " + e.getMessage());
        } catch (TracingInactiveException e) {
        } catch (NoSuchFieldError e) {
        }
        return task.executeOnExecutor(exec, params);
    }
}
