/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.api.v1;

public interface Defaults {
	public static final long MAX_TRANSACTION_COUNT = 1000;
	public static final long MAX_TRANSACTION_AGE_IN_SECONDS = 600;
	public static final long HARVEST_INTERVAL_IN_SECONDS = 60;
	public static final long MIN_HARVEST_DELTA_IN_SECONDS = 50;
	public static final long MIN_HTTP_ERROR_STATUS_CODE = 400;
	public static final boolean COLLECT_NETWORK_ERRORS = true;
	public static final int ERROR_LIMIT = 10;
	public static final int RESPONSE_BODY_LIMIT = 1024;
	public static final int STACK_TRACE_LIMIT = 50;
    public static final float ACTIVITY_TRACE_MIN_UTILIZATION = 0.3f;

}
