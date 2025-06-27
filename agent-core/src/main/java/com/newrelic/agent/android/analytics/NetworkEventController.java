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
            // Check if the domain should be ignored
            if (shouldIgnoreNetworkRequest(httpTransaction.getUrl())) {
                log.debug("Ignoring network error event to filtered domain: " + httpTransaction.getUrl());
                return;
            }
            
            if (!AnalyticsControllerImpl.getInstance().addEvent(NetworkRequestErrorEvent.createHttpErrorEvent(httpTransaction))) {
                log.error("Failed to add " + AnalyticsEvent.EVENT_TYPE_MOBILE_REQUEST_ERROR);
            } else {
                log.audit(AnalyticsEventCategory.RequestError.toString() + " added to event store for request: " + httpTransaction.getUrl());
            }
        }
    }

    public static void createNetworkFailureEvent(final HttpTransaction httpTransaction) {
        if (FeatureFlag.featureEnabled(FeatureFlag.NetworkErrorRequests)) {
            // Check if the domain should be ignored
            if (shouldIgnoreNetworkRequest(httpTransaction.getUrl())) {
                log.debug("Ignoring network failure event to filtered domain: " + httpTransaction.getUrl());
                return;
            }
            
            if (!AnalyticsControllerImpl.getInstance().addEvent(NetworkRequestErrorEvent.createNetworkFailureEvent(httpTransaction))) {
                log.error("Failed to add " + AnalyticsEvent.EVENT_TYPE_MOBILE_REQUEST_ERROR);
            } else {
                log.audit(AnalyticsEventCategory.RequestError.toString() + " added to event store for request: " + httpTransaction.getUrl());
            }
        }
    }

    public static void createNetworkRequestEvent(final HttpTransaction txn) {
        if (FeatureFlag.featureEnabled(FeatureFlag.NetworkRequests)) {
            // Check if the domain should be ignored
            if (shouldIgnoreNetworkRequest(txn.getUrl())) {
                log.debug("Ignoring network request to filtered domain: " + txn.getUrl());
                return;
            }
            
            if (!AnalyticsControllerImpl.getInstance().addEvent(NetworkRequestEvent.createNetworkEvent((txn)))) {
                log.error("Failed to add " + AnalyticsEvent.EVENT_TYPE_MOBILE_REQUEST);
            } else {
                log.audit(AnalyticsEventCategory.NetworkRequest.toString() + " added to event store for request: " + txn.getUrl());
            }
        }
    }

    protected NetworkEventController() {

    }

    /**
     * Check if a network request URL should be ignored based on configured domain filters.
     *
     * @param url The URL to check
     * @return true if the request should be ignored, false otherwise
     */
    private static boolean shouldIgnoreNetworkRequest(String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }

        try {
            java.net.URL urlObj = new java.net.URL(url);
            String host = urlObj.getHost();
            
            if (host == null || host.isEmpty()) {
                return false;
            }

            java.util.List<String> ignoredDomains = com.newrelic.agent.android.AgentConfiguration.getInstance().getIgnoredNetworkDomains();
            
            for (String ignoredDomain : ignoredDomains) {
                if (ignoredDomain != null && !ignoredDomain.isEmpty()) {
                    // Check exact match or subdomain match
                    if (host.equals(ignoredDomain) || host.endsWith("." + ignoredDomain)) {
                        return true;
                    }
                }
            }
        } catch (java.net.MalformedURLException e) {
            log.debug("Failed to parse URL for domain filtering: " + url);
        }

        return false;
    }
}
