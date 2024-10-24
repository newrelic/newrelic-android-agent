/*
 * Copyright (c) 2024. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.aei;

import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.logging.ConsoleAgentLog;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.UUID;

public class SessionMapperTest {

    private static File reportsDir;
    private File sessionMapperFile;
    private SessionMapper mapper;

    @BeforeClass
    public static void beforeClass() throws Exception {
        reportsDir = Files.createTempDirectory("LogReporting-").toFile();
        reportsDir.mkdirs();

        AgentLogManager.setAgentLog(new ConsoleAgentLog());
        AgentLogManager.getAgentLog().setLevel(AgentLog.DEBUG);
    }

    @Before
    public void setUp() throws Exception {
        sessionMapperFile = new File(reportsDir, "sessionMapper");
        mapper = new SessionMapper(sessionMapperFile);

        mapper.put(123, UUID.randomUUID().toString());
        mapper.put(234, UUID.randomUUID().toString());
        mapper.put(345, UUID.randomUUID().toString());
    }

    @After
    public void tearDown() throws Exception {
        sessionMapperFile.delete();
    }

    @AfterClass
    public static void afterClass() throws Exception {
        Assert.assertTrue(reportsDir.delete());
    }

    @Test
    public void put() {
        mapper.put(6661, AgentConfiguration.getInstance().getSessionID());
        Assert.assertEquals(AgentConfiguration.getInstance().getSessionID(), mapper.get(6661));
    }

    @Test
    public void get() {
        Assert.assertNotEquals("", mapper.get(234));
        Assert.assertEquals("", mapper.get(456));
    }

    @Test
    public void getOrDefault() {
        Assert.assertEquals("", mapper.get(456));
    }

    @Test
    public void load() {
        Assert.assertEquals(0, sessionMapperFile.length());
        mapper.flush();
        Assert.assertNotEquals(0, sessionMapperFile.length());
        mapper.clear();
        Assert.assertTrue(mapper.mapper.isEmpty());
        mapper.load();
        Assert.assertFalse(mapper.mapper.isEmpty());
    }

    @Test
    public void flush() {
        Assert.assertEquals(0, sessionMapperFile.length());
        mapper.flush();
        ;
        Assert.assertNotEquals(0, sessionMapperFile.length());
    }

    @Test
    public void clear() {
        Assert.assertFalse(mapper.mapper.isEmpty());
        mapper.clear();
        Assert.assertTrue(mapper.mapper.isEmpty());
    }

    @Test
    public void delete() {
        mapper.flush();
        Assert.assertTrue(sessionMapperFile.exists() && sessionMapperFile.length() > 0);
        mapper.delete();
        Assert.assertFalse(sessionMapperFile.exists());
    }
}