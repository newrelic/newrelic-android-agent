/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.analytics;

import java.util.Set;

class AnalyticsEventFactory {

    static AnalyticsEvent createEvent(String name, AnalyticsEventCategory eventCategory, String eventType, Set<AnalyticsAttribute> eventAttributes) {
        AnalyticsEvent event = null;
        switch (eventCategory) {
            case Session:
                event = new SessionEvent(eventAttributes);
                break;
            case RequestError:
                event = new NetworkRequestErrorEvent(eventAttributes);
                break;
            case Interaction:
                event = new InteractionEvent(name, eventAttributes);
                break;
            case Crash:
                event = new CrashEvent(name, eventAttributes);
                break;
            case Custom:
                event = new CustomEvent(name, eventType, eventAttributes);
                break;
            case Breadcrumb:
                event = new BreadcrumbEvent(name, eventAttributes);
                break;
            case NetworkRequest:
                event = new NetworkRequestEvent(eventAttributes);
                break;
            case UserAction:
                event = new UserActionEvent(name, eventAttributes);
                break;
            case ApplicationExit:
                event = new ApplicationExitEvent(name, eventAttributes);
                break;
        }

        return event;
    }

    // Private constructor to prevent instantiation
    private AnalyticsEventFactory() {
    }
}
