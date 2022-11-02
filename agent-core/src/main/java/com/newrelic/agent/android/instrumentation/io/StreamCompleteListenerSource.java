/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.instrumentation.io;

public interface StreamCompleteListenerSource {
	void addStreamCompleteListener(StreamCompleteListener streamCloseListener);
	void removeStreamCompleteListener(StreamCompleteListener streamCloseListener);
}
