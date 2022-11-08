/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.background;

import java.util.EventObject;

public class ApplicationStateEvent extends EventObject {
	private static final long serialVersionUID = 1L;
	
	public ApplicationStateEvent(Object source) {
		super(source);
	}
}
