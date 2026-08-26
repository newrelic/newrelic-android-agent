/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.analytics;

import java.util.Set;

public class MobileViewEvent extends AnalyticsEvent {

    public MobileViewEvent(String name, Set<AnalyticsAttribute> attributeSet) {
        super(name, AnalyticsEventCategory.MobileView, AnalyticsEvent.EVENT_TYPE_MOBILE_VIEW, attributeSet);
    }
}
