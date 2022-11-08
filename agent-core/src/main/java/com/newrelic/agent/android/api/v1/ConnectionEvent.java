/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.api.v1;

import java.util.EventObject;

import com.newrelic.agent.android.api.common.ConnectionState;

public final class ConnectionEvent extends EventObject {
	private static final long serialVersionUID = 1L;
	
	private final ConnectionState connectionState;
	
	public ConnectionEvent(final Object source) {
		this(source, null);
	}
	
	public ConnectionEvent(final Object source, final ConnectionState connectionState) {
		super(source);
		this.connectionState = connectionState;
	}

	public ConnectionState getConnectionState() {
		return connectionState;
	}
}
