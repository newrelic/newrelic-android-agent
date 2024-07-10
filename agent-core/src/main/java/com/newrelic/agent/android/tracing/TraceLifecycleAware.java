/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.tracing;

public interface TraceLifecycleAware {
    default void onEnterMethod() {}

    default void onExitMethod() {}

    default void onTraceStart(ActivityTrace activityTrace) {}

    default void onTraceComplete(ActivityTrace activityTrace) {}

    default void onTraceRename(ActivityTrace activityTrace) {}
}
