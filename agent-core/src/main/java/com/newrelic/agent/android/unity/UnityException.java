/*
 * Copyright (c) 2023-2024 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.unity;

public class UnityException extends RuntimeException {
    private String sourceExceptionType = null;

    public UnityException() {
        super();
    }

    public UnityException(String sourceExceptionType, String detailMessage) {
        super(detailMessage);
        this.sourceExceptionType = sourceExceptionType;
    }

    public UnityException(String detailMessage) {
        super(detailMessage);
    }

    public UnityException(String detailMessage, StackTraceElement[] stackTraceElements) {
        super(detailMessage);
        setStackTrace(stackTraceElements);
    }

    public void appendStackFrame(StackTraceElement stackFrame) {
        StackTraceElement[] currentStack = getStackTrace();
        StackTraceElement[] newStack = new StackTraceElement[currentStack.length + 1];
        for (int i = 0; i < currentStack.length; i++) {
            newStack[i] = currentStack[i];
        }
        newStack[currentStack.length] = stackFrame;
        setStackTrace(newStack);
    }

    public void appendStackFrame(String className, String methodName, String fileName, int lineNumber) {
        StackTraceElement stackFrame = new StackTraceElement(className, methodName, fileName, lineNumber);
        StackTraceElement[] currentStack = getStackTrace();
        StackTraceElement[] newStack = new StackTraceElement[currentStack.length + 1];
        for (int i = 0; i < currentStack.length; i++) {
            newStack[i] = currentStack[i];
        }
        newStack[currentStack.length] = stackFrame;
        setStackTrace(newStack);
    }

    public void setSourceExceptionType(final String sourceExceptionType) {
        this.sourceExceptionType = sourceExceptionType;
    }

    @Override
    public String toString() {
        return (sourceExceptionType == null || sourceExceptionType.isEmpty()) ? this.getClass().getName() : sourceExceptionType;
    }
}
