/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.background;

public interface ApplicationStateListener {
	void applicationForegrounded(ApplicationStateEvent e);
	void applicationBackgrounded(ApplicationStateEvent e);
}
