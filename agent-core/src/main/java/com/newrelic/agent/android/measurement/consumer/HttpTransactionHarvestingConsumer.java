/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.measurement.consumer;

import com.newrelic.agent.android.Agent;
import com.newrelic.agent.android.analytics.AnalyticsAttribute;
import com.newrelic.agent.android.analytics.AnalyticsControllerImpl;
import com.newrelic.agent.android.analytics.EventManagerImpl;
import com.newrelic.agent.android.analytics.EventTransformAdapter;
import com.newrelic.agent.android.harvest.Harvest;
import com.newrelic.agent.android.harvest.HttpTransaction;
import com.newrelic.agent.android.measurement.Measurement;
import com.newrelic.agent.android.measurement.MeasurementType;
import com.newrelic.agent.android.measurement.http.HttpTransactionMeasurement;

public class HttpTransactionHarvestingConsumer extends BaseMeasurementConsumer {
    public HttpTransactionHarvestingConsumer() {
        super(MeasurementType.Network);
    }

    @Override
    public void consumeMeasurement(Measurement measurement) {
        HttpTransactionMeasurement m = (HttpTransactionMeasurement) measurement;
        HttpTransaction txn = new HttpTransaction();

        txn.setUrl(m.getUrl());
        txn.setHttpMethod(m.getHttpMethod());
        txn.setStatusCode(m.getStatusCode());
        txn.setErrorCode(m.getErrorCode());
        txn.setTotalTime(m.getTotalTime());
        txn.setCarrier(Agent.getActiveNetworkCarrier());
        txn.setWanType(Agent.getActiveNetworkWanType());
        txn.setBytesReceived(m.getBytesReceived());
        txn.setBytesSent(m.getBytesSent());
        txn.setAppData(m.getAppData());
        txn.setTimestamp(m.getStartTime());
        txn.setResponseBody(m.getResponseBody());
        txn.setParams(m.getParams());
        txn.setTraceContext(m.getTraceContext());
        txn.setTraceAttributes(m.getTraceAttributes());

        // transform url if a suitable transformer is available
        EventManagerImpl eventManager = (EventManagerImpl) AnalyticsControllerImpl.getInstance().getEventManager();
        if (eventManager.getListener() instanceof EventTransformAdapter) {
            EventTransformAdapter transformer = (EventTransformAdapter) eventManager.getListener();
            String url = transformer.onAttributeTransform(AnalyticsAttribute.REQUEST_URL_ATTRIBUTE, txn.getUrl());
            txn.setUrl(url);
        }

        Harvest.addHttpTransaction(txn);
    }
}
