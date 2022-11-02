/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.analytics;

import java.util.Set;

public class CustomEvent extends AnalyticsEvent {
    public CustomEvent(String name) {
        super(name, AnalyticsEventCategory.Custom);
    }

    public CustomEvent(String name, Set<AnalyticsAttribute> attributeSet) {
        super(name, AnalyticsEventCategory.Custom, null, attributeSet);
    }

    public CustomEvent(String name, String eventType, Set<AnalyticsAttribute> attributeSet) {
        super(name, AnalyticsEventCategory.Custom, eventType, attributeSet);
    }

}
