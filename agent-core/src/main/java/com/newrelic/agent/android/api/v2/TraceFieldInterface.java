/*
 * Copyright (c) 2021-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.api.v2;

import com.newrelic.agent.android.tracing.Trace;

public interface TraceFieldInterface {
    void _nr_setTrace(Trace trace);
}
