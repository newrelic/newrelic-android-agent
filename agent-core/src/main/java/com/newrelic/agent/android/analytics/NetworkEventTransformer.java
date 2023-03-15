/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.analytics;

import java.util.Map;

public class NetworkEventTransformer extends EventTransformAdapter {
    public NetworkEventTransformer(final Map<String, String> transformations) {
        withAttributeTransform(AnalyticsAttribute.REQUEST_URL_ATTRIBUTE, transformations);
    }

    @Override
    public boolean onEventAdded(AnalyticsEvent analyticsEvent) {
        switch (analyticsEvent.getEventType()) {
            case AnalyticsEvent.EVENT_TYPE_MOBILE_REQUEST:          // "MobileRequest"
            case AnalyticsEvent.EVENT_TYPE_MOBILE_REQUEST_ERROR:    // "MobileRequestError"
                onEventTransform(analyticsEvent);
                break;

            default:
                break;
        }

        return super.onEventAdded(analyticsEvent);
    }
}
