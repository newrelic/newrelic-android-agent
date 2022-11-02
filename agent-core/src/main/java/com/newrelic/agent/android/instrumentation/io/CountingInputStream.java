/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.instrumentation.io;

import com.newrelic.agent.android.analytics.AnalyticsAttribute;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.Buffer;

public class CountingInputStream extends InputStream implements StreamCompleteListenerSource {

    private final InputStream impl;
    private final StreamCompleteListenerManager listenerManager;
    private final boolean enableBuffering;

    private ByteBuffer buffer;

    private long count = 0;

    private static final AgentLog log = AgentLogManager.getAgentLog();

    public CountingInputStream(final InputStream impl) throws IOException {
        this(impl, false);
    }

    public CountingInputStream(final InputStream impl, final boolean enableBuffering) throws IOException {
        this(impl, enableBuffering, AnalyticsAttribute.ATTRIBUTE_VALUE_MAX_LENGTH);
    }

    CountingInputStream(final InputStream impl, final boolean enableBuffering, int capacity) throws IOException {
        this(impl, (enableBuffering ? ByteBuffer.allocate(capacity) : null));
    }

    CountingInputStream(final InputStream impl, ByteBuffer byteBuffer) throws IOException {
        if (impl == null) {
            throw new IOException("CountingInputStream: input stream cannot be null");
        }

        this.impl = impl;
        this.buffer = byteBuffer;
        this.enableBuffering = (this.buffer != null);
        this.listenerManager = new StreamCompleteListenerManager();

        if (enableBuffering) {
            fillBuffer();
        }
    }

    public void addStreamCompleteListener(StreamCompleteListener streamCompleteListener) {
        listenerManager.addStreamCompleteListener(streamCompleteListener);
    }

    public void removeStreamCompleteListener(StreamCompleteListener streamCompleteListener) {
        listenerManager.removeStreamCompleteListener(streamCompleteListener);
    }

    @Override
    public int read() throws IOException {
        int n;

        if (enableBuffering) {
            synchronized (buffer) {
                if (bufferHasBytes(1)) {
                    n = readBuffer();
                    if (n >= 0) {
                        count++;
                    }
                    return n;
                }
            }
        }

        try {
            n = impl.read();
            if (n >= 0) {
                count++;
            } else {
                notifyStreamComplete();
            }
            return n;
        } catch (IOException e) {
            notifyStreamError(e);
            throw e;
        }
    }

    @Override
    public int read(byte[] b) throws IOException {
        int n = 0;
        int numBytesFromBuffer = 0;
        int inputBufferRemaining = b.length;


        if (enableBuffering) {
            synchronized (buffer) {
                // Does the buffer have enough bytes to fully satisfy the request?
                if (bufferHasBytes(inputBufferRemaining)) {
                    n = readBufferBytes(b);
                    if (n >= 0) {
                        count += n;
                    } else {
                        // What here?
                        throw new IOException("readBufferBytes failed");
                    }
                    return n;
                } else {
                    // No, it doesn't. Does it have enough for a partial read?
                    int remaining = buffer.remaining();
                    if (remaining > 0) {
                        // We will do a partial read from buffer, and the rest from the real stream.
                        numBytesFromBuffer = readBufferBytes(b, 0, remaining);
                        if (numBytesFromBuffer < 0)
                            throw new IOException("partial read from buffer failed");
                        inputBufferRemaining -= numBytesFromBuffer;
                        count += numBytesFromBuffer;
                    }
                }
            }
        }

        try {
            n = impl.read(b, numBytesFromBuffer, inputBufferRemaining);
            if (n >= 0) {
                count += n;
                return n + numBytesFromBuffer;
            } else {
                if (numBytesFromBuffer <= 0) {
                    notifyStreamComplete();
                    return n;
                } else
                    return numBytesFromBuffer;
            }
        } catch (IOException e) {
            log.error(e.toString());
            System.out.println("NOTIFY STREAM ERROR: " + e);
            e.printStackTrace();
            notifyStreamError(e);
            throw e;
        }
    }

