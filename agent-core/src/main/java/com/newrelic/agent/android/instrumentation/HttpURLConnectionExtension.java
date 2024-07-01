/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.instrumentation;

import com.newrelic.agent.android.TaskQueue;
import com.newrelic.agent.android.api.common.TransactionData;
import com.newrelic.agent.android.instrumentation.io.CountingInputStream;
import com.newrelic.agent.android.instrumentation.io.CountingOutputStream;
import com.newrelic.agent.android.instrumentation.io.StreamCompleteEvent;
import com.newrelic.agent.android.instrumentation.io.StreamCompleteListener;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.measurement.HttpTransactionMeasurement;
import com.newrelic.agent.android.util.Constants;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;
import java.security.Permission;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

//
// HttpURLConnection doesn't really provide a clear distinction between request &
// response, so we mostly try to guess.
//
// As near as I can tell, the request is nearly always triggered by
// getInputStream/getOutputStream calls in practice on a JRE (even in
// the case where you're calling things like getResponseCode), but I'm
// being a little extra paranoid to be safe in case it doesn't hold
// true for Android. This may mean multiple calls to setBytes{Sent,Received}
// but I don't think it should otherwise affect us.
//
public class HttpURLConnectionExtension extends HttpURLConnection {
    private final HttpURLConnection impl;
    private TransactionState transactionState;
    private CountingInputStream errorStream = null;

    private static final AgentLog log = AgentLogManager.getAgentLog();

    public HttpURLConnectionExtension(final HttpURLConnection impl) {
        super(impl.getURL());
        this.impl = impl;
        transactionState = getTransactionState();

        TransactionStateUtil.setCrossProcessHeader(impl);
        TransactionStateUtil.setTrace(transactionState);
        TransactionStateUtil.setDistributedTraceHeaders(transactionState, impl);
    }

    @Override
    public void addRequestProperty(String field, String newValue) {
        impl.addRequestProperty(field, newValue);
        TransactionStateUtil.addHeadersAsCustomAttribute(transactionState, field, newValue);
    }

    @Override
    public void disconnect() {
        //
        // Ensure we don't create a new TransactionState if one doesn't already exist.
        //
        if (transactionState != null && !transactionState.isComplete()) {
            addTransactionAndErrorData(transactionState);
        }
        impl.disconnect();
    }

    @Override
    public boolean usingProxy() {
        return impl.usingProxy();
    }

    @Override
    public void connect() throws IOException {
        //
        // Initialize the transaction state since we're probably about to send a request.
        //
        // XXX N.B. impl.connect() may be called by e.g. getInputStream()/getOutputStream(),
        //     and we'll never know about it. That's okay.
        //
        getTransactionState();

        try {
            impl.connect();
        } catch (IOException e) {
            error(e);
            throw e;
        }
    }

    @Override
    public boolean getAllowUserInteraction() {
        return impl.getAllowUserInteraction();
    }

    @Override
    public int getConnectTimeout() {
        return impl.getConnectTimeout();
    }

    @Override
    public Object getContent() throws IOException {
        //
        // XXX don't think we can wrap this one properly unless we have a content length.
        //
        getTransactionState();
        final Object object;
        try {
            object = impl.getContent();
        } catch (IOException e) {
            error(e);
            throw e;
        }
        final int contentLength = impl.getContentLength();
        if (contentLength >= 0) {
            final TransactionState transactionState = getTransactionState();
            if (!transactionState.isComplete()) {
                transactionState.setBytesReceived(contentLength);
                addTransactionAndErrorData(transactionState);
            }
        }
        return object;
    }

    @Override
    public Object getContent(@SuppressWarnings("rawtypes") Class[] types) throws IOException {
        //
        // XXX don't think we can wrap this one properly unless we have a content length.
        //
        getTransactionState();
        final Object object;
        try {
            object = impl.getContent(types);
        } catch (IOException e) {
            error(e);
            throw e;
        }
        checkResponse();
        return object;
    }

    @Override
    public String getContentEncoding() {
        getTransactionState();
        final String contentEncoding = impl.getContentEncoding();
        checkResponse();
        return contentEncoding;
    }

    @Override
    public int getContentLength() {
        getTransactionState();
        final int contentLength = impl.getContentLength();
        checkResponse();
        return contentLength;
    }

    @Override
    public String getContentType() {
        getTransactionState();
        final String contentType = impl.getContentType();
        checkResponse();
        return contentType;
    }

    @Override
    public long getDate() {
        getTransactionState();
        final long date = impl.getDate();
        checkResponse();
        return date;
    }

    @Override
    public InputStream getErrorStream() {
        getTransactionState();
        try {
            if (errorStream == null || errorStream.available() == 0) {
                errorStream = new CountingInputStream(impl.getErrorStream(), true);
            }
        } catch (Exception e) {
            log.error("HttpsURLConnectionExtension: " + e);
            return impl.getErrorStream();
        }
        return errorStream;
    }

    @Override
    public long getHeaderFieldDate(String field, long defaultValue) {
        getTransactionState();
        final long date = impl.getHeaderFieldDate(field, defaultValue);
        checkResponse();
        return date;
    }

