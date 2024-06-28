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
    // The message attribute on a log record MUST be truncated to 32768 bytes (32Kib) on the agent side.
    int MAX_MESSAGE_LEN = (32 * 1024);
    
    String INVALID_KEYSET = "{}\\[\\]]";
    String[] ANONYMIZATION_TARGETS = {
            "http?//{.*}/{.*}",
            "{.*}\\@{.*}\\.{.*}"
    };

    /**
     * Validate content and length of message data.
     *
     * @param message
     * @return
     */
    default String validate(String message) {
        if (null == message || message.isEmpty()) {
            message = INVALID_MSG;
        } else if (message.length() > MAX_MESSAGE_LEN) {
            message = message.substring(0, MAX_MESSAGE_LEN - 1);
        }

        return message;
    }

    /**
     * Some specific attributes have additional restrictions:
     *  accountId: This is a reserved attribute name. If it is included, it will be dropped during ingest.
     *  appId: Must be an integer. When using a non-integer data type, the data will be ingested but becomes unqueryable.
     *  entity.guid, entity.name, and entity.type: These attributes are used internally to identify entities.
     *      Any values submitted with these keys in the attributes section of a metric data point may cause undefined behavior
     *  such as missing entities in the UI or telemetry not associating with the expected entities.
     *  eventType: This is a reserved attribute name. If it is included, it will be dropped during ingest.
     *  timestamp: Must be a Unix epoch timestamp (either in seconds or in milliseconds) or an ISO8601-formatted timestamp.
     *
     * @param attributes
     * @return validated attribute map
     * @link reserved attributes: https://source.datanerd.us/agents/agent-specs/blob/main/Application-Logging.md#log-record-attributes
     */
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

