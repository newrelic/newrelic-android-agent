/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.instrumentation.io;

import java.util.EventObject;

public final class StreamCompleteEvent extends EventObject {
	private static final long serialVersionUID = 1L;
	
	private final long bytes;
	private final Exception exception;
	
	public StreamCompleteEvent(Object source, long bytes, Exception exception) {
		super(source);
		this.bytes = bytes;
		this.exception = exception;
	}
	
	public StreamCompleteEvent(Object source, long bytes) {
		this(source, bytes, null);
	}
	
	public long getBytes() {
		return bytes;
	}
	
	public Exception getException() {
		return exception;
	}
	
	public boolean isError() {
		return exception != null;
	}
}
