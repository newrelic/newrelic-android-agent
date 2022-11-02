/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.unity;

import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("serial")
public class UnityEvent {
    private String name;
    private Map<String, Object> attributes;

    public UnityEvent(String name) {
        this.name = name;
        attributes = new HashMap<String, Object>();
    }

    public String getName() {
        return name;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    public void addAttribute(String name, String value) {
        attributes.put(name, value);
    }

    public void addAttribute(String name, Double value) {
        attributes.put(name, value);
    }
}
