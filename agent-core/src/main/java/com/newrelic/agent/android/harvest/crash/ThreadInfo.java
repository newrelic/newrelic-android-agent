/*
 * Copyright 2021-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.harvest.crash;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.newrelic.agent.android.harvest.type.HarvestableObject;
import com.newrelic.agent.android.util.SafeJsonPrimitive;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ThreadInfo extends HarvestableObject {
    protected boolean crashed;
    protected long threadId;
    protected String threadName;
    protected int threadPriority;
    protected String state;

    protected StackTraceElement[] stackTrace;

    public ThreadInfo() {
        super();
    }

    public ThreadInfo(Throwable throwable) {
        // We assume here the crashed thread is the current thread
        this(Thread.currentThread(), throwable.getStackTrace());
        this.crashed = true;
    }

    public ThreadInfo(Thread thread, StackTraceElement[] stackTrace) {
        super();

        this.crashed = false;
        this.threadId = thread.getId();
        this.threadName = thread.getName();
        this.threadPriority = thread.getPriority();
        this.state = thread.getState().toString();

        this.stackTrace = stackTrace;
    }

    public long getThreadId() {
        return threadId;
    }

    public List<ThreadInfo> allThreads() {
        final List<ThreadInfo> threads = new ArrayList<ThreadInfo>();
        final long crashedThreadId = getThreadId();

        threads.add(this);
        for (Map.Entry<Thread, StackTraceElement[]> threadEntry : Thread.getAllStackTraces().entrySet()) {
            final Thread thread = threadEntry.getKey();
            final StackTraceElement[] threadStackTrace = threadEntry.getValue();
            if (thread.getId() != crashedThreadId) {
                threads.add(new ThreadInfo(thread, threadStackTrace));
            }
        }

        return threads;
    }

    public static List<ThreadInfo> extractThreads(Throwable throwable) {
        return new ThreadInfo(throwable).allThreads();
    }

    @Override
    public JsonObject asJsonObject() {
        final JsonObject data = new JsonObject();

        data.add("crashed", SafeJsonPrimitive.factory(crashed));
        data.add("state", SafeJsonPrimitive.factory(state));
        data.add("threadNumber", SafeJsonPrimitive.factory(threadId));
        data.add("threadId", SafeJsonPrimitive.factory(threadName));
        data.add("priority", SafeJsonPrimitive.factory(threadPriority));
        data.add("stack", getStackAsJson());

        return data;
    }

    public static ThreadInfo newFromJson(JsonObject jsonObject) {
        final ThreadInfo info = new ThreadInfo();

        info.crashed = jsonObject.get("crashed").getAsBoolean();
        info.state = jsonObject.get("state").getAsString();
        info.threadId = jsonObject.get("threadNumber").getAsLong();
        info.threadName = jsonObject.get("threadId").getAsString();
        info.threadPriority = jsonObject.get("priority").getAsInt();

        info.stackTrace = info.stackTraceFromJson(jsonObject.get("stack").getAsJsonArray());

        return info;
    }

    public StackTraceElement[] stackTraceFromJson(JsonArray jsonArray) {
        final StackTraceElement[] stack = new StackTraceElement[jsonArray.size()];
        int i = 0;

        for (JsonElement jsonElement : jsonArray) {
            String fileName = "unknown";

            if (jsonElement.getAsJsonObject().get("fileName") !=null) {
                fileName = jsonElement.getAsJsonObject().get("fileName").getAsString();
            }

            final String className = jsonElement.getAsJsonObject().get("className").getAsString();
            final String methodName = jsonElement.getAsJsonObject().get("methodName").getAsString();
            final int lineNumber = jsonElement.getAsJsonObject().get("lineNumber").getAsInt();

            final StackTraceElement stackTraceElement = new StackTraceElement(className, methodName, fileName, lineNumber);

            stack[i++] = stackTraceElement;
        }

        return stack;
    }

    public List<ThreadInfo> newListFromJson(JsonArray jsonArray) {
        final List<ThreadInfo> list = new ArrayList<ThreadInfo>();

        for (JsonElement jsonElement: jsonArray) {
            list.add(newFromJson(jsonElement.getAsJsonObject()));
        }

        return list;
    }

    private JsonArray getStackAsJson() {
        final JsonArray data = new JsonArray();

        for (StackTraceElement element : stackTrace) {
            final JsonObject elementJson = new JsonObject();

            if (element.getFileName() != null) {
                elementJson.add("fileName", SafeJsonPrimitive.factory(element.getFileName()));
            }

            elementJson.add("className", SafeJsonPrimitive.factory(element.getClassName()));
            elementJson.add("methodName", SafeJsonPrimitive.factory(element.getMethodName()));
            elementJson.add("lineNumber", SafeJsonPrimitive.factory(element.getLineNumber()));

            data.add(elementJson);
        }

        return data;
    }
}
