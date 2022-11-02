/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.instrumentation.io;

import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;

import java.io.IOException;
import java.io.OutputStream;


public class CountingOutputStream extends OutputStream implements StreamCompleteListenerSource {
    private static final AgentLog log = AgentLogManager.getAgentLog();
    private final OutputStream impl;
    private long count = 0;
    private final StreamCompleteListenerManager listenerManager = new StreamCompleteListenerManager();

    public CountingOutputStream(final OutputStream impl) throws IOException {
        if (impl == null) {
            throw new IOException("CountingOutputStream: output stream cannot be null");
        }
        this.impl = impl;
    }

    public void addStreamCompleteListener(StreamCompleteListener streamCompleteListener) {
        listenerManager.addStreamCompleteListener(streamCompleteListener);
    }

    public void removeStreamCompleteListener(StreamCompleteListener streamCompleteListener) {
        listenerManager.removeStreamCompleteListener(streamCompleteListener);
    }

    public long getCount() {
        return count;
    }

    @Override
    public void write(int oneByte) throws IOException {
        try {
            impl.write(oneByte);
            count++;
        } catch (IOException e) {
            notifyStreamError(e);
            throw e;
        }
    }

    @Override
    public void write(byte[] buffer) throws IOException {
        try {
            impl.write(buffer);
            count += buffer.length;
        } catch (IOException e) {
            notifyStreamError(e);
            throw e;
        }
    }

    @Override
    public void write(byte[] buffer, int offset, int count) throws IOException {
        try {
            impl.write(buffer, offset, count);
            this.count += count;
        } catch (IOException e) {
            notifyStreamError(e);
            throw e;
        }
    }

    @Override
    public void flush() throws IOException {
        try {
            impl.flush();
        } catch (IOException e) {
            notifyStreamError(e);
            throw e;
        }
    }

    @Override
    public void close() throws IOException {
        try {
            impl.close();
            notifyStreamComplete();
        } catch (IOException e) {
            notifyStreamError(e);
            throw e;
		}
		catch (Exception e) {
            // in this case, code in a StreamComplete handler
            // has thrown, so just log it and move on
            log.warning(e.toString());
        }
    }

    private void notifyStreamComplete() {
        if (!listenerManager.isComplete()) {
            listenerManager.notifyStreamComplete(new StreamCompleteEvent(this, count));
        }
    }

    private void notifyStreamError(Exception e) {
        if (!listenerManager.isComplete()) {
            listenerManager.notifyStreamError(new StreamCompleteEvent(this, count, e));
        }
    }
}
