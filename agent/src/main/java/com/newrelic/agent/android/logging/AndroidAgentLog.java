/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.logging;

import android.util.Log;

public class AndroidAgentLog implements AgentLog {
	private static final String TAG = "newrelic";

    // Default to INFO
    private int level = INFO;

    @Override
    public void audit(String message) {
        if (level == AUDIT) {
            Log.d(TAG, message);
        }
    }

    public void debug(final String message) {
        if (level >= DEBUG) {
            Log.d(TAG, message);
        }
	}

    public void verbose(final String message) {
        if (level >= VERBOSE) {
            Log.v(TAG, message);
        }
    }

	public void info(final String message) {
        if (level >= INFO) {
            Log.i(TAG, message);
        }
	}

    public void warn(final String message) {
        if (level >= WARN) {
            Log.w(TAG, message);
        }
    }

    public void error(final String message) {
        if (level >= ERROR) {
            Log.e(TAG, message);
        }
	}
	
	public void error(final String message, Throwable cause) {
        if (level >= ERROR) {
            Log.e(TAG, message, cause);
        }
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        if (level <= AUDIT && level >= ERROR) {
            this.level = level;
        } else {
            throw new IllegalArgumentException("Log level is not between ERROR and AUDIT");
        }
    }
}
