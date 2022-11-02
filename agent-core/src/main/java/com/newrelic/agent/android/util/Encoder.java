/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.util;

public interface Encoder {
	String encode(byte[] bytes);
	String encodeNoWrap(byte[] bytes);
}
