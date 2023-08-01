/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.analytics;

import com.newrelic.agent.android.FeatureFlag;
import com.newrelic.agent.android.distributedtracing.TraceContext;
import com.newrelic.agent.android.harvest.HttpTransaction;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;


public class NetworkRequestEvent extends AnalyticsEvent {

    public NetworkRequestEvent(Set<AnalyticsAttribute> attributeSet) {
        super(null, AnalyticsEventCategory.NetworkRequest, AnalyticsEvent.EVENT_TYPE_MOBILE_REQUEST, attributeSet);
    }

    public static NetworkRequestEvent createNetworkEvent(final HttpTransaction txn) {
        final Set<AnalyticsAttribute> attributes = createDefaultAttributeSet(txn);

        attributes.add(new AnalyticsAttribute(AnalyticsAttribute.RESPONSE_TIME_ATTRIBUTE, txn.getTotalTime()));
        attributes.add(new AnalyticsAttribute(AnalyticsAttribute.STATUS_CODE_ATTRIBUTE, txn.getStatusCode()));
        attributes.add(new AnalyticsAttribute(AnalyticsAttribute.BYTES_SENT_ATTRIBUTE, txn.getBytesSent()));
        attributes.add(new AnalyticsAttribute(AnalyticsAttribute.BYTES_RECEIVED_ATTRIBUTE, txn.getBytesReceived()));

        return new NetworkRequestEvent(attributes);
    }

    static Set<AnalyticsAttribute> createDefaultAttributeSet(final HttpTransaction txn) {
        final Set<AnalyticsAttribute> attributes = new HashSet<AnalyticsAttribute>();

        try {
            final URL url = new URL(txn.getUrl());
            attributes.add(new AnalyticsAttribute(AnalyticsAttribute.REQUEST_DOMAIN_ATTRIBUTE, url.getHost()));
            attributes.add(new AnalyticsAttribute(AnalyticsAttribute.REQUEST_PATH_ATTRIBUTE, url.getPath()));
        } catch (MalformedURLException e) {
            log.error(txn.getUrl() + " is not a valid URL. Unable to set host or path attributes.");
        }

        attributes.add(new AnalyticsAttribute(AnalyticsAttribute.REQUEST_URL_ATTRIBUTE, txn.getUrl()));
        attributes.add(new AnalyticsAttribute(AnalyticsAttribute.CONNECTION_TYPE_ATTRIBUTE, txn.getWanType()));
        attributes.add(new AnalyticsAttribute(AnalyticsAttribute.REQUEST_METHOD_ATTRIBUTE, txn.getHttpMethod()));

        double totalTime = txn.getTotalTime();
        if (totalTime != 0d) {
            attributes.add(new AnalyticsAttribute(AnalyticsAttribute.RESPONSE_TIME_ATTRIBUTE, totalTime));
        }

        double bytesSent = txn.getBytesSent();
        if (bytesSent != 0d) {
            attributes.add(new AnalyticsAttribute(AnalyticsAttribute.BYTES_SENT_ATTRIBUTE, bytesSent));
        }

        double bytesReceived = txn.getBytesReceived();
        if (bytesReceived != 0d) {
            attributes.add(new AnalyticsAttribute(AnalyticsAttribute.BYTES_RECEIVED_ATTRIBUTE, bytesReceived));
        }

        if (txn.getParams() != null) {
            for (String key : txn.getParams().keySet()) {
                attributes.add(new AnalyticsAttribute(key, txn.getParams().get(key)));
            }
        }

        if (FeatureFlag.featureEnabled(FeatureFlag.DistributedTracing)) {
            TraceContext traceContext = txn.getTraceContext();
            if (traceContext != null || txn.getTraceAttributes() != null) {
                try {
                    Set<AnalyticsAttribute> validatedTracePayloadAttributes =
                            validator.toValidatedAnalyticsAttributes(traceContext != null ? traceContext.asTraceAttributes() : txn.getTraceAttributes());

                    attributes.addAll(validatedTracePayloadAttributes);
                } catch (Exception e) {
                    log.error("Error occurred parsing the instrinsic attribute set: ", e);
                }
            }
        }

        return attributes;
    }
}
