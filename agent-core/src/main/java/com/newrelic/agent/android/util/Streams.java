/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.stream.Stream;

public class Streams {
    public static final int DEFAULT_BUFFER_SIZE = 1024 * 64;

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

    public static String slurpString(final InputStream in) throws IOException {
        final byte[] bytes = slurpBytes(in);
        return new String(bytes);
    }

    public static String slurpString(final InputStream in, final String encoding) throws IOException {
        final byte[] bytes = slurpBytes(in);
        return new String(bytes, encoding);
    }

    /**
     * JDK 11 supports java.nio Path, Paths and Files classes, but they were not added to Android
     * until SDK 26. The agent's minSdk is currently 24, so provide wrappers until that changes.
     *
     * @see <a href="https://developer.android.com/reference/java/nio/file/Path">Path</a>
     * @see <a href="https://developer.android.com/reference/java/nio/file/Paths">Paths</a>
     * @see <a href="https://developer.android.com/reference/java/nio/file/Files">Files</a>
     */

    /**
     * Returns contents of file as array of strings.
     *
     * @param file
     * @return
     * @throws IOException
     */
    public static String[] readAllLines(File file) throws IOException {
        String[] lines = {};

        try (BufferedReader br = Streams.newBufferedFileReader(file)) {
            lines = br.lines().map(Object::toString).toArray(String[]::new);
        }

        return lines;
    }

    /**
     * Returns contents of file as stream of strings.
     *
     * @param file
     * @return
     * @throws IOException
     */
    public static Stream<String> lines(File file) throws IOException {
        BufferedReader br = new BufferedReader(new FileReader(file));
        return br.lines();
    }

    /**
     * Return contents of file as a byte array.
     *
     * @param file
     * @return Byte array
     * @throws IOException
     */
    public static byte[] readAllBytes(File file) throws IOException {
        return slurpBytes(new FileInputStream(file));
    }

    /**
     * Returns buffer reader for passed file. Encoding is not supported in Android 24.
     *
     * @param file
     * @return BufferedFileStream instance (closeable)
     * @throws IOException
     */
    public static BufferedReader newBufferedFileReader(File file) throws IOException {
        return new BufferedReader(new FileReader(file));
    }

    /**
     * Returns buffer reader for passed file. Encoding is not supported in Android 24.
     *
     * @param file
     * @return BufferedFileStream instance (closeable)
     * @throws IOException
     */
    public static BufferedWriter newBufferedFileWriter(File file) throws IOException {
        return new BufferedWriter(new FileWriter(file));
    }

    /**
     * Returns the non-recursive contents of a directory as a stream of Files.
     *
     * @param rootDir
     * @return Stream of files contained in passed directory
     * @throws IOException
     */
    public static Stream<File> listFiles(File rootDir) throws IOException {
        return Arrays.stream(rootDir.listFiles());
    }

    /**
     * Returns the non-recursive filtered contents of a directory as a stream of Files.
     *
     * @param rootDir
     * @return Stream of files contained in passed directory
     * @throws IOException
     */
    public static Stream<File> listFiles(File rootDir, FilenameFilter filter) throws IOException {
        return Arrays.stream(rootDir.listFiles(filter));
    }

}