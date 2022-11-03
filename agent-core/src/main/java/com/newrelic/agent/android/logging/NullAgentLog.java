/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.logging;

public class NullAgentLog implements AgentLog {
	@Override
	public void audit(String message) {

	}

	@Override
	public void debug(String message) {
	}

	@Override
	public void info(String message) {
	}

	@Override
	public void verbose(String message) {
	}

	@Override
	public void error(String message) {
	}
	
	@Override
	public void error(String message, Throwable cause) {
	}

	@Override
	public void warning(String message) {
	}

    @Override
    public int getLevel() {
        return DEBUG;
    }

    @Override
    public void setLevel(int level) {
    }
}
