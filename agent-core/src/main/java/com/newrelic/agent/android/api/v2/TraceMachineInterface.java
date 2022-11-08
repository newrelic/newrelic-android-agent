/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.api.v2;

public interface TraceMachineInterface {
    public long getCurrentThreadId();

    public String getCurrentThreadName();

    public boolean isUIThread();
}
