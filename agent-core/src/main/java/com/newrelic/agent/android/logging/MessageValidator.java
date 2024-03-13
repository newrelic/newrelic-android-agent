/*
 * Copyright (c) 2023. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.logging;

import static com.newrelic.agent.android.logging.LogReporting.INVALID_MSG;
import static com.newrelic.agent.android.logging.LogReporting.LOG_LEVEL_ATTRIBUTE;
import static com.newrelic.agent.android.logging.LogReporting.LOG_MESSAGE_ATTRIBUTE;

import java.util.HashMap;
import java.util.Map;

public interface MessageValidator {

    String INVALID_KEYSET = "{}\\[\\]]";
    String[] ANONYMIZATION_TARGETS = {
            "http?//{.*}/{.*}",
            "{.*}\\@{.*}\\.{.*}"
    };

    /**
     * TODO The message attribute on a log record MUST be truncated to 32768 bytes (32Kib) on the agent side.
     *
     * @param message
     * @return
     */
    default String validate(String message) {
        return (null == message || message.isEmpty()) ? INVALID_MSG : message;
    }

    default Map<String, Object> validate(Map<String, Object> attributes) {
        if (null == attributes) {
            attributes = new HashMap<>();
        }

        if (!attributes.containsKey(LOG_MESSAGE_ATTRIBUTE)) {
            attributes.put(LOG_MESSAGE_ATTRIBUTE, INVALID_MSG);
        }

        if (!attributes.containsKey(LOG_LEVEL_ATTRIBUTE)) {
            attributes.put(LOG_LEVEL_ATTRIBUTE, LogLevel.INFO.name());
        }

        return attributes;
    }

    default Throwable validate(final Throwable throwable) {
        return null != throwable ? throwable : new IllegalArgumentException();   // TODO
    }

    default Map<String, Object> anonymize(Map<String, Object> attributes) {
        attributes = validate(attributes);
        return attributes;    // TODO
    }
}

