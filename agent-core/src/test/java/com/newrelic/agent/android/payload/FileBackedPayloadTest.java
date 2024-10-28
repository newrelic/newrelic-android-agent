/*
 * Copyright (c) 2024. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.payload;

import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.logging.ConsoleAgentLog;
import com.newrelic.agent.android.util.Streams;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class FileBackedPayloadTest {

    private static File reportsDir;
    private File dataFile;
    private String data;
    private FileBackedPayload payload;

    @BeforeClass
    public static void beforeClass() throws Exception {
        reportsDir = Files.createTempDirectory("FileBackedPayload-").toFile();
        reportsDir.mkdirs();

        AgentLogManager.setAgentLog(new ConsoleAgentLog());
        AgentLogManager.getAgentLog().setLevel(AgentLog.DEBUG);
    }

    @AfterClass
    public static void afterClass() throws Exception {
        Assert.assertTrue(reportsDir.delete());
    }

    @Before
    public void setUp() throws Exception {
        dataFile = File.createTempFile("payload-", ".dat", reportsDir);
        data = Streams.slurpString(dataFile, StandardCharsets.UTF_8.toString());
        payload = new FileBackedPayload(dataFile);
    }

    @After
    public void tearDown() throws Exception {
        Assert.assertTrue(dataFile.delete());
    }

    @Test
    public void getBytes() throws IOException {
        Assert.assertEquals(0, payload.size());
        seedData(dataFile);
        Assert.assertNotEquals(0, payload.size());
        Assert.assertEquals(dataFile.length(), payload.size());
    }

    @Test
    public void payloadFile() {
        Assert.assertEquals(dataFile, payload.payloadFile());
        Assert.assertEquals(dataFile.getAbsolutePath(), payload.getUuid());
    }

    @Test
    public void putBytes() {
        Assert.assertEquals(0, dataFile.length());
        Assert.assertEquals(0, payload.size());
        payload.putBytes(data.getBytes(StandardCharsets.UTF_8));
        Assert.assertEquals(dataFile.length(), payload.size());
    }

    @Test
    public void getTimestamp() throws IOException {
        try (OutputStream os = new FileOutputStream(dataFile)) {
            FileBackedPayloadTest.class.getResourceAsStream("/logReporting/logdata-vortex-413.rollup").transferTo(os);
        }
        Assert.assertEquals(dataFile.lastModified(), payload.getTimestamp());
    }

    @Test
    public void setPersisted() {
        Assert.assertTrue(payload.isPersisted());

        dataFile.setReadOnly();
        payload = new FileBackedPayload(dataFile);
        Assert.assertFalse(payload.isPersisted());
    }

    @Test
    public void size() {
        Assert.assertEquals(dataFile.length(), payload.size());
    }

    @Test
    public void compress() throws IOException {
        seedData(dataFile);
        long preSize = dataFile.length();
        File compressedFile = payload.compress(dataFile, false);
        Assert.assertTrue(compressedFile.getAbsolutePath().endsWith(".gz"));

        long postSize = compressedFile.length();
        Assert.assertTrue(preSize > postSize);
        AgentLogManager.getAgentLog().info("File compressed to " + (int) ((double) postSize / (double) preSize * 100.) + "% of original");
        compressedFile.delete();

        compressedFile = payload.compress(dataFile, true);
        Assert.assertEquals(compressedFile.getAbsolutePath(), dataFile.getAbsolutePath());
    }

    private File seedData(File file) throws IOException {
        try (OutputStream os = new FileOutputStream(file)) {
            FileBackedPayloadTest.class.getResourceAsStream("/logReporting/logdata-vortex-413.rollup").transferTo(os);
        }

        return file;
    }
}