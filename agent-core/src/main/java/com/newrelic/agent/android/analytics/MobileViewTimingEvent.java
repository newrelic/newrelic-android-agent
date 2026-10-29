/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.analytics;

import java.util.Set;

public class MobileViewTimingEvent extends AnalyticsEvent {
    public MobileViewTimingEvent(String name, Set<AnalyticsAttribute> attributeSet) {
        super(name, AnalyticsEventCategory.MobileViewTiming, AnalyticsEvent.EVENT_TYPE_MOBILE_VIEW_TIMING, attributeSet);
    }

    // MobileViewTiming is a reserved event type: the base check would reject the agent's own event.
    // The event is nameless (identified by timingName), so only the type is validated.
    @Override
    public boolean isValid() {
        return validator.isValidEventType(eventType);
    }
}
