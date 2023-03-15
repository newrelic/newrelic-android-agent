/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.api.common;

import java.util.concurrent.TimeUnit;

import com.newrelic.agent.android.api.v1.Defaults;


public final class ConnectionState {
	private final Object dataToken;
	private final String crossProcessId;
	private final long serverTimestamp;
	private final long harvestInterval;
	private final TimeUnit harvestIntervalTimeUnit;
	private final long maxTransactionAge;
	private final TimeUnit maxTransactionAgeTimeUnit;
	private final long maxTransactionCount;
	private final int stackTraceLimit;
	private final int responseBodyLimit;
	private final boolean collectingNetworkErrors;
	private final int errorLimit;
	
	public static final ConnectionState NULL = new ConnectionState();
	
	private ConnectionState() {
		dataToken = null;
		crossProcessId = null;
		serverTimestamp = 0;
		harvestInterval = Defaults.HARVEST_INTERVAL_IN_SECONDS;
		harvestIntervalTimeUnit = TimeUnit.SECONDS;
		maxTransactionAge = Defaults.MAX_TRANSACTION_AGE_IN_SECONDS;
		maxTransactionAgeTimeUnit = TimeUnit.SECONDS;
		maxTransactionCount = Defaults.MAX_TRANSACTION_COUNT;
		stackTraceLimit = Defaults.STACK_TRACE_LIMIT;
		responseBodyLimit = Defaults.RESPONSE_BODY_LIMIT;
		collectingNetworkErrors = Defaults.COLLECT_NETWORK_ERRORS;
		errorLimit = Defaults.ERROR_LIMIT;
	}
	
	public ConnectionState(
			final Object dataToken,
			final String crossProcessId,
			final long serverTimestamp,
			final long harvestInterval,
			final TimeUnit harvestIntervalTimeUnit,
			final long maxTransactionAge,
			final TimeUnit maxTransactionAgeTimeUnit,
			final long maxTransactionCount,
			final int stackTraceLimit,
			final int responseBodyLimit,
			final boolean collectingNetworkerrors,
			final int errorLimit
	) {
		this.dataToken = dataToken;
		this.crossProcessId = crossProcessId;
		this.serverTimestamp = serverTimestamp;
		this.harvestInterval = harvestInterval;
		this.harvestIntervalTimeUnit = harvestIntervalTimeUnit;
		this.maxTransactionAge = maxTransactionAge;
		this.maxTransactionAgeTimeUnit = maxTransactionAgeTimeUnit;
		this.maxTransactionCount = maxTransactionCount;
		this.stackTraceLimit = stackTraceLimit;
		this.responseBodyLimit = responseBodyLimit;
		this.collectingNetworkErrors = collectingNetworkerrors;
		this.errorLimit = errorLimit;
	}

	public Object getDataToken() {
		return dataToken;
	}
	
	public String getCrossProcessId() {
		return crossProcessId;
	}
	
	public long getServerTimestamp() {
		return serverTimestamp;
	}
	
	public long getHarvestIntervalInSeconds() {
		return TimeUnit.SECONDS.convert(harvestInterval, harvestIntervalTimeUnit);
	}
	
	public long getHarvestIntervalInMilliseconds() {
		return TimeUnit.MILLISECONDS.convert(harvestInterval, harvestIntervalTimeUnit);
	}
	
	public long getMaxTransactionAgeInSeconds() {
		return TimeUnit.SECONDS.convert(maxTransactionAge, maxTransactionAgeTimeUnit);
	}
	
	public long getMaxTransactionAgeInMilliseconds() {
		return TimeUnit.MILLISECONDS.convert(maxTransactionAge, maxTransactionAgeTimeUnit);
	}
	
	public long getMaxTransactionCount() {
		return maxTransactionCount;
	}
	
	public int getStackTraceLimit() {
		return stackTraceLimit;
	}
	
	public int getResponseBodyLimit() {
		return responseBodyLimit;
	}
	
	public boolean isCollectingNetworkErrors() {
		return collectingNetworkErrors;
	}
	
	public int getErrorLimit() {
		return errorLimit;
	}
	
	public String toString() {
		final StringBuilder sb = new StringBuilder();
		sb.append(dataToken);
		return sb.toString();
	}
}
