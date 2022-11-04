/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.harvest.crash;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.newrelic.agent.android.harvest.type.HarvestableObject;

public class ExceptionInfo extends HarvestableObject {
    private String className;
    private String message;

    public ExceptionInfo() {
        super();
    }

    public ExceptionInfo(Throwable throwable) {
        super();

        this.className = throwable.getClass().getName();

        if (throwable.getMessage() != null) {
            this.message = throwable.getMessage();
        } else {
            this.message = "";
        }
    }

    public String getClassName() {
        return className;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public JsonObject asJsonObject() {
        JsonObject data = new JsonObject();

        data.add("name", new JsonPrimitive(className != null ? className : ""));
        data.add("cause", new JsonPrimitive(message != null ? message : ""));

        return data;
    }

    public static ExceptionInfo newFromJson(JsonObject jsonObject) {
        final ExceptionInfo info = new ExceptionInfo();

        info.className = jsonObject.has("name") ? jsonObject.get("name").getAsString() : "";

        info.message = jsonObject.has("cause") ? jsonObject.get("cause").getAsString() : "";

        return info;
    }
}
