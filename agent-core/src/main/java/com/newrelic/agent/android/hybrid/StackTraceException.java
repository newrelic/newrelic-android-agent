/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.hybrid;

import com.newrelic.agent.android.hybrid.rninterface.IStackTraceException;

public class StackTraceException implements IStackTraceException {
    final private String name;
    final private String cause;

    StackTraceException(String name, String cause) {
        this.name = name;
        this.cause = cause;
    }

    @Override
    public String getExceptionName() {
        return name;
    }

    @Override
    public String getCause() {
        return cause;
    }
}
