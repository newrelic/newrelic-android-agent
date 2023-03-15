/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android;

public class AgentInitializationException extends Exception {
	private static final long serialVersionUID = 2725421917845262499L;

	public AgentInitializationException(final String message) {
		super(message);
	}
}
