/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.compile;

import java.util.Map;

public class Log {
    final static String TAG = "newrelic";

    public enum Level {
        TRACE(5),
        DEBUG(4),
        INFO(3),
        WARN(2),
        ERROR(1),
        OFF(0);
        final int value;

        Level(final int newValue) {
            value = newValue;
        }
    }

    final Level logLevel;
    final String name;

    public Log(Map<String, String> agentOptions) {
        String logLevelOpt = agentOptions.getOrDefault("loglevel", Level.WARN.name());
        logLevel = Level.valueOf(logLevelOpt);
        name = TAG;
    }

    protected void log(String level, String message) {
        // no-op
    }

    protected void log(Level level, String message) {
        if (isLevelEnabled(level)) {
            synchronized (this) {
                log(level.name(), message);
            }
        }
    }

    boolean isLevelEnabled(Level level) {
        return logLevel.value >= level.value;
    }

}
