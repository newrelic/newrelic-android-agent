/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.instrumentation.httpclient;

import com.newrelic.agent.android.TaskQueue;
import com.newrelic.agent.android.api.common.TransactionData;
import com.newrelic.agent.android.instrumentation.TransactionState;
import com.newrelic.agent.android.instrumentation.TransactionStateUtil;
import com.newrelic.agent.android.instrumentation.io.CountingInputStream;
import com.newrelic.agent.android.instrumentation.io.CountingOutputStream;
import com.newrelic.agent.android.instrumentation.io.StreamCompleteEvent;
import com.newrelic.agent.android.instrumentation.io.StreamCompleteListener;
import com.newrelic.agent.android.instrumentation.io.StreamCompleteListenerSource;
import com.newrelic.agent.android.measurement.http.HttpTransactionMeasurement;

import org.apache.http.Header;
import org.apache.http.HttpEntity;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

@Deprecated
public final class HttpRequestEntityImpl implements HttpEntity, StreamCompleteListener {
    private final HttpEntity impl;
    private final TransactionState transactionState;

    public HttpRequestEntityImpl(final HttpEntity impl, final TransactionState transactionState) {
        this.impl = impl;
        this.transactionState = transactionState;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void consumeContent() throws IOException {
        try {
            impl.consumeContent();
        } catch (IOException e) {
            handleException(e);
            throw e;
        }
    }

    @Override
    public InputStream getContent() throws IOException, IllegalStateException {
        try {
            if (!transactionState.isSent()) {
                final CountingInputStream stream = new CountingInputStream(impl.getContent());
                stream.addStreamCompleteListener(this);
                return stream;
            } else {
                return impl.getContent();
            }
        } catch (IOException e) {
            handleException(e);
            throw e;
        } catch (IllegalStateException e) {
            handleException(e);
            throw e;
        }
    }

    @Override
    public Header getContentEncoding() {
        return impl.getContentEncoding();
    }

    @Override
    public long getContentLength() {
        return impl.getContentLength();
    }

    @Override
    public Header getContentType() {
        return impl.getContentType();
    }

    @Override
    public boolean isChunked() {
        return impl.isChunked();
    }

    @Override
    public boolean isRepeatable() {
        return impl.isRepeatable();
    }

    @Override
    public boolean isStreaming() {
        return impl.isStreaming();
    }

    @Override
    public void writeTo(OutputStream outstream) throws IOException {
        try {
            if (!transactionState.isSent()) {
                final CountingOutputStream stream = new CountingOutputStream(outstream);
                impl.writeTo(stream);
                transactionState.setBytesSent(stream.getCount());
            } else {
                impl.writeTo(outstream);
            }
        } catch (IOException e) {
            handleException(e);
            throw e;
        }
    }

    @Override
    public void streamComplete(StreamCompleteEvent e) {
        final StreamCompleteListenerSource source = (StreamCompleteListenerSource) e.getSource();
        source.removeStreamCompleteListener(this);
        transactionState.setBytesSent(e.getBytes());
    }

    @Override
    public void streamError(StreamCompleteEvent e) {
        final StreamCompleteListenerSource source = (StreamCompleteListenerSource) e.getSource();
        source.removeStreamCompleteListener(this);
        handleException(e.getException(), e.getBytes());
    }

    protected void handleException(Exception e) {
        handleException(e, null);
    }

    protected void handleException(Exception e, Long streamBytes) {
        TransactionStateUtil.setErrorCodeFromException(transactionState, e);
        if (!transactionState.isComplete()) {
            if (streamBytes != null) {
                transactionState.setBytesSent(streamBytes);
            }
            final TransactionData transactionData = transactionState.end();

            // If no transaction data is available, don't record a transaction measurement.
            if (transactionData != null) {
                transactionData.setResponseBody(e.toString());
                TaskQueue.queue(new HttpTransactionMeasurement(transactionData));
            }
        }
    }
}
