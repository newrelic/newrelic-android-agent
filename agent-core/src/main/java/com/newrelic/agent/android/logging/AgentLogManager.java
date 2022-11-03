/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.logging;

public class AgentLogManager {
	private static DefaultAgentLog instance = new DefaultAgentLog();
	
	public static AgentLog getAgentLog() {
		return instance;
	}
	
	public static void setAgentLog(AgentLog instance) {
		AgentLogManager.instance.setImpl(instance);
	}
}
