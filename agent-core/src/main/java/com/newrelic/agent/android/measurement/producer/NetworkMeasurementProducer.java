/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.measurement.producer;

import com.newrelic.agent.android.measurement.MeasurementType;
import com.newrelic.agent.android.measurement.http.HttpTransactionMeasurement;
import com.newrelic.agent.android.util.Util;

public class NetworkMeasurementProducer extends BaseMeasurementProducer {
    public NetworkMeasurementProducer() {
        super(MeasurementType.Network);
    }

    public void produceMeasurement(String urlString, String httpMethod, int statusCode, int errorCode, long startTime, double totalTime, long bytesSent, long bytesReceived, String appData) {
        String url = Util.sanitizeUrl(urlString);
        if (url == null)
            return;

        produceMeasurement(new HttpTransactionMeasurement(url, httpMethod, statusCode, errorCode, startTime, totalTime, bytesSent, bytesReceived, appData));
    }

    public void produceMeasurement(HttpTransactionMeasurement transactionMeasurement) {
        String url = Util.sanitizeUrl(transactionMeasurement.getUrl());
        if (url == null)
            return;

        transactionMeasurement.setUrl(url);
        super.produceMeasurement(transactionMeasurement);
    }
}
