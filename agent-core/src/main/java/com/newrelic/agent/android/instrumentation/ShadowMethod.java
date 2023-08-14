package com.newrelic.agent.android.instrumentation;

/**
 * This annotation is used to make shadow calls to static agent methods. These delegates must not
 * modify the passed data, but are provided merely for inspection.
 *
 * To instrument static methods, simply specify isStatic = true.
 *
 * NOTE: The @Shadow annotation supports an optional scope argument.  The purpose of scope is to
 * limit instrumentation to a specific class at the cost of not instrumenting sub or super classes.
 *
 */
public @interface ShadowMethod {
	boolean isStatic() default false;
    String scope() default "";
}
