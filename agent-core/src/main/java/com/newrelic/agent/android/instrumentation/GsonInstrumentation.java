/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.instrumentation;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.newrelic.agent.android.tracing.TraceMachine;

import java.io.Reader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;

@SuppressWarnings("unused")
public class GsonInstrumentation {
    private static final ArrayList<String> categoryParams = new ArrayList<String>(Arrays.asList("category", MetricCategory.class.getName(), "JSON"));

    public GsonInstrumentation() {}

    @ReplaceCallSite(scope = "com.google.gson.Gson")
    public static String toJson(Gson gson, Object src) {
        TraceMachine.enterMethod("Gson#toJson", categoryParams);
        final String string = gson.toJson(src);
        TraceMachine.exitMethod();

        return string;
    }

    @ReplaceCallSite(scope = "com.google.gson.Gson")
    public static String toJson(Gson gson, Object src, Type typeOfSrc) {
        TraceMachine.enterMethod("Gson#toJson", categoryParams);
        final String string = gson.toJson(src, typeOfSrc);
        TraceMachine.exitMethod();

        return string;
    }

    @ReplaceCallSite(scope = "com.google.gson.Gson")
    public static void toJson(Gson gson, Object src, Appendable writer) throws JsonIOException {
        TraceMachine.enterMethod("Gson#toJson", categoryParams);
        gson.toJson(src, writer);
        TraceMachine.exitMethod();
    }

    @ReplaceCallSite(scope = "com.google.gson.Gson")
    public static void toJson(Gson gson, Object src, Type typeOfSrc, Appendable writer) throws JsonIOException {
        TraceMachine.enterMethod("Gson#toJson", categoryParams);
        gson.toJson(src, typeOfSrc, writer);
        TraceMachine.exitMethod();
    }

    @ReplaceCallSite(scope = "com.google.gson.Gson")
    public static void toJson(Gson gson, Object src, Type typeOfSrc, JsonWriter writer) throws JsonIOException {
        TraceMachine.enterMethod("Gson#toJson", categoryParams);
        gson.toJson(src, typeOfSrc, writer);
        TraceMachine.exitMethod();
    }

    @ReplaceCallSite(scope = "com.google.gson.Gson")
    public static String toJson(Gson gson, JsonElement jsonElement) {
        TraceMachine.enterMethod("Gson#toJson", categoryParams);
        final String string = gson.toJson(jsonElement);
        TraceMachine.exitMethod();

        return string;
    }

    @ReplaceCallSite(scope = "com.google.gson.Gson")
    public static void toJson(Gson gson, JsonElement jsonElement, Appendable writer) throws JsonIOException {
        TraceMachine.enterMethod("Gson#toJson", categoryParams);
        gson.toJson(jsonElement, writer);
        TraceMachine.exitMethod();
    }

    @ReplaceCallSite(scope = "com.google.gson.Gson")
    public static void toJson(Gson gson, JsonElement jsonElement, JsonWriter writer) throws JsonIOException {
        TraceMachine.enterMethod("Gson#toJson", categoryParams);
        gson.toJson(jsonElement, writer);
        TraceMachine.exitMethod();
    }

    @ReplaceCallSite(scope = "com.google.gson.Gson")
    public static <T> T fromJson(Gson gson, String json, Class<T> classOfT) throws JsonSyntaxException {
        TraceMachine.enterMethod("Gson#fromJson", categoryParams);
        final T object = gson.fromJson(json, classOfT);
        TraceMachine.exitMethod();

        return object;
    }

    @ReplaceCallSite(scope = "com.google.gson.Gson")
    public static <T> T fromJson(Gson gson, String json, Type typeOfT) throws JsonSyntaxException {
        TraceMachine.enterMethod("Gson#fromJson", categoryParams);
        final T object = gson.fromJson(json, typeOfT);
        TraceMachine.exitMethod();

        return object;
    }

    @ReplaceCallSite(scope = "com.google.gson.Gson")
    public static <T> T fromJson(Gson gson, Reader json, Class<T> classOfT) throws JsonSyntaxException, JsonIOException {
        TraceMachine.enterMethod("Gson#fromJson", categoryParams);
        final T object = gson.fromJson(json, classOfT);
        TraceMachine.exitMethod();

        return object;
    }

    @ReplaceCallSite(scope = "com.google.gson.Gson")
    public static <T> T fromJson(Gson gson, Reader json, Type typeOfT) throws JsonIOException, JsonSyntaxException {
        TraceMachine.enterMethod("Gson#fromJson", categoryParams);
        final T object = gson.fromJson(json, typeOfT);
        TraceMachine.exitMethod();

        return object;
    }

    @ReplaceCallSite(scope = "com.google.gson.Gson")
    public static <T> T fromJson(Gson gson, JsonReader reader, Type typeOfT) throws JsonIOException, JsonSyntaxException {
        TraceMachine.enterMethod("Gson#fromJson", categoryParams);
        final T object = gson.fromJson(reader, typeOfT);
        TraceMachine.exitMethod();

        return object;
    }

    @ReplaceCallSite(scope = "com.google.gson.Gson")
    public static <T> T fromJson(Gson gson, JsonElement json, Class<T> classOfT) throws JsonSyntaxException {
        TraceMachine.enterMethod("Gson#fromJson", categoryParams);
        final T object = gson.fromJson(json, classOfT);
        TraceMachine.exitMethod();

        return object;
    }

    @ReplaceCallSite(scope = "com.google.gson.Gson")
    public static <T> T fromJson(Gson gson, JsonElement json, Type typeOfT) throws JsonSyntaxException {
        TraceMachine.enterMethod("Gson#fromJson", categoryParams);
        final T object = gson.fromJson(json, typeOfT);
        TraceMachine.exitMethod();

        return object;
    }
}
