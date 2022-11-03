/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.analytics;

import com.newrelic.agent.android.Agent;
import com.newrelic.agent.android.FeatureFlag;
import com.newrelic.agent.android.harvest.HttpTransaction;
import com.newrelic.agent.android.util.Constants;

import java.util.Set;

public class NetworkRequestErrorEvent extends AnalyticsEvent {

    static final String DISABLE_FF_RESPONSE = "NEWRELIC_RESPONSE_BODY_CAPTURE_DISABLED";

    static Set<AnalyticsAttribute> createErrorAttributeSet(final HttpTransaction httpTransaction) {
        final Set<AnalyticsAttribute> attributes = NetworkRequestEvent.createDefaultAttributeSet(httpTransaction);

        if (FeatureFlag.featureEnabled(FeatureFlag.HttpResponseBodyCapture)) {
            String responseBody = httpTransaction.getResponseBody();
            if (!(responseBody == null || responseBody.isEmpty())) {
                if (responseBody.length() > AnalyticsAttribute.ATTRIBUTE_VALUE_MAX_LENGTH) {
                    log.warning("NetworkRequestErrorEvent: truncating response body to " + AnalyticsAttribute.ATTRIBUTE_VALUE_MAX_LENGTH + " bytes");
                    responseBody = responseBody.substring(0, AnalyticsAttribute.ATTRIBUTE_VALUE_MAX_LENGTH);
                }
                String encodedResponseBody = Agent.getEncoder().encodeNoWrap(responseBody.getBytes());
                if (!(encodedResponseBody == null || encodedResponseBody.isEmpty())) {
                    attributes.add(new AnalyticsAttribute(AnalyticsAttribute.RESPONSE_BODY_ATTRIBUTE, encodedResponseBody));
                }
            }
        } else {
            attributes.add(new AnalyticsAttribute(AnalyticsAttribute.RESPONSE_BODY_ATTRIBUTE, DISABLE_FF_RESPONSE));
        }

        String appData = httpTransaction.getAppData();
        if (!(appData == null || appData.isEmpty())) {
            attributes.add(new AnalyticsAttribute(AnalyticsAttribute.APP_DATA_ATTRIBUTE, appData));
        }

        if (httpTransaction.getParams() != null) {
            String contentType = httpTransaction.getParams().get(Constants.Transactions.CONTENT_TYPE);
            if (!(contentType == null || contentType.isEmpty())) {
                attributes.add(new AnalyticsAttribute(AnalyticsAttribute.CONTENT_TYPE_ATTRIBUTE, contentType));
            }
        }

        return attributes;
    }

    public NetworkRequestErrorEvent(Set<AnalyticsAttribute> attributeSet) {
        super(null, AnalyticsEventCategory.RequestError, AnalyticsEvent.EVENT_TYPE_MOBILE_REQUEST_ERROR, attributeSet);
    }

    public static NetworkRequestErrorEvent createHttpErrorEvent(final HttpTransaction httpTransaction) {
        final Set<AnalyticsAttribute> attributes = createErrorAttributeSet(httpTransaction);

        attributes.add(new AnalyticsAttribute(AnalyticsAttribute.STATUS_CODE_ATTRIBUTE, httpTransaction.getStatusCode()));

        return new NetworkRequestErrorEvent(attributes);
    }

    public static NetworkRequestErrorEvent createNetworkFailureEvent(final HttpTransaction httpTransaction) {
        final Set<AnalyticsAttribute> attributes = createErrorAttributeSet(httpTransaction);

        attributes.add(new AnalyticsAttribute(AnalyticsAttribute.NETWORK_ERROR_CODE_ATTRIBUTE, httpTransaction.getErrorCode()));

        return new NetworkRequestErrorEvent(attributes);
    }

}