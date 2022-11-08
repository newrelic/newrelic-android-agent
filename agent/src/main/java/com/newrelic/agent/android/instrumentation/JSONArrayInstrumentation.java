/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.instrumentation;

import com.newrelic.agent.android.tracing.TraceMachine;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.Arrays;

@SuppressWarnings("unused")
public class JSONArrayInstrumentation {
    private static final ArrayList<String> categoryParams = new ArrayList<String>(Arrays.asList("category", MetricCategory.class.getName(), "JSON"));

    JSONArrayInstrumentation () {}

    @TraceConstructor
    public static JSONArray init(String json) throws JSONException {
        if (json == null) {
            throw new JSONException("Failed to initialize JSONArray: json string is null.");
        }
        final JSONArray jsonArray;

        try {
            TraceMachine.enterMethod("JSONArray#<init>", categoryParams);
            jsonArray = new JSONArray(json);
            TraceMachine.exitMethod();
        } catch (JSONException e) {
            TraceMachine.exitMethod();
            throw e;
        }

        return jsonArray;
    }

    @ReplaceCallSite(scope = "org.json.JSONArray")
    public static String toString(JSONArray jsonArray) {
        TraceMachine.enterMethod("JSONArray#toString", categoryParams);
        final String jsonString = jsonArray.toString();
        TraceMachine.exitMethod();

        return jsonString;
    }

    @ReplaceCallSite(scope = "org.json.JSONArray")
    public static String toString(JSONArray jsonArray, int indentFactor) throws JSONException {
        final String jsonString;

        try {
            TraceMachine.enterMethod("JSONArray#toString", categoryParams);
            jsonString = jsonArray.toString(indentFactor);
            TraceMachine.exitMethod();
        } catch (JSONException e) {
            TraceMachine.exitMethod();
            throw e;
        }

        return jsonString;
    }
}
