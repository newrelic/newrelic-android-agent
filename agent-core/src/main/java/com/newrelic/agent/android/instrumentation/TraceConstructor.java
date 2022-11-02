/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.instrumentation;

/**
 * This annotation is used to trace constructors.  It works much like @ReplaceCallSite except it wraps constructors with
 * the appropriate calls to the trace machine.
 * @ReplaceCallSite is insufficient because we don't have a proper method name (in bytecode the constructor method is
 * "<init>".  Additionally, it's not possible to actually swizzle the constructor call due to things like VM type
 * checking and avoiding lots of stack fiddling.  Thus, the code within the method marked with this annotation will never
 * be called.
 */
public @interface TraceConstructor {
}