    @Override
    public boolean getInstanceFollowRedirects() {
        return impl.getInstanceFollowRedirects();
    }

    @Override
    public Permission getPermission() throws IOException {
        return impl.getPermission();
    }

    @Override
    public String getRequestMethod() {
        return impl.getRequestMethod();
    }

    @Override
    public int getResponseCode() throws IOException {
        getTransactionState();
        final int responseCode;
        try {
            responseCode = impl.getResponseCode();
        } catch (IOException e) {
            error(e);
            throw e;
        }
        checkResponse();
        return responseCode;
    }

    @Override
    public String getResponseMessage() throws IOException {
        getTransactionState();
        final String message;
        try {
            message = impl.getResponseMessage();
        } catch (IOException e) {
            error(e);
            throw e;
        }
        checkResponse();
        return message;
    }

    @Override
    public void setChunkedStreamingMode(int chunkLength) {
        impl.setChunkedStreamingMode(chunkLength);
    }

    @Override
    public void setFixedLengthStreamingMode(int contentLength) {
        impl.setFixedLengthStreamingMode(contentLength);
    }

    @Override
    public void setInstanceFollowRedirects(boolean followRedirects) {
        impl.setInstanceFollowRedirects(followRedirects);
    }

    @Override
    public void setRequestMethod(String method) throws ProtocolException {
        getTransactionState();
        try {
            impl.setRequestMethod(method);
        } catch (ProtocolException e) {
            error(e);
            throw e;
        }
    }

    @Override
    public boolean getDefaultUseCaches() {
        return impl.getDefaultUseCaches();
    }

    @Override
    public boolean getDoInput() {
        return impl.getDoInput();
    }

    @Override
    public boolean getDoOutput() {
        return impl.getDoOutput();
    }

    @Override
    public long getExpiration() {
        getTransactionState();
        final long expiration = impl.getExpiration();
        checkResponse();
        return expiration;
    }

    @Override
    public String getHeaderField(int pos) {
        getTransactionState();
        final String header = impl.getHeaderField(pos);
        checkResponse();
        return header;
    }

    @Override
    public String getHeaderField(String key) {
        getTransactionState();
        final String header = impl.getHeaderField(key);
        checkResponse();
        return header;
    }

    @Override
    public int getHeaderFieldInt(String field, int defaultValue) {
        getTransactionState();
        final int header = impl.getHeaderFieldInt(field, defaultValue);
        checkResponse();
        return header;
    }

    @Override
    public String getHeaderFieldKey(int posn) {
        getTransactionState();
        final String key = impl.getHeaderFieldKey(posn);
        checkResponse();
        return key;
    }

    @Override
    public Map<String, List<String>> getHeaderFields() {
        getTransactionState();
        final Map<String, List<String>> fields = impl.getHeaderFields();
        checkResponse();
        return fields;
    }

    @Override
    public long getIfModifiedSince() {
        getTransactionState();
        final long ifModifiedSince = impl.getIfModifiedSince();
        checkResponse();
        return ifModifiedSince;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        final TransactionState transactionState = getTransactionState();
        final CountingInputStream in;
        try {
            in = new CountingInputStream(impl.getInputStream());
            TransactionStateUtil.inspectAndInstrumentResponse(transactionState, impl);
        } catch (IOException e) {
            error(e);
            throw e;
        }
        in.addStreamCompleteListener(new StreamCompleteListener() {
            @Override
            public void streamError(StreamCompleteEvent e) {
                if (!transactionState.isComplete()) {
                    transactionState.setBytesReceived(e.getBytes());
                }
                error(e.getException());
            }

            @Override
            public void streamComplete(StreamCompleteEvent e) {
                if (!transactionState.isComplete()) {
                    try {
                        transactionState.setStatusCode(impl.getResponseCode());
                    } catch (IOException ioE) {
                        log.error("HttpURLConnectionExtension.getInputStream.streamComplete: " + e);
                    }
                    final long contentLength = impl.getContentLength();
                    long numBytes = e.getBytes();
                    if (contentLength >= 0) {
                        numBytes = contentLength;
                    }
                    transactionState.setBytesReceived(numBytes);
                    addTransactionAndErrorData(transactionState);
                }
            }
        });
        return in;
    }

    @Override
    public long getLastModified() {
        getTransactionState();
        final long lastModified = impl.getLastModified();
        checkResponse();
        return lastModified;
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        final TransactionState transactionState = getTransactionState();
        final CountingOutputStream out;
        try {
            out = new CountingOutputStream(impl.getOutputStream());
        } catch (IOException e) {
            error(e);
            throw e;
        }
        out.addStreamCompleteListener(new StreamCompleteListener() {
            @Override
            public void streamError(StreamCompleteEvent e) {
                if (!transactionState.isComplete()) {
                    transactionState.setBytesSent(e.getBytes());
                }
                error(e.getException());
            }

            @Override
            public void streamComplete(StreamCompleteEvent e) {
                if (!transactionState.isComplete()) {
                    try {
                        transactionState.setStatusCode(impl.getResponseCode());
                    } catch (IOException ioE) {
                        log.error("HttpURLConnectionExtension.getOutputStream.streamComplete: " + e);
                    }
                    final String header = impl.getRequestProperty(Constants.Network.CONTENT_LENGTH_HEADER);
                    long numBytes = e.getBytes();
                    if (header != null) {
                        try {
                            numBytes = Long.parseLong(header);
                        } catch (NumberFormatException ex) {
                            ex.printStackTrace();
                        }
                    }
                    transactionState.setBytesSent(numBytes);
                    addTransactionAndErrorData(transactionState);
                }
            }
        });
        return out;
    }

