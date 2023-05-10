/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.analytics;

import java.util.Set;

public class CrashEvent extends AnalyticsEvent {

    public CrashEvent(String name, Set<AnalyticsAttribute> attributeSet) {
        super(name, AnalyticsEventCategory.Crash, AnalyticsEvent.EVENT_TYPE_MOBILE, attributeSet);
    }
}
