/*
 * Copyright (c) 2024. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.analytics;

import com.newrelic.agent.android.harvest.crash.ThreadInfo;

import java.util.List;
import java.util.Set;

public class ApplicationExitEvent extends AnalyticsEvent {

    public ApplicationExitEvent(String name, Set<AnalyticsAttribute> attributeSet) {
        super(name, AnalyticsEventCategory.ApplicationExit, AnalyticsEvent.EVENT_TYPE_MOBILE_APPLICATION_EXIT, attributeSet);
    }

    @Override
    public boolean isValid() {
        return validator.isValidEventName(name) && (validator.isValidEventType(eventType));
    }
}
