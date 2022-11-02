/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.logging;

public interface AgentLog {
    int AUDIT = 6;
    int DEBUG = 5;
    int VERBOSE = 4;
    int INFO = 3;
    int WARNING = 2;
    int ERROR = 1;

    void audit(String message);
	void debug(String message);
    void verbose(String message);
	void info(String message);
    void warning(String message);
	void error(String message);
	void error(String message, Throwable cause);
    int getLevel();
    void setLevel(int level);
}
