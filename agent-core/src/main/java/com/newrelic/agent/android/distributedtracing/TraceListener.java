/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.distributedtracing;

import java.util.Map;

/**
 * The Distributed Trace listener is called during the creation of new traces and spans.
 * If the listener throws an exception, it will be removed and HandledException
 * event is created.
 */
public interface TraceListener {

    /**
     * Notified when a new trace has been created. The listener must provide a
     * long (16 byte/32 char) entity suitable for trace IDs.
     *
     * @param requestContext k/v String pairs that identify this request, including
     *                       the trace ID
     */
    void onTraceCreated(Map<String, String> requestContext);

    /**
     * Notified when a new span has been created. The listener must provide a
     * long (8 byte/16 char) entity suitable for span IDs.
     *
     * @param requestContext k/v String pairs that identify this request, including
     *                       the span ID
     */
    void onSpanCreated(Map<String, String> requestContext);

}
