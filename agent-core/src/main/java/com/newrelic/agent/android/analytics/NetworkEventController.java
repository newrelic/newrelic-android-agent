/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.analytics;

import com.newrelic.agent.android.FeatureFlag;
import com.newrelic.agent.android.harvest.HttpTransaction;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;

public class NetworkEventController {

    static final AgentLog log = AgentLogManager.getAgentLog();

    public static void createHttpErrorEvent(final HttpTransaction httpTransaction) {

        if (FeatureFlag.featureEnabled(FeatureFlag.NetworkErrorRequests)) {
            if (!AnalyticsControllerImpl.getInstance().addEvent(NetworkRequestErrorEvent.createHttpErrorEvent(httpTransaction))) {
                log.error("Failed to add " + AnalyticsEvent.EVENT_TYPE_MOBILE_REQUEST_ERROR);
            } else {
                log.audit(AnalyticsEventCategory.RequestError.toString() + " added to event store for request: " + httpTransaction.getUrl());
            }
        }
    }

    public static void createNetworkFailureEvent(final HttpTransaction httpTransaction) {
        if (FeatureFlag.featureEnabled(FeatureFlag.NetworkErrorRequests)) {
            if (!AnalyticsControllerImpl.getInstance().addEvent(NetworkRequestErrorEvent.createNetworkFailureEvent(httpTransaction))) {
                log.error("Failed to add " + AnalyticsEvent.EVENT_TYPE_MOBILE_REQUEST_ERROR);
            } else {
                log.audit(AnalyticsEventCategory.RequestError.toString() + " added to event store for request: " + httpTransaction.getUrl());
            }
        }
    }

    public static void createNetworkRequestEvent(final HttpTransaction txn) {
        if (FeatureFlag.featureEnabled(FeatureFlag.NetworkRequests)) {
            if (!AnalyticsControllerImpl.getInstance().addEvent(NetworkRequestEvent.createNetworkEvent((txn)))) {
                log.error("Failed to add " + AnalyticsEvent.EVENT_TYPE_MOBILE_REQUEST);
            } else {
                log.audit(AnalyticsEventCategory.NetworkRequest.toString() + " added to event store for request: " + txn.getUrl());
            }
        }
    }

    protected NetworkEventController() {

    }
}
