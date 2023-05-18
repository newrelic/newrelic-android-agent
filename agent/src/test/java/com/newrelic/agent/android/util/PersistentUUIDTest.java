/**
 * Copyright 2021-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.util;

import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.os.Environment;
import android.text.TextUtils;

import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.NullAgentImpl;
import com.newrelic.agent.android.SpyContext;
import com.newrelic.agent.android.analytics.AnalyticsAttribute;
import com.newrelic.agent.android.analytics.AnalyticsControllerImpl;
import com.newrelic.agent.android.metric.MetricNames;
import com.newrelic.agent.android.stats.StatsEngine;
import com.newrelic.agent.android.stores.SharedPrefsAnalyticsAttributeStore;

import org.junit.Assert;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Spy;
import org.robolectric.RobolectricTestRunner;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.UUID;

@RunWith(RobolectricTestRunner.class)
public class PersistentUUIDTest {
    private static final String UUID_FILENAME = "nr_installation";
    private static final String UUID_STATIC = "ab20dfe5-96d2-4c6d-b975-3fe9d8778dfc";
    private static final String UUID_JSON = "{'nr_uuid':'" + UUID_STATIC + "'}";
    private static File UUID_FILE = new File(Environment.getDataDirectory(), UUID_FILENAME);

    @Spy
    private TestPersistentUUID persistentUUID;

    private Context context = new SpyContext().getContext();

    AgentConfiguration agentConfiguration;

    @Before
    public void setUp() throws Exception {
        UUID_FILE = new File(context.getFilesDir(), UUID_FILENAME);
        persistentUUID = spy(new TestPersistentUUID(context));
        StatsEngine.reset();
    }

    @Before
    public void setUpAnalytics() throws Exception {
        agentConfiguration = new AgentConfiguration();
        agentConfiguration.setEnableAnalyticsEvents(true);
        agentConfiguration.setAnalyticsAttributeStore(new SharedPrefsAnalyticsAttributeStore(context));
        agentConfiguration.setDeviceID(UUID_STATIC);
        AnalyticsControllerImpl.shutdown();
        AnalyticsControllerImpl.initialize(agentConfiguration, new NullAgentImpl());
    }

    @After
    public void tearDown() throws Exception {
        if (UUID_FILE != null) {
            UUID_FILE.delete();
            Assert.assertFalse(UUID_FILE.exists());
        }
    }

    @Test
    public void testGetDeviceId() throws Exception {
        String uuid = persistentUUID.getDeviceId(context);
        Assert.assertNotNull("DeviceID is not null", uuid);
        Assert.assertEquals("Should be a Type-4 generated UUID", uuid, UUID.fromString(uuid).toString());
    }

    @Test
    public void testGetPersistentUUID() throws Exception {
        String uuid = persistentUUID.getPersistentUUID();
        verify(persistentUUID).setPersistedUUID(uuid);
        verify(persistentUUID).putUUIDToFileStore(uuid);
        Assert.assertTrue(!TextUtils.isEmpty(uuid));

        reset(persistentUUID);
        Assert.assertEquals("Should return persisted UUID", uuid, persistentUUID.getPersistentUUID());
        verify(persistentUUID).getUUIDFromFileStore();
    }

    @Test
    public void testWriteUUIDToFile() throws Exception {
        String uuid = persistentUUID.getUUIDFromFileStore();
        persistentUUID.putUUIDToFileStore(persistentUUID.getPersistentUUID());
        uuid = persistentUUID.getUUIDFromFileStore();
        Assert.assertEquals("Should save uuid to file", uuid, persistentUUID.getPersistentUUID());
    }

    @Test
    public void testReadUUIDFromFile() throws Exception {
        UUID_FILE.delete();

        // Test corrupted file
        BufferedWriter out = new BufferedWriter(new FileWriter(UUID_FILE));
        out.write(UUID_JSON);
        out.close();

        String uuid = persistentUUID.getUUIDFromFileStore();
        Assert.assertEquals("Should save uuid to file", uuid, UUID_STATIC);
    }

    @Test
    public void testInvalidUUID() throws Exception {
        // Tests Type 4 (pseudo randomly generated) UUID
        // format of existing UUID is xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx

        String testUUID = UUID.fromString(UUID_STATIC).toString();

        Assert.assertTrue("Should be a Type-4 generated UUID", !TextUtils.isEmpty(testUUID));
        Assert.assertEquals("UUIDs should match", UUID_STATIC, testUUID);

        try {
            testUUID = UUID.fromString("a-b-c.d").toString();
            Assert.assertTrue("UUID format should be Type 4", TextUtils.isEmpty(testUUID));
            Assert.fail();
        } catch (Exception e) {
            Assert.assertTrue("Throws IllegalArgumentException", e instanceof IllegalArgumentException);
        }

        try {
            testUUID = UUID.fromString(null).toString();
            Assert.assertTrue("UUID args should not be null", TextUtils.isEmpty(testUUID));
            Assert.fail();
        } catch (Exception e) {
            Assert.assertTrue("Throws NullPointerException", e instanceof NullPointerException);
        }
    }

    @Test
    public void testInvalidUUIDStore() throws Exception {
        String uuid;

        // Test empty file
        writeToFileStore(null, 1);
        try {
            uuid = persistentUUID.getUUIDFromFileStore();
            Assert.assertTrue("UUID is empty", TextUtils.isEmpty(uuid));
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail("Should not throw");
        }

        // Test corrupted file
        try {
            writeToFileStore("0xdeadbeef == 0xbadf00d", 1);
            uuid = persistentUUID.getUUIDFromFileStore();
            Assert.assertTrue("UUID is empty", TextUtils.isEmpty(uuid));
        } catch (Exception e) {
            Assert.fail("Should not throw:" + e.getMessage());
        }

        // Test super large file
        writeToFileStore(UUID_STATIC, 100);
        try {
            uuid = persistentUUID.getUUIDFromFileStore();
            Assert.assertTrue("UUID is empty", TextUtils.isEmpty(uuid));
        } catch (Exception e) {
            Assert.fail("Should not throw:" + e.getMessage());
        }
    }

    @Test
    public void testRecoveredUUID() throws Exception {
        writeToFileStore(UUID_JSON, 1);
        String uuid = persistentUUID.getPersistentUUID();
        Assert.assertEquals("Should save uuid to file", uuid, persistentUUID.getPersistentUUID());
        Assert.assertTrue("Should increment RECOVERED metric", StatsEngine.get().getStatsMap().containsKey(MetricNames.METRIC_UUID_RECOVERED));
    }

    @Test
    public void testSetDeviceId() throws Exception {
        final String deviceId = UUID.randomUUID().toString();

        UUID_FILE.delete();

        String uuid = persistentUUID.getPersistentUUID();
        Assert.assertEquals("Should save uuid to file", uuid, persistentUUID.getPersistentUUID());
    }

    @Test
    public void testInstallMetricAndAttributes() throws Exception {
        String uuid = persistentUUID.getPersistentUUID();

        // First, check that that metric has been created
        Assert.assertTrue("Should contain install metric", StatsEngine.get().getStatsMap().containsKey(MetricNames.MOBILE_APP_INSTALL));

        // Next, check that the 'install' attribute has been created and is the correct value
        Assert.assertTrue("Should contain install attribute", AnalyticsControllerImpl.getInstance().getAttribute(AnalyticsAttribute.APP_INSTALL_ATTRIBUTE).getBooleanValue());
    }

    private void writeToFileStore(String data, int repeat) {
        UUID_FILE.delete();
        repeat = Math.max(1, repeat);
        try (FileWriter fileWriter = new FileWriter(UUID_FILE)) {
            while (repeat-- > 0) {
                try (BufferedWriter out = new BufferedWriter(fileWriter)) {
                    if (TextUtils.isEmpty(data)) {
                        out.newLine();
                    } else {
                        out.write(data);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private class TestPersistentUUID extends PersistentUUID {

        private ArrayList<String> noticedTags = new ArrayList<String>();

        public TestPersistentUUID(Context context) {
            super(context);
        }

        public ArrayList<String> getNoticedTags() {
            return noticedTags;
        }

        @Override
        protected void noticeUUIDMetric(String tag) {
            noticedTags.add(tag);
        }

    }
}