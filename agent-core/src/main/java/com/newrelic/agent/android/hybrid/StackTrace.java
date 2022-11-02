/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.hybrid;

import com.newrelic.agent.android.hybrid.rninterface.IStack;
import com.newrelic.agent.android.hybrid.rninterface.IStackTrace;
import com.newrelic.agent.android.hybrid.rninterface.IStackTraceException;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class StackTrace extends Throwable implements IStackTrace {
    final private UUID stackId;
    final private boolean isFatal;
    final private IStackTraceException rnStackTraceException;
    final private String buildId;
    private IStack[] rnStacks = null;
    private Map<String, Object> attributes = new HashMap<>();

    public StackTrace(String errorName, String errorMessage, String errorStack, boolean isFatal, String buildId) {
        this(errorName, errorMessage, errorStack, isFatal, buildId, null);
    }

    public StackTrace(String errorName, String errorMessage, String errorStack, boolean isFatal, String buildId, Map<String, Object> attributes) {
        if (errorStack == null || errorStack.isEmpty()) {
            throw new IllegalArgumentException("Cannot create a RNStackTrace without a stack.");
        }

        this.stackId = UUID.randomUUID();
        this.isFatal = isFatal;
        this.buildId = buildId;

        if (attributes != null) {
            this.attributes = attributes;
        }

        String[] stringStackTrace = errorStack.split("\n");
        String exName = errorName;
        String exCause = errorMessage;
        if (errorName == null || errorName.isEmpty()) {
            exName = errorStack.split("\n")[0];
        }
        if (errorMessage == null || errorMessage.isEmpty()) {
            exCause = errorStack.split("\n")[0];
        }
        rnStackTraceException = new StackTraceException(exName, exCause);

        // skip the first line (errorName: errorMessage)
        int stackLength = stringStackTrace.length - 1;
        if (stackLength > 0) {
            StackFrame[] rnStackFrames = new StackFrame[stackLength];
            for (int i = 1; i < stackLength + 1; ++i) {
                StackFrame frame = new StackFrame(stringStackTrace[i]);
                rnStackFrames[i - 1] = frame;
            }
            rnStacks = new Stack[1];
            rnStacks[0] = new Stack(rnStackFrames);
        }
    }

    @Override
    public String getStackId() {
        return stackId.toString();
    }

    @Override
    public String getStackType() {
        return "ReactNative";
    }

    @Override
    public boolean isFatal() {
        return isFatal;
    }

    @Override
    public IStack[] getStacks() {
        return rnStacks;
    }

    @Override
    public IStackTraceException getStackTraceException() {
        return rnStackTraceException;
    }

    @Override
    public String getBuildId() {
        return buildId;
    }

    @Override
    public Map<String, Object> getCustomAttributes() {
        return attributes;
    }

}
