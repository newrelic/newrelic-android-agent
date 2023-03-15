/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.analytics;

public enum AnalyticsEventCategory {
    Session,
    Interaction,
    Crash,
    Custom,
    NetworkRequest,
    RequestError,
    Breadcrumb,
    UserAction;

    public static AnalyticsEventCategory fromString(String categoryString) {
        AnalyticsEventCategory category = Custom;
        if (categoryString != null) {
            if (categoryString.equalsIgnoreCase("session")) {
                category = Session;
            } else if (categoryString.equalsIgnoreCase("interaction")) {
                category = Interaction;
            } else if (categoryString.equalsIgnoreCase("crash")) {
                category = Crash;
            } else if (categoryString.equalsIgnoreCase("requesterror")) {
                category = RequestError;
            } else if (categoryString.equalsIgnoreCase("breadcrumb")) {
                category = Breadcrumb;
            } else if (categoryString.equalsIgnoreCase("networkrequest")) {
                category = NetworkRequest;
            } else if (categoryString.equalsIgnoreCase("useraction")) {
                category = UserAction;
            }
        }
        return category;
    }
}
