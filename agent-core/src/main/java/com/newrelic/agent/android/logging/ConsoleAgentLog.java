/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.logging;

public class ConsoleAgentLog implements AgentLog {
    private int level = INFO;

    @Override
    public void audit(String message) {
        if (level == AUDIT)
            print("AUDIT", message);
    }

    @Override
    public void debug(String message) {
        if (level >= DEBUG)
            print("DEBUG", message);
    }

    @Override
    public void verbose(String message) {
        if (level >= VERBOSE)
            print("VERBOSE", message);
    }

    @Override
    public void info(String message) {
        if (level >= INFO)
            print("INFO", message);
    }

    @Override
    public void warning(String message) {
        if (level >= WARNING)
            print("WARN", message);
    }

    @Override
    public void error(String message, Throwable cause) {
        if (level >= ERROR)
            print("ERROR", message + " " + cause.getMessage());
    }

    @Override
    public void error(String message) {
        if (level >= ERROR)
            print("ERROR", message);
    }


    @Override
    public int getLevel() {
        return level;
    }

    @Override
    public void setLevel(int level) {
        this.level = level;
    }

    private static void print(String tag, String message) {
        System.out.println("[" + tag + "] " + message);
    }
}
