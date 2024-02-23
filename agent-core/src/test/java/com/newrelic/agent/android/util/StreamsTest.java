/*
 * Copyright (c) 2024. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.util;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class StreamsTest {
    private static final int TINY_BUFFER_SIZE = 128;
    private static final int EXPECTED_LINES = 25;
    private static final int EXPECTED_BYTES = 2385;

    private File testInputFile;
    private File testOutputFile;

    @Before
    public void setUp() throws Exception {
        testInputFile = new File(StreamsTest.class.getResource("/NetworkRequests.txt").getFile());
        testOutputFile = File.createTempFile("streamsTest", "txt");
    }

    @Test
    public void copy() throws IOException {
        Streams.copy(new FileInputStream(testInputFile), new FileOutputStream(testOutputFile));
        Assert.assertTrue(testOutputFile.exists()
                && testOutputFile.isFile()
                && testOutputFile.canWrite()
                && testOutputFile.length() == EXPECTED_BYTES);
    }

    @Test
    public void copyWithBufferSize() throws IOException {
        Streams.copy(new FileInputStream(testInputFile), new FileOutputStream(testOutputFile), TINY_BUFFER_SIZE);
        Assert.assertTrue(testOutputFile.exists()
                && testOutputFile.isFile()
                && testOutputFile.canWrite()
                && testOutputFile.length() == EXPECTED_BYTES);
    }

    @Test
    public void copyCloseable() throws IOException {
        Streams.copy(new FileInputStream(testInputFile), new FileOutputStream(testOutputFile), true);
        Assert.assertTrue(testOutputFile.exists()
                && testOutputFile.isFile()
                && testOutputFile.canWrite()
                && testOutputFile.length() == EXPECTED_BYTES);
    }

    @Test
    public void testCloseableWithBufferSize() throws IOException {
        Streams.copy(new FileInputStream(testInputFile), new FileOutputStream(testOutputFile), TINY_BUFFER_SIZE, true);
        Assert.assertTrue(testOutputFile.exists()
                && testOutputFile.isFile()
                && testOutputFile.canWrite()
                && testOutputFile.length() == EXPECTED_BYTES);
    }

    @Test
    public void slurpBytes() throws IOException {
        byte[] bytes = Streams.slurpBytes(new FileInputStream(testInputFile));
        Assert.assertTrue(bytes.length == EXPECTED_BYTES);
    }

    @Test
    public void slurpString() throws IOException {
        String encodedString = Streams.slurpString(new FileInputStream(testInputFile));
        Assert.assertTrue(encodedString.length() > 0);
    }

    @Test
    public void slurpEncodedString() throws IOException {
        String encoding = StandardCharsets.UTF_8.toString();
        String encodedString = Streams.slurpString(new FileInputStream(testInputFile), encoding);
        Assert.assertTrue(encodedString.length() > 0);
        Assert.assertNotNull(encodedString.getBytes(encoding));
    }

    @Test
    public void slurpStringFromFile() throws IOException {
        String encoding = StandardCharsets.UTF_8.toString();
        String encodedString = Streams.slurpString(testInputFile, encoding);
        Assert.assertTrue(encodedString.length() > 0);
        Assert.assertNotNull(encodedString.getBytes(encoding));
    }

    @Test
    public void readAllLines() throws IOException {
        String[] lines = Streams.readAllLines(testInputFile);
        Assert.assertTrue(lines.length == EXPECTED_LINES);
    }

    @Test
    public void lines() throws IOException {
        Set<String> lines = Streams.lines(testInputFile).collect(Collectors.toSet());
        Assert.assertTrue(lines.size() == EXPECTED_LINES);
    }

    @Test
    public void readAllBytes() throws IOException {
        byte[] bytes = Streams.readAllBytes(testInputFile);
        Assert.assertTrue(bytes.length == EXPECTED_BYTES);
    }

    @Test
    public void newBufferedFileReader() throws IOException {
        try (BufferedReader bufferedFileReader = Streams.newBufferedFileReader(testInputFile)) {
            Assert.assertNotNull(bufferedFileReader);
            Assert.assertTrue(bufferedFileReader.ready());
            Assert.assertTrue(bufferedFileReader.lines() instanceof Stream);
            Assert.assertEquals(EXPECTED_LINES, bufferedFileReader.lines().count());
        }
    }

    @Test
    public void newBufferedFileWriter() throws IOException {
        try (BufferedWriter bufferedFileWriter = Streams.newBufferedFileWriter(testOutputFile)) {
            Assert.assertNotNull(bufferedFileWriter);
            bufferedFileWriter.append("nothing");
            bufferedFileWriter.newLine();
            bufferedFileWriter.close();
            Assert.assertEquals(1, Streams.newBufferedFileReader(testOutputFile).lines().count());
        }
    }

    @Test
    public void list() throws IOException {
        Set<File> fileSet = Streams.list(testInputFile.getParentFile()).collect(Collectors.toSet());
        Assert.assertFalse(fileSet.isEmpty());
        Assert.assertTrue(fileSet.contains(testInputFile));
    }

    @Test
    public void filteredList() throws IOException {
        Set<File> fileSet = Streams.list(testInputFile.getParentFile(),
                pathname -> pathname.equals(testInputFile)).collect(Collectors.toSet());
        Assert.assertFalse(fileSet.isEmpty());
        Assert.assertEquals("Should filter to 1 file", 1, fileSet.size());
        Assert.assertTrue(fileSet.contains(testInputFile));
    }

    @Test
    public void testInvalidList() {
        try {
            Streams.list(new File("")).collect(Collectors.toSet());
            Assert.fail("Should throw exception");
        } catch (IllegalArgumentException e) {
        }

        try {
            Streams.list(null).collect(Collectors.toSet());
            Assert.fail("Should throw exception");
        } catch (IllegalArgumentException e) {
        }

        try {
            Streams.list(new File("/dev/wtf")).collect(Collectors.toSet());
            Assert.fail("Should throw exception");
        } catch (IllegalArgumentException e) {
        }
    }

}