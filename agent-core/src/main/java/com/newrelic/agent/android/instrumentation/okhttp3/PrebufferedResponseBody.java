/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.instrumentation.okhttp3;

import java.io.IOException;

import okhttp3.MediaType;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSource;

public class PrebufferedResponseBody extends ResponseBody {
    private final ResponseBody impl;
    private final BufferedSource source;
    private final long contentLength;

    public PrebufferedResponseBody(ResponseBody impl) {
        BufferedSource source = impl.source();

        if (impl.contentLength() == -1) {
            final Buffer buffer = new Buffer();
            try {
                source.readAll(buffer);
                source = buffer;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        this.impl = impl;
        this.source = source;
        this.contentLength = impl.contentLength() >= 0 ? impl.contentLength() : source.buffer().size();
    }

    @Override
    public MediaType contentType() {
        return impl.contentType();
    }

    @Override
    public long contentLength() {
        long contentLength = impl.contentLength();
        switch ((int) contentLength) {
            case -1:
                // contentLength == -1 means the source hasn't been read yet
                // so return that to caller
                break;
            case 0:
                // if the impl buffer is empty (has been exhausted),
                // return length of current source buffer (could also be 0)
                contentLength = source.buffer().size();
                break;
        }

        return contentLength;
    }
    @Override
    public BufferedSource source() {
        return source;
    }

    @Override
    public void close() {
        impl.close();
    }
}