    public int read(byte[] b, int off, int len) throws IOException {
        int n = 0;
        int numBytesFromBuffer = 0;
        int inputBufferRemaining = len;

        if (enableBuffering) {
            synchronized (buffer) {
                // Does the buffer have enough bytes to fully satisfy the request?
                if (bufferHasBytes(inputBufferRemaining)) {
                    n = readBufferBytes(b, off, len);
                    if (n >= 0) {
                        count += n;
                    } else {
                        // What here?
                        throw new IOException("readBufferBytes failed");
                    }
                    return n;
                } else {
                    // No, it doesn't. Does it have enough for a partial read?
                    int remaining = buffer.remaining();
                    if (remaining > 0) {
                        // We will do a partial read from buffer, and the rest from the real stream.
                        numBytesFromBuffer = readBufferBytes(b, off, remaining);
                        if (numBytesFromBuffer < 0)
                            throw new IOException("partial read from buffer failed");
                        inputBufferRemaining -= numBytesFromBuffer;
                        count += numBytesFromBuffer;
                    }
                }
            }
        }

        try {
            n = impl.read(b, off + numBytesFromBuffer, inputBufferRemaining);
            if (n >= 0) {
                count += n;
                return n + numBytesFromBuffer;
            } else {
                if (numBytesFromBuffer <= 0) {
                    notifyStreamComplete();
                    return n;
                } else
                    return numBytesFromBuffer;
            }
        } catch (IOException e) {
            notifyStreamError(e);
            throw e;
        }
    }

    public long skip(long byteCount) throws IOException {
        long toSkip = byteCount;

        if (enableBuffering) {
            synchronized (buffer) {
                if (bufferHasBytes(byteCount)) {
                    buffer.position((int) byteCount);
                    count += byteCount;
                    return byteCount;
                } else {
                    // Partial?
                    toSkip = byteCount - buffer.remaining();
                    if (toSkip > 0) {
                        buffer.position(buffer.remaining());
                    } else
                        throw new IOException("partial read from buffer (skip) failed");
                }
            }
        }

        try {
            long n = impl.skip(toSkip);
            count += n;
            return n;
        } catch (IOException e) {
            notifyStreamError(e);
            throw e;
        }
    }

    public int available() throws IOException {
        int remaining = 0;

        if (enableBuffering) {
            remaining = buffer.remaining();
        }

        try {
            return remaining + impl.available();
        } catch (IOException e) {
            notifyStreamError(e);
            throw e;
        }
    }

    public void close() throws IOException {
        try {
            impl.close();
            notifyStreamComplete();
        } catch (IOException e) {
            notifyStreamError(e);
            throw e;
        } catch (Exception e) {
            // in this case, code in a StreamComplete handler
            // has thrown, so just log it and move on
            log.error(e.getLocalizedMessage());
        }
    }

    public void mark(int readlimit) {
        if (!markSupported())
            return;
        impl.mark(readlimit);
    }

    public boolean markSupported() {
        return impl.markSupported();
    }

    public void reset() throws IOException {
        if (!markSupported())
            return;

        try {
            impl.reset();
        } catch (IOException e) {
            notifyStreamError(e);
            throw e;
        }
    }

    private int readBuffer() {
        if (bufferEmpty())
            return -1;
        return buffer.get();
    }

    private int readBufferBytes(byte[] bytes) {
        return readBufferBytes(bytes, 0, bytes.length);
    }

    private int readBufferBytes(byte[] bytes, int offset, int length) {
        if (bufferEmpty())
            return -1;

        int remainingBefore = buffer.remaining();
        buffer.get(bytes, offset, length);
        return remainingBefore - buffer.remaining();
    }

    private boolean bufferHasBytes(long num) {
        return buffer.remaining() >= num;
    }

    private boolean bufferEmpty() {
        if (buffer.hasRemaining()) {
            return false;
        }
        return true;
    }

    public void fillBuffer() {
        if (buffer != null) {
            if (!buffer.hasArray()) {
                return;
            }
            int bytesRead = 0;
            synchronized (buffer) {
                try {
                    while (bytesRead < buffer.capacity()) {
                        int readCnt = 0;
                        readCnt = impl.read(buffer.array(), bytesRead, buffer.capacity() - bytesRead);
                        if (readCnt <= 0) {
                            break;
                        }
                        bytesRead += readCnt;
                    }
                    buffer.limit(bytesRead);
                } catch (NoSuchMethodError e) {
                    log.error(e.toString());
                    try {
                        ((Buffer) buffer).limit(bytesRead);
                    } catch (NoSuchMethodError e1) {
                        log.error(e1.toString());
                        try {
                            // make a copy with known length
                            buffer = ByteBuffer.wrap(buffer.array(), 0, bytesRead);
                        } catch (IndexOutOfBoundsException e2) {
                            log.error(e2.toString());
                        }
                    }
                } catch (Exception e) {
                    log.error(e.getLocalizedMessage());
                }
            }
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

    public String getBufferAsString() {
        if (buffer != null) {
            synchronized (buffer) {
                byte[] buf = new byte[buffer.limit()];
                for (int i = 0; i < buffer.limit(); i++) {
                    buf[i] = buffer.get(i);
                }
                return new String(buf);
            }
        } else {
            return "";
        }
    }
}