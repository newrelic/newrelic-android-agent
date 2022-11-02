/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.analytics;

import java.util.Set;

public class SessionEvent extends AnalyticsEvent {
    public SessionEvent() {
        super(null, AnalyticsEventCategory.Session);
    }

    public SessionEvent(Set<AnalyticsAttribute> attributeSet) {
        super(null, AnalyticsEventCategory.Session, AnalyticsEvent.EVENT_TYPE_MOBILE, attributeSet);
    }
}
