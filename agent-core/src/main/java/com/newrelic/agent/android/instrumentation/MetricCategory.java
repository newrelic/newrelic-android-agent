/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.instrumentation;

import java.util.HashMap;
import java.util.Map;

public enum MetricCategory {
    NONE("None"),
    VIEW_LOADING("View Loading"),
    VIEW_LAYOUT("Layout"),
    DATABASE("Database"),
    IMAGE("Images"),
    JSON("JSON"),
    NETWORK("Network");

    private String categoryName;
    private final static Map<String, MetricCategory> methodMap = new HashMap<String, MetricCategory>() {{
       put("onCreate", VIEW_LOADING);
    }};

    MetricCategory(String categoryName) {
        this.categoryName = categoryName;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public static MetricCategory categoryForMethod(String fullMethodName) {
        if (fullMethodName == null)
            return MetricCategory.NONE;

        String methodName = null;
        int hashIndex = fullMethodName.indexOf("#");
        if (hashIndex >= 0) {
            methodName = fullMethodName.substring(hashIndex + 1);
        }
        MetricCategory category = methodMap.get(methodName);
        if (category == null)
            category = MetricCategory.NONE;
        return category;
    }
}
