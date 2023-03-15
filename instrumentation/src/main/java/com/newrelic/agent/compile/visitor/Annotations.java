/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.compile.visitor;


public final class Annotations {
    public static boolean isNewRelicAnnotation(final String descriptor) {
        return descriptor.startsWith("Lcom/newrelic/agent/android/instrumentation/");
    }

    private Annotations() {
    }
}
