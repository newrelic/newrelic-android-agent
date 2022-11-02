/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.hybrid.rninterface;

public interface IStack {
    String getId();

    boolean isThrowingThread();

    IStackFrame[] getStackFrames();
}