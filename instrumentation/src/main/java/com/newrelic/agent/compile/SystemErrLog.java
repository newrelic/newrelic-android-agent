/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.compile;

import java.util.Map;

public final class SystemErrLog extends Log {

    public SystemErrLog(Map<String, String> agentOptions) {
        super(agentOptions);
    }

    protected void log(String level, String message) {
        synchronized (this) {
            System.out.println("[newrelic." + level.toLowerCase() + "] " + message);
        }
    }

    @Override
    public void warning(String message, Throwable cause) {
        if (logLevel >= LogLevel.WARN.getValue()) {
            synchronized (this) {
                log("warn", message);
                cause.printStackTrace(System.err);
            }
        }
    }

    @Override
    public void error(String message, Throwable cause) {
        if (logLevel >= LogLevel.WARN.getValue()) {
            synchronized (this) {
                log("error", message);
                cause.printStackTrace(System.err);
            }
        }
    }

}