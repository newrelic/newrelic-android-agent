/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.hybrid;

import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.analytics.AnalyticsAttribute;
import com.newrelic.agent.android.analytics.AnalyticsControllerImpl;
import com.newrelic.agent.android.analytics.AnalyticsEvent;
import com.newrelic.agent.android.error.Error;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class JSErrorDataController {
    protected static final AgentConfiguration agentConfiguration = new AgentConfiguration();
    private static final AgentLog log = AgentLogManager.getAgentLog();

    public static boolean sendJSErrorData(String name, String message, String stackTrace, boolean isFatal, String jsAppVersion, Map<String, Object> additionalAttributes) {
        final HashMap<String, Object> eventAttributes = new HashMap<>();
        try {
            //map attributes first, then all internal attributes will overwrite if any duplicate
            if (additionalAttributes != null) {
                eventAttributes.putAll(additionalAttributes);
            }
            eventAttributes.put(AnalyticsAttribute.JSERROR_ERRORID, UUID.randomUUID().toString());
            eventAttributes.put(AnalyticsAttribute.JSERROR_THREADS, stackTrace);
            eventAttributes.put(AnalyticsAttribute.JSERROR_ISFATAL, isFatal);
            eventAttributes.put(AnalyticsAttribute.JSERROR_ERRORTYPE, name);
            eventAttributes.put(AnalyticsAttribute.JSERROR_DESCRIPTION, message);
            eventAttributes.put(AnalyticsAttribute.JSERROR_CAUSE, message);
            eventAttributes.put(AnalyticsAttribute.JSERROR_TIMESTAMP, System.currentTimeMillis());
            eventAttributes.put(AnalyticsAttribute.EVENT_TYPE_ATTRIBUTE, AnalyticsEvent.EVENT_TYPE_MOBILE_JSERROR);

            JSErrorDataReporter jsErrorReporter = JSErrorDataReporter.getInstance();
            final AnalyticsControllerImpl analyticsController = AnalyticsControllerImpl.getInstance();
            Error jsError = new Error(analyticsController.getSessionAttributes(), eventAttributes);
            //add jsAppVersion to sessionAttribute key value
            jsError.getSessionAttributes().add(new AnalyticsAttribute(AnalyticsAttribute.JSERROR_APP_VERSION, jsAppVersion));

            jsErrorReporter.reportJSErrorData(jsError.asJsonObject().toString().getBytes());
            return true;
        }catch(Exception ex){
            log.error("HandledJSError: exception " + ex.getClass().getName() + " failed to send data.");
        }

        return false;
    }
}
