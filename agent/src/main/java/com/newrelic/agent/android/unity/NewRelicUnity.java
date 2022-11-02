/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.unity;

import com.newrelic.agent.android.crash.UncaughtExceptionHandler;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;

@SuppressWarnings("serial")
public class NewRelicUnity {
    private static final AgentLog log = AgentLogManager.getAgentLog();
    private static final String ROOT_TRACE_NAME = "Unity";

    /**
     * Accepts a UnityException from the Unity plugin and re-throws it in the JVM.
     *
     * @param ex
     */
    static void handleUnityCrash(UnityException ex) {
        java.lang.Thread.UncaughtExceptionHandler currentExceptionHandler = Thread.getDefaultUncaughtExceptionHandler();
        if (currentExceptionHandler != null) {
            if (currentExceptionHandler instanceof UncaughtExceptionHandler) {
                currentExceptionHandler.uncaughtException(Thread.currentThread(), ex);
            }
        }
    }
}
