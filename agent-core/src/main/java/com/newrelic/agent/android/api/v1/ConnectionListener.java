/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.api.v1;

public interface ConnectionListener {
	void connected(ConnectionEvent e);
	void disconnected(ConnectionEvent e);
}
