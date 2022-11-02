/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.util;

import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.Deflater;

public class Deflator {
    private final static AgentLog log = AgentLogManager.getAgentLog();

    // Apply deflate encoding to a message.
    @SuppressWarnings("NewApi")
    public static byte[] deflate(byte[] messageBytes) {
        final int DEFLATE_BUFFER_SIZE = 8192;

        Deflater deflater = new Deflater();
        try {
            deflater.setInput(messageBytes);
            deflater.finish();

            try ( ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                byte[] buf = new byte[DEFLATE_BUFFER_SIZE];
                while (!deflater.finished()) {
                    int byteCount = deflater.deflate(buf);
                    if (byteCount <= 0) {
                        log.error("HTTP request contains an incomplete payload");
                    }
                    baos.write(buf, 0, byteCount);
                }
                return baos.toByteArray();

            } catch (IOException e) {
            }
        } finally {
            deflater.end();
        }

        return messageBytes;
    }
}
