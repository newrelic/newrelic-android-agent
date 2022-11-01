/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class Streams {
    public static final int DEFAULT_BUFFER_SIZE = 1024 * 8;

    private Streams() {
    }

    public static int copy(InputStream input, OutputStream output) throws IOException {
        return copy(input, output, Streams.DEFAULT_BUFFER_SIZE, false);
    }

    public static int copy(InputStream input, OutputStream output, boolean closeStreams) throws IOException {
        return copy(input, output, Streams.DEFAULT_BUFFER_SIZE, closeStreams);
    }

    public static int copy(InputStream input, OutputStream output, int bufferSize) throws IOException {
        return copy(input, output, bufferSize, false);
    }

    public static int copy(InputStream input, OutputStream output, int bufferSize, boolean closeStreams) throws IOException {
        try {
            byte[] buffer = new byte[bufferSize];
            int count = 0;
            int n;
            while (-1 != (n = input.read(buffer))) {
                output.write(buffer, 0, n);
                count += n;
            }
            return count;
        } finally {
            if (closeStreams) {
                input.close();
                output.close();
            }
        }
    }

    public static byte[] slurpBytes(final InputStream in) throws IOException {
        try (final ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            copy(in, out);
            out.flush();
            return out.toByteArray();
        }
    }

    public static String slurp(final InputStream in, final String encoding) throws IOException {
        final byte[] bytes = slurpBytes(in);
        return new String(bytes, encoding);
    }

}
