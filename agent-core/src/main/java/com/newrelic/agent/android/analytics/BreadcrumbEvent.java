/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.analytics;

import java.util.Set;

public class BreadcrumbEvent extends AnalyticsEvent {

    public BreadcrumbEvent(String name, Set<AnalyticsAttribute> attributeSet) {
        super(name, AnalyticsEventCategory.Breadcrumb, AnalyticsEvent.EVENT_TYPE_MOBILE_BREADCRUMB, attributeSet);
    }
}
