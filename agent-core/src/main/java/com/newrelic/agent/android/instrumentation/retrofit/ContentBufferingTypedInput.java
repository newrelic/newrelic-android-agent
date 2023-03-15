/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.instrumentation.retrofit;

import com.newrelic.agent.android.instrumentation.io.CountingInputStream;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import retrofit.mime.TypedInput;

public class ContentBufferingTypedInput implements TypedInput {
    private static final AgentLog log = AgentLogManager.getAgentLog();

    private TypedInput impl;
    private CountingInputStream inputStream;

    public ContentBufferingTypedInput(TypedInput impl) {
        if (impl == null) {
            impl = new EmptyBodyTypedInput();
        }
        this.impl = impl;
        this.inputStream = null;
    }

    @Override
    public String mimeType() {
        return impl.mimeType();
    }

    @Override
    public long length() {
        try {
            prepareInputStream();
            return inputStream.available();
        } catch (IOException e) {
            log.error("ContentBufferingTypedInput generated an IO exception: ", e);
        }
        return -1;
    }

    @Override
    public InputStream in() throws IOException {
        prepareInputStream();
        return inputStream;
    }

    private void prepareInputStream() throws IOException {
        if (this.inputStream == null) {
            try {
	            InputStream is = impl.in();
    	        if (is == null) {
        	        is = new ByteArrayInputStream(new byte[0]);
            	}
                this.inputStream = new CountingInputStream(is, true);
            } catch (Exception e) {
                log.error("ContentBufferingTypedInput: " + e.toString());
            }
        }
    }
}
