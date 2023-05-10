/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.instrumentation;


/**
 * This annotation is used to replace method invocations in client code with calls to static agent methods.
 * To replace static methods, simply specify isStatic = true.
 *
 * NOTE: Since we're not able to determine the full class hierarchy at compile time, we don't include the class name when
 * indicating we want to instrument a method.  This works well when methods have unique names and signatures. However,
 * some methods have very generic names and signatures (for example: object.toString()) and instrumentation of such
 * methods would result in instrumentation leak.  Thus, the @ReplaceCallSite annotation supports an optional scope
 * argument.  The purpose of scope is to limit instrumentation to a specific class at the cost of not instrumenting sub
 * or super classes.
 *
 * This example replaces calls to android.view.LayoutInflater.setFactory(Landroid.view.LayoutInflater.Factory;)V
 * with calls to the following method.
 *
 * <code>
 * 
 * @ReplaceCallSite
 * public static void setFactory(final android.view.LayoutInflater inflater, final android.view.LayoutInflater.Factory factory) {
 * }
 * 
 * </code>
 *
 *
 */
public @interface ReplaceCallSite {
	boolean isStatic() default false;
    String scope() default "";
}
