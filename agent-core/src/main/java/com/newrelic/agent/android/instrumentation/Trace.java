/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.instrumentation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

//import java.lang.annotation.Retention;
//import java.lang.annotation.RetentionPolicy;

@Target(ElementType.METHOD)
//@Retention(RetentionPolicy.RUNTIME)
public @interface Trace {
    public static final String NULL = "";

    String metricName() default NULL;

    boolean skipTransactionTrace() default false;

    MetricCategory category() default MetricCategory.NONE;
}