    @Override
    public int getReadTimeout() {
        return impl.getReadTimeout();
    }

    @Override
    public Map<String, List<String>> getRequestProperties() {
        return impl.getRequestProperties();
    }

    @Override
    public String getRequestProperty(String field) {
        return impl.getRequestProperty(field);
    }

    @Override
    public URL getURL() {
        return impl.getURL();
    }

    @Override
    public boolean getUseCaches() {
        return impl.getUseCaches();
    }

    @Override
    public void setAllowUserInteraction(boolean newValue) {
        impl.setAllowUserInteraction(newValue);
    }

    @Override
    public void setConnectTimeout(int timeoutMillis) {
        impl.setConnectTimeout(timeoutMillis);
    }

    @Override
    public void setDefaultUseCaches(boolean newValue) {
        impl.setDefaultUseCaches(newValue);
    }

    @Override
    public void setDoInput(boolean newValue) {
        impl.setDoInput(newValue);
    }

    @Override
    public void setDoOutput(boolean newValue) {
        impl.setDoOutput(newValue);
    }

    @Override
    public void setIfModifiedSince(long newValue) {
        impl.setIfModifiedSince(newValue);
    }

    @Override
    public void setReadTimeout(int timeoutMillis) {
        impl.setReadTimeout(timeoutMillis);
    }

    @Override
    public void setRequestProperty(String field, String newValue) {
        impl.setRequestProperty(field, newValue);
        TransactionStateUtil.addHeadersAsCustomAttribute(transactionState, field, newValue);
    }

    @Override
    public void setUseCaches(boolean newValue) {
        impl.setUseCaches(newValue);
    }

    @Override
    public String toString() {
        return impl.toString();
    }

    private void checkResponse() {
        if (!getTransactionState().isComplete()) {
            TransactionStateUtil.inspectAndInstrumentResponse(getTransactionState(), impl);
        }
    }

    TransactionState getTransactionState() {
        if (transactionState == null) {
            transactionState = new TransactionState();
            TransactionStateUtil.inspectAndInstrument(transactionState, impl);
        }
        return transactionState;
    }

    void error(final Exception e) {
        final TransactionState transactionState = getTransactionState();
        TransactionStateUtil.setErrorCodeFromException(transactionState, e);
        if (!transactionState.isComplete()) {
            TransactionStateUtil.inspectAndInstrumentResponse(transactionState, impl);
            final TransactionData transactionData = transactionState.end();

            // If no transaction data is available, don't record a transaction measurement.
            if (transactionData != null) {
                String responseBody = e.toString();
                try {
                    InputStream errorStream = getErrorStream();
                    if ((errorStream != null) && errorStream instanceof CountingInputStream) {
                        responseBody = ((CountingInputStream) errorStream).getBufferAsString();
                    }
                } catch (Exception e1) {
                    log.error("HttpsURLConnectionExtension.error: " + e1);
                }

                transactionData.setResponseBody(responseBody);
                TaskQueue.queue(new HttpTransactionMeasurement(transactionData));
            }
        }
    }

    void addTransactionAndErrorData(TransactionState transactionState) {
        final TransactionData transactionData = transactionState.end();

        // If no transaction data is available, bail out.
        if (transactionData == null) {
            return;
        }

        if (transactionState.isErrorOrFailure()) {
            String responseBody = "";

            try {
                InputStream errorStream = getErrorStream();
                if (errorStream instanceof CountingInputStream) {
                    responseBody = ((CountingInputStream) errorStream).getBufferAsString();
                }
            } catch (Exception e) {
                log.error("HttpURLConnectionExtension.addTransactionAndErrorData: " + e);
            }


            // If there is a Content-Type header present, add it to the error param map.
            final Map<String, String> params = new TreeMap<String, String>();
            final String contentType = impl.getContentType();

            if (contentType != null && !"".equals(contentType)) {
                params.put(Constants.Transactions.CONTENT_TYPE, contentType);
            }

            /* Also add Content-Length. TransactionState bytesReceived is set directly
             * from the Content-Length header in this error case.
             */
            params.put(Constants.Transactions.CONTENT_LENGTH, transactionState.getBytesReceived() + "");

            transactionData.setResponseBody(responseBody);

            transactionData.getParams().putAll(params);

        }

        TaskQueue.queue(new HttpTransactionMeasurement(transactionData));
    }
}
