/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.hybrid.rninterface;

import java.util.Map;

public interface IStackTrace {
    String getStackId();

    String getStackType();

    boolean isFatal();

    IStack[] getStacks();

    IStackTraceException getStackTraceException();

    String getBuildId();

    Map<String, Object> getCustomAttributes();

}
