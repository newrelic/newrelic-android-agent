/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.instrumentation.okhttp2;

import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.ResponseBody;

import java.io.IOException;

import okio.BufferedSource;

public class PrebufferedResponseBody extends ResponseBody {
    ResponseBody impl;
    private BufferedSource source;

    public PrebufferedResponseBody(ResponseBody impl, BufferedSource source) {
        this.impl = impl;
        this.source = source;
    }

    @Override
    public MediaType contentType() {
        return impl.contentType();
    }

    @Override
    public long contentLength() {
        long contentLength = 0;
        try {
            contentLength = impl.contentLength();
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
        } catch (IOException e) {
            // if unknown, return buffer size
            contentLength = source.buffer().size();
        }
        return contentLength;
    }

    @Override
    public BufferedSource source() {
        return source;
    }

    @Override
    public void close() throws IOException {
        impl.close();
    }
}
