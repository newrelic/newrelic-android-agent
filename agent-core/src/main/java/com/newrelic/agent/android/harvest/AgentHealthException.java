/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.harvest;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.newrelic.agent.android.harvest.type.HarvestableArray;
import com.newrelic.agent.android.util.SafeJsonPrimitive;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public class AgentHealthException extends HarvestableArray {
    private String exceptionClass;
    private String message;
    private String threadName;
    private StackTraceElement[] stackTrace;
    final private AtomicLong count = new AtomicLong(1);
    private Map<String, String> extras;

    public AgentHealthException(Exception e) {
        this(e, Thread.currentThread().getName());
    }

    public AgentHealthException(Exception e, String threadName) {
        this(e.getClass().getName(), e.getMessage(), threadName, e.getStackTrace());
    }

    public AgentHealthException(String exceptionClass, String message, String threadName, StackTraceElement[] stackTrace) {
        this(exceptionClass, message, threadName, stackTrace, null);
    }

    public AgentHealthException(String exceptionClass, String message, String threadName, StackTraceElement[] stackTrace, Map<String, String> extras) {
        super();

        this.exceptionClass = exceptionClass;
        this.message = message;
        this.threadName = threadName;
        this.stackTrace = stackTrace;
        this.extras = extras;
    }

    public void increment() {
        count.getAndIncrement();
    }

    public void increment(long i) {
        count.getAndAdd(i);
    }

    public String getExceptionClass() {
        return exceptionClass;
    }

    public String getMessage() {
        return message;
    }

    public String getThreadName() {
        return threadName;
    }

    public StackTraceElement[] getStackTrace() {
        return stackTrace;
    }

    public long getCount() {
        return count.get();
    }

    public Map<String, String> getExtras() {
        return extras;
    }

    public String getSourceClass() {
        return stackTrace[0].getClassName();
    }

    public String getSourceMethod() {
        return stackTrace[0].getMethodName();
    }

    @Override
    public JsonArray asJsonArray() {
        final JsonArray data = new JsonArray();

        data.add(SafeJsonPrimitive.factory(exceptionClass));
        data.add(SafeJsonPrimitive.factory(message == null ? "" : message));
        data.add(SafeJsonPrimitive.factory(threadName));
        data.add(stackTraceToJson());
        data.add(SafeJsonPrimitive.factory(count.get()));
        data.add(extrasToJson());

        return data;
    }

    private JsonArray stackTraceToJson() {
        final JsonArray stack = new JsonArray();

        for (StackTraceElement element : stackTrace) {
            stack.add(SafeJsonPrimitive.factory(element.toString()));
        }

        return stack;
    }

    private JsonObject extrasToJson() {
        final JsonObject data = new JsonObject();

        if (extras != null) {
            for (Map.Entry<String, String> entry : extras.entrySet()) {
                data.add(entry.getKey(), SafeJsonPrimitive.factory(entry.getValue()));
            }
        }

        return data;
    }
}
