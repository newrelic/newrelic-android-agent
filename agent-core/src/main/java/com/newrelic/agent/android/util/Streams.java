/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
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

    /**
     * Byte array and Strings: the max capacity of a byte array int  0x7fffffff (2^31-1),
     * while a File length is 0x7fffffffffffffffL (2^53-1);
     */

    public static byte[] slurpBytes(final InputStream in) throws IOException {
        try (final ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            copy(in, out);
            out.flush();
            return out.toByteArray();
        }
    }

    /**
     * Return contents of an InputStream as an encoded String.
     *
     * @param in
     * @return String constructed of byte stream
     * @throws IOException
     */
    public static String slurpString(final InputStream in) throws IOException {
        return slurpString(in, StandardCharsets.UTF_8.name());
    }

    /**
     * Return contents of an input stream as a String.
     *
     * @param in InputStream
     * @param encoding StandardCharsets.UTF_8 if null
     * @return encoded String
     * @throws IOException
     */
    public static String slurpString(final InputStream in, final String encoding) throws IOException {
        return new String(slurpBytes(in), (null == encoding || encoding.isEmpty()) ? StandardCharsets.UTF_8.name() : encoding);
    }

    /**
     * Return contents of file as an encoded String.
     *
     * @param file File
     * @param encoding StandardCharsets.UTF_8 if null
     * @return encoded String
     * @throws IOException
     */
    public static String slurpString(File file, final String encoding) throws IOException {
        return slurpString(new FileInputStream(file), encoding);
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
     * @param rootDir Directory to search
     * @return Stream of files contained in passed directory
     * @throws IOException
     */
    public static Stream<File> list(File rootDir) throws IllegalArgumentException {
        return Streams.list(rootDir, pathname -> true);
    }

    /**
     * Returns the non-recursive filtered contents of a directory as a stream of Files.
     *
     * @param rootDir Directory to search
     * @param filter  FileFilter
     * @return Stream of files contained in passed directory
     * @throws IOException
     */
    public static Stream<File> list(File rootDir, FileFilter filter) throws IllegalArgumentException {
        if (null == rootDir) {
            throw new IllegalArgumentException("Invalid file argument: file is null");
        }

        if (!rootDir.exists()) {
            throw new IllegalArgumentException("Invalid file argument: file does not exist");
        }

        if (!rootDir.isDirectory()) {
            throw new IllegalArgumentException("Invalid file argument: file is not a directory");
        }

        if (null == filter) {
            filter = pathname -> false;
        }

        return Arrays.stream(rootDir.listFiles(filter));
    }
}