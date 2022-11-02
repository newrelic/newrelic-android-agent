/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.instrumentation.httpclient;

import com.newrelic.agent.android.Measurements;
import com.newrelic.agent.android.TaskQueue;
import com.newrelic.agent.android.api.common.TransactionData;
import com.newrelic.agent.android.instrumentation.TransactionState;
import com.newrelic.agent.android.instrumentation.TransactionStateUtil;
import com.newrelic.agent.android.instrumentation.io.CountingInputStream;
import com.newrelic.agent.android.instrumentation.io.CountingOutputStream;
import com.newrelic.agent.android.instrumentation.io.StreamCompleteEvent;
import com.newrelic.agent.android.instrumentation.io.StreamCompleteListener;
import com.newrelic.agent.android.instrumentation.io.StreamCompleteListenerSource;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.measurement.http.HttpTransactionMeasurement;
import com.newrelic.agent.android.util.Constants;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.entity.HttpEntityWrapper;
import org.apache.http.message.AbstractHttpMessage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.TreeMap;

@Deprecated
public final class HttpResponseEntityImpl implements HttpEntity, StreamCompleteListener {
    private final static String TRANSFER_ENCODING_HEADER = "Transfer-Encoding";
    private final static String ENCODING_CHUNKED = "chunked";

    private final HttpEntity impl;
    private final TransactionState transactionState;
    private final long contentLengthFromHeader;
    private CountingInputStream contentStream;
    private static final AgentLog log = AgentLogManager.getAgentLog();

    public HttpResponseEntityImpl(final HttpEntity impl, final TransactionState transactionState, final long contentLengthFromHeader) {
        this.impl = impl;
        this.transactionState = transactionState;
        this.contentLengthFromHeader = contentLengthFromHeader;
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
    public InputStream getContent() throws IOException,
            IllegalStateException {
        if (contentStream != null) {
            return contentStream;
        }
        try {
            boolean shouldBuffer = true;

            // If the response is chunked, don't try to pre-buffer it.
            if (impl instanceof AbstractHttpMessage) {
                final AbstractHttpMessage message = (AbstractHttpMessage) impl;
                final Header transferEncodingHeader = message.getLastHeader(TRANSFER_ENCODING_HEADER);
                if (transferEncodingHeader != null && ENCODING_CHUNKED.equalsIgnoreCase(transferEncodingHeader.getValue())) {
                    shouldBuffer = false;
                }
            } else {
                if (impl instanceof HttpEntityWrapper) {
                    HttpEntityWrapper entityWrapper = (HttpEntityWrapper) impl;
                    shouldBuffer = !entityWrapper.isChunked();
                }
            }

            try {
                contentStream = new CountingInputStream(impl.getContent(), shouldBuffer);
                contentStream.addStreamCompleteListener(this);
            } catch (IllegalArgumentException e) {
                log.error("HttpResponseEntityImpl: " + e.toString());
            }
            return contentStream;
        } catch (IOException e) {
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
        if (!transactionState.isComplete()) {
            CountingOutputStream outputStream = null;
            try {
                outputStream = new CountingOutputStream(outstream);
                impl.writeTo(outputStream);
            } catch (IOException e) {
                //
                // We don't have a StreamCompleteListener bound to the outputStream,
                // so we need to handle this explicitly.
                //
                if (outputStream != null) {
                    handleException(e, outputStream.getCount());
                }
                throw e;
            }

            if (!transactionState.isComplete()) {
                if (contentLengthFromHeader >= 0) {
                    transactionState.setBytesReceived(contentLengthFromHeader);
                } else {
                    transactionState.setBytesReceived(outputStream.getCount());
                }
                addTransactionAndErrorData(transactionState);
            }
        } else {
            impl.writeTo(outstream);
        }
    }

    @Override
    public void streamComplete(StreamCompleteEvent e) {
        final StreamCompleteListenerSource source = (StreamCompleteListenerSource) e.getSource();
        source.removeStreamCompleteListener(this);
        if (!transactionState.isComplete()) {
            if (contentLengthFromHeader >= 0) {
                transactionState.setBytesReceived(contentLengthFromHeader);
            } else {
                transactionState.setBytesReceived(e.getBytes());
            }
            addTransactionAndErrorData(transactionState);
        }
    }

    @Override
    public void streamError(StreamCompleteEvent e) {
        final StreamCompleteListenerSource source = (StreamCompleteListenerSource) e.getSource();
        source.removeStreamCompleteListener(this);
        TransactionStateUtil.setErrorCodeFromException(transactionState, e.getException());
        if (!transactionState.isComplete()) {
            transactionState.setBytesReceived(e.getBytes());
        }
    }

    private void addTransactionAndErrorData(TransactionState transactionState) {
        final TransactionData transactionData = transactionState.end();

        // If no transaction data is available, bail out.
        if (transactionData == null) {
            return;
        }

        if (transactionState.isErrorOrFailure()) {
            String responseBody = "";

            try {
                final InputStream errorStream = getContent();
                if (errorStream instanceof CountingInputStream) {
                    responseBody = ((CountingInputStream) errorStream).getBufferAsString();
                }
            } catch (Exception e) {
                log.error("HttpResponseEntityImpl: " + e);
            }

            Header contentType = impl.getContentType();

            // If there is a Content-Type header present, add it to the error param map.
            Map<String, String> params = new TreeMap<String, String>();
            if (contentType != null && contentType.getValue() != null && !"".equals(contentType.getValue())) {
                params.put(Constants.Transactions.CONTENT_TYPE, contentType.getValue());
            }

			/* Also add Content-Length. TransactionState bytesReceived is set directly
             * from the Content-Length header in this error case.
			*/
            params.put(Constants.Transactions.CONTENT_LENGTH, transactionState.getBytesReceived() + "");

            transactionData.setResponseBody(responseBody);
            transactionData.setParams(params);

        }

        TaskQueue.queue(new HttpTransactionMeasurement(transactionData));
    }

    protected void handleException(Exception e) {
        handleException(e, null);
    }

    protected void handleException(Exception e, Long streamBytes) {
        TransactionStateUtil.setErrorCodeFromException(transactionState, e);
        if (!transactionState.isComplete()) {
            if (streamBytes != null) {
                transactionState.setBytesReceived(streamBytes);
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
