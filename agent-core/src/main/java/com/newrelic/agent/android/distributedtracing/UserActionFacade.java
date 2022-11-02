/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.distributedtracing;

import com.newrelic.agent.android.FeatureFlag;
import com.newrelic.agent.android.analytics.AnalyticsAttribute;
import com.newrelic.agent.android.analytics.AnalyticsControllerImpl;
import com.newrelic.agent.android.analytics.AnalyticsEvent;
import com.newrelic.agent.android.analytics.AnalyticsEventCategory;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;


public class UserActionFacade {

    private static TraceFacade traceFacade = DistributedTracing.getInstance();
    private static final AtomicReference<UserActionFacade> instance = new AtomicReference<>(null);

    public static UserActionFacade getInstance() {
        UserActionFacade.instance.compareAndSet(null, new UserActionFacade());
        return UserActionFacade.instance.get();
    }

    static void setTraceFacade(final TraceFacade traceFacade) {
        UserActionFacade.traceFacade = traceFacade;
    }

    public void recordUserAction(UserActionType userActionType) {
        recordUserAction(userActionType, null);
    }

    public void recordUserAction(UserActionType userActionType, Map<String, Object> userActionAttributes) {
        if (FeatureFlag.featureEnabled(FeatureFlag.DistributedTracing)) {
            Map<String, Object> attributes = new TreeMap<>();

            attributes.put(DistributedTracing.ACTION_TYPE_ATTRIBUTE, userActionType.toString());

            if (userActionAttributes != null) {
                attributes.putAll(userActionAttributes);
            }

            AnalyticsControllerImpl.getInstance().internalRecordEvent(
                    AnalyticsEvent.EVENT_NAME_IS_TYPE,
                    AnalyticsEventCategory.UserAction,
                    AnalyticsEvent.EVENT_TYPE_MOBILE_USER_ACTION,
                    attributes);
        }
    }

}