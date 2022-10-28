/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.compile.transformers;

import java.lang.instrument.ClassFileTransformer;


public interface NewRelicClassTransformer extends ClassFileTransformer {
    /**
     * The classes into which we're injecting code.
     * The methods in the class that we're modifying.
     */
    String NR_CLASS_REWRITER_CLASS_NAME = "com/newrelic/agent/compile/ClassTransformer";
    String NR_CLASS_REWRITER_METHOD_NAME = "transformClassBytes";
    String NR_CLASS_REWRITER_METHOD_SIGNATURE = "(Ljava/lang/String;[B)[B";

    boolean modifies(Class<?> clazz);
}

