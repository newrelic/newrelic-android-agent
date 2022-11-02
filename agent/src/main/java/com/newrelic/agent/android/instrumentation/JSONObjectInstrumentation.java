/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.instrumentation;

import com.newrelic.agent.android.tracing.TraceMachine;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;

@SuppressWarnings("unused")
public class JSONObjectInstrumentation {
    private static final ArrayList<String> categoryParams = new ArrayList<String>(Arrays.asList("category", MetricCategory.class.getName(), "JSON"));

    JSONObjectInstrumentation () {}

    @TraceConstructor
    public static JSONObject init(String json) throws JSONException {
        if (json == null) {
            throw new JSONException("Failed to initialize JSONObject: json string is null.");
        }

        final JSONObject jsonObject;

        try {
            TraceMachine.enterMethod(null, "JSONObject#<init>", categoryParams);
            jsonObject = new JSONObject(json);
            TraceMachine.exitMethod();
        } catch (JSONException e) {
            TraceMachine.exitMethod();
            throw e;
        }

        return jsonObject;
    }

    @ReplaceCallSite(scope = "org.json.JSONObject")
    public static String toString(JSONObject jsonObject) {
        TraceMachine.enterMethod(null, "JSONObject#toString", categoryParams);
        final String jsonString = jsonObject.toString();
        TraceMachine.exitMethod();

        return jsonString;
    }

    @ReplaceCallSite(scope = "org.json.JSONObject")
    public static String toString(JSONObject jsonObject, int indentFactor) throws JSONException {
        final String jsonString;
        try {
            TraceMachine.enterMethod(null, "JSONObject#toString", categoryParams);
            jsonString = jsonObject.toString(indentFactor);
            TraceMachine.exitMethod();
        } catch (JSONException e) {
            TraceMachine.exitMethod();
            throw e;
        }

        return jsonString;
    }
}
