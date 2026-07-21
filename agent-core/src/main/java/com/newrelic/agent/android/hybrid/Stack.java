/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.hybrid;

import com.newrelic.agent.android.hybrid.rninterface.IStack;
import com.newrelic.agent.android.hybrid.rninterface.IStackFrame;

import java.util.UUID;

public class Stack implements IStack {
    private StackFrame[] stackFrames;
    private final String id;

    Stack(StackFrame[] stackFrames) {
        this.stackFrames = stackFrames;
        this.id = UUID.randomUUID().toString();
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public boolean isThrowingThread() {
        return true;
    }

    @Override
    public IStackFrame[] getStackFrames() {
        return stackFrames;
    }

}
