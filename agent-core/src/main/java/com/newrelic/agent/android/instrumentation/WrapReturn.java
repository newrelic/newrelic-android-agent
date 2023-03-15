/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.instrumentation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
public @interface WrapReturn {
	String className();
	String methodName();
	/**
	 *
	 * @see org.objectweb.asm.commons.Method
	 */
	String methodDesc();
}
