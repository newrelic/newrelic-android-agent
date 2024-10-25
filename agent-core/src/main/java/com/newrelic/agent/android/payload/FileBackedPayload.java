/*
 * Copyright (c) 2024. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.payload;

import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.util.Streams;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPOutputStream;

/**
 * FileBackedPayload is an implementation of Payload that encodes a data store
 * filename as its payload data, rather than bytes of the data itself. UUID is overloaded to
 * contain the filename as well.
 * <p>
 * Requests to getBytes() will then return the contents of the file as UTF-8 encoded bytes.
 * Payload data is persisted all-or-none. No edits or updates are supported.
 * <p>
 * Requests to putBytes(byte[]) will fill the file with the passed payload data.
 */

public class FileBackedPayload extends Payload {

    boolean isCompressed = false;

    public FileBackedPayload(File payloadFile) {
        super(payloadFile.getAbsolutePath().getBytes(StandardCharsets.UTF_8));
        this.uuid = payloadFile.getAbsolutePath();
        if (payloadFile.exists()) {
            this.timestamp = payloadFile.lastModified();
            this.isPersistable = payloadFile.canWrite();
        }
    }

    /**
     * Return the contents of the backing file as a byte array.
     * <p>
     * Beware of file size limitations.
     *
     * @return Byte array containing the file data, an empty array on failure
     */
    @Override
    public byte[] getBytes() {
        try {
            File payloadFile = payloadFile();
            if (payloadFile.exists()) {
                return Streams.readAllBytes(payloadFile);
            }
        } catch (IOException e) {
            AgentLogManager.getAgentLog().error("FileBackedPayload: failed to read payload. " + e);
        }

        return "".getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Creates a File instance from the pathname data stored in the payload
     *
     * @return
     */
    protected File payloadFile() {
        return new File(uuid);
    }

    @Override
    public void putBytes(byte[] payloadBytes) {
        if (isPersisted()) {
            try (BufferedWriter writer = Streams.newBufferedFileWriter(payloadFile())) {
                writer.write(new String(payloadBytes, StandardCharsets.UTF_8));
                writer.flush();
            } catch (IOException e) {
                AgentLogManager.getAgentLog().error("FileBackedPayload: failed to write payload to backing file." + e);
            }
        } else {
            super.putBytes(payloadBytes);
        }
    }

    @Override
    public long getTimestamp() {
        return payloadFile().lastModified();
    }

    @Override
    public void setPersisted(boolean isPersistable) {
        if (!isPersistable) {
            payloadFile().delete();
        }
    }

    /**
     * Returns the size of the payload (file). However, the max capacity of a ByteBuffer
     * is 0x7fffffff (2^31-1), while a File length is 0x7fffffffffffffffL (2^53-1);
     *
     * @return Minimum of file size or Integer.MAX_VALUE.
     */
    @Override
    public long size() {
        File payloadFile = payloadFile();
        return payloadFile.exists() ? payloadFile.length() : 0;
    }

    /**
     * GZIP compress the payload file.
     *
     * @param payloadFile File to be compressed
     * @param replace     Delete the passed file, rename the compressed file to the pass file name
     * @return File of new compressed file
     * @throws IOException
     */
    File compress(final File payloadFile, boolean replace) throws IOException {
        File compressedFile = new File(payloadFile.getAbsolutePath() + ".gz");

        try (FileInputStream fis = new FileInputStream(payloadFile);
             FileOutputStream fos = new FileOutputStream(compressedFile);
             GZIPOutputStream gzOut = new GZIPOutputStream(fos, Streams.DEFAULT_BUFFER_SIZE, true)) {

            Streams.copy(fis, gzOut);
            gzOut.flush();

            isCompressed = true;

            if (replace && payloadFile.delete()) {
                if (compressedFile.renameTo(payloadFile)) {
                    compressedFile = payloadFile;
                }
            }
        }

        return compressedFile;
    }

}
