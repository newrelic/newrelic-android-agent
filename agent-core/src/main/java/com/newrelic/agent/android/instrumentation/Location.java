/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.instrumentation;

public class Location {
	private final String countryCode;
	private final String region;
	
	public Location(String countryCode, String region) {
		if (countryCode == null || region == null) {
			throw new IllegalArgumentException("Country code and region must not be null.");
		}
		this.countryCode = countryCode;
		this.region = region;
	}
	
	public String getCountryCode() {
		return countryCode;
	}
	
	public String getRegion() {
		return region;
	}
}
