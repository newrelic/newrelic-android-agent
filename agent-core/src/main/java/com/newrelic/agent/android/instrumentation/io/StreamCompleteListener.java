/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.instrumentation.io;

public interface StreamCompleteListener {
	void streamComplete(StreamCompleteEvent e);
	void streamError(StreamCompleteEvent e);
}
