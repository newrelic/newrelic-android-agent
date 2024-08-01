/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.logging;

public class DefaultAgentLog implements AgentLog {
	private AgentLog impl = new NullAgentLog();
	
	public void setImpl(final AgentLog impl) {
		synchronized (this) {
			this.impl = impl;
		}
	}

	@Override
	public void audit(String message) {
		synchronized (this) {
			impl.audit(message);
		}
	}
	
	@Override
	public void debug(String message) {
		synchronized (this) {
			impl.debug(message);
		}
	}

	@Override
	public void info(String message) {
		synchronized (this) {
			impl.info(message);
		}
	}

    @Override
	public void verbose(String message) {
        synchronized (this) {
			impl.verbose(message);
        }
    }

	@Override
    public void warn(String message) {
		synchronized (this) {
            impl.warn(message);
		}
	}

	@Override
	public void error(String message) {
		synchronized (this) {
			impl.error(message);
		}
	}

    @Override
    public void error(String message, Throwable cause) {
        synchronized (this) {
            impl.error(message, cause);
        }
    }

    @Override
    public int getLevel() {
        synchronized (this) {
            return impl.getLevel();
        }
    }

    @Override
    public void setLevel(int level) {
        synchronized (this) {
            impl.setLevel(level);
        }
    }

	public AgentLog getInstance() {
		return impl;
	}

}
