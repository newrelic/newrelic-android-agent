/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.tracing;

public interface TraceLifecycleAware {
    public void onEnterMethod();

    public void onExitMethod();

    public void onTraceStart(ActivityTrace activityTrace);

    public void onTraceComplete(ActivityTrace activityTrace);

    public void onTraceRename(ActivityTrace activityTrace);
}
