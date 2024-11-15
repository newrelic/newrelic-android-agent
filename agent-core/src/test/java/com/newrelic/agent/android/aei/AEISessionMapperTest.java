/*
 * Copyright (c) 2024. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.aei;

import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.harvest.Harvest;
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
import java.util.Set;
import java.util.UUID;

public class AEISessionMapperTest {

    private static File reportsDir;
    private File sessionMapperFile;
    private AEISessionMapper mapper;

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
        mapper = new AEISessionMapper(sessionMapperFile);

        mapper.put(123, new AEISessionMapper.AEISessionMeta(UUID.randomUUID().toString(), 321));
        mapper.put(234, new AEISessionMapper.AEISessionMeta(UUID.randomUUID().toString(), 432));
        mapper.put(345, new AEISessionMapper.AEISessionMeta(UUID.randomUUID().toString(), 543));
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
        mapper.put(6661, new AEISessionMapper.AEISessionMeta(AgentConfiguration.getInstance().getSessionID(), 1666));
        Assert.assertEquals(AgentConfiguration.getInstance().getSessionID(), mapper.getSessionId(6661));
        Assert.assertEquals(1666, mapper.getRealAgentID(6661));
    }

    @Test
    public void get() {
        Assert.assertNotNull(mapper.get(234));
        Assert.assertNull(mapper.get(456));
    }

    @Test
    public void getOrDefault() {
        Assert.assertNotNull(mapper.getOrDefault(456, UUID.randomUUID().toString()));
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

    @Test
    public void erase() {
        Assert.assertNotNull(mapper.get(123));
        Assert.assertNotNull(mapper.get(234));
        Assert.assertNotNull(mapper.get(345));
        Assert.assertNull(mapper.get(456));

        Set<Integer> pidSet = Set.of(123, 234, 456);
        mapper.erase(pidSet);

        Assert.assertNotNull(mapper.get(123));
        Assert.assertNotNull(mapper.get(234));
        Assert.assertNull(mapper.get(345));

        mapper.put(456, new AEISessionMapper.AEISessionMeta(UUID.randomUUID().toString(), 654));
        mapper.erase(pidSet);
        Assert.assertNotNull(mapper.get(456));
    }
}