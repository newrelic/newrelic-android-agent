/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.payload;

import java.util.concurrent.Callable;

class PayloadReaper implements Callable<PayloadSender> {
    final PayloadSender sender;
    final PayloadSender.CompletionHandler handler;

    public PayloadReaper(PayloadSender sender, PayloadSender.CompletionHandler handler) {
        if (sender == null) {
            throw new NullPointerException("Must provide payload sender!");
        }

        this.sender = sender;
        this.handler = handler;
    }

    @Override
    public PayloadSender call() throws Exception {
        PayloadSender payloadSender = null;
        try {
            payloadSender = sender.call();

            if (handler != null) {
                handler.onResponse(payloadSender);
            }

            return payloadSender;

        } catch (Exception e) {
            if (handler != null) {
                handler.onException(sender, e);
            }
        }

        return null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o instanceof PayloadReaper) {
            PayloadReaper payloadReaper = (PayloadReaper) o;
            return this.sender.payload.equals(payloadReaper.sender.payload);
        }
        return false;
    }

    public String getUuid() {
        return sender.getPayload().getUuid();
    }

}
