/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.distributedtracing;

import com.newrelic.agent.android.instrumentation.TransactionState;

public interface TraceFacade {
    TraceContext startTrace(TransactionState transactionState);

    void setConfiguration(TraceConfiguration traceConfiguration);

    void setTraceListener(TraceListener listener);
}