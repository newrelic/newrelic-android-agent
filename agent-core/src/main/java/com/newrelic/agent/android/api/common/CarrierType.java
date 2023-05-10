/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.api.common;

public interface CarrierType {
	String BLUETOOTH = "bluetooth";
	String ETHERNET = "ethernet";
	String NONE = "none";
	String WIFI = "wifi";
	String CELLULAR = "cellular";
	String UNKNOWN = "unknown";
}
