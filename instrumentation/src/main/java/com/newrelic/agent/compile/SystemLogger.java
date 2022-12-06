/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.compile;

import org.slf4j.event.Level;

import java.util.Map;

public final class SystemLogger extends Logger {

    public SystemLogger(Map<String, String> agentOptions) {
        super(agentOptions);
    }

    @Override
    protected void log(String level, String message) {
        synchronized (this) {
            System.out.println("[" + level + "] [" + Log.TAG + "] " + message);
        }
    }

    @Override
    public void warn(String message, Throwable cause) {
        if (isLevelEnabled(Level.WARN)) {
            synchronized (this) {
                System.err.println("[WARN] [" + Log.TAG + "] " + message);
                cause.printStackTrace(System.err);
            }
        }
    }

    @Override
    public void error(String message, Throwable cause) {
        if (isLevelEnabled(Level.ERROR)) {
            synchronized (this) {
                System.err.println("[ERROR] [" + Log.TAG + "] " + message);
                cause.printStackTrace(System.err);
            }
        }
    }

}