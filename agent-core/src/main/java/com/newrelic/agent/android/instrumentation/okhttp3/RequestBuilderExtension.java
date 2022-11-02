/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.instrumentation.okhttp3;

import com.newrelic.agent.android.Agent;
import com.newrelic.agent.android.util.Constants;

import okhttp3.Request;

public class RequestBuilderExtension {

    private Request.Builder impl;

    public RequestBuilderExtension(Request.Builder impl) {
        this.impl = impl;
        setCrossProcessHeader();
    }

    public Request build() {
        return impl.build();
    }

    private void setCrossProcessHeader() {
        final String crossProcessId = Agent.getCrossProcessId();
        if (crossProcessId != null) {
            impl.removeHeader(Constants.Network.CROSS_PROCESS_ID_HEADER);
            impl.addHeader(Constants.Network.CROSS_PROCESS_ID_HEADER, crossProcessId);
        }
    }
}
