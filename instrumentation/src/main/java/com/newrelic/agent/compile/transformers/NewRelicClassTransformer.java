/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.compile.transformers;

import java.lang.instrument.ClassFileTransformer;


public interface NewRelicClassTransformer extends ClassFileTransformer {

    boolean modifies(Class<?> clazz);
}

