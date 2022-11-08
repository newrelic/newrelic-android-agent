/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.stores;

import android.content.Context;

import com.newrelic.agent.android.SpyContext;
import com.newrelic.agent.android.agentdata.HexAttribute;
import com.newrelic.agent.android.agentdata.builder.AgentDataBuilder;
import com.newrelic.agent.android.payload.Payload;
import com.newrelic.com.google.flatbuffers.FlatBufferBuilder;
import com.newrelic.mobile.fbs.HexAgentData;
import com.newrelic.mobile.fbs.HexAgentDataBundle;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class SharedPrefsPayloadStoreTest {

    private static Context context = new SpyContext().getContext();
    private static byte[] testBytes = {0xd, 0xe, 0xa, 0xd, 0xb, 0xe, 0xe, 0xf};
    private static String testJson = "{'carrier': 'wifi', 'uuid': '571494b6-0717-4097-bfdd-426c0a0b20ca','deviceManufacturer': 'Genymotion', 'platformVersion': '5.8.3', 'osName': 'Android', 'osVersion': '5.1','osMajorVersion': '5', 'newRelicVersion': '5.8.3'}";

    private SharedPrefsPayloadStore payloadStore;
    private Payload testPayload;
    private Payload jsonPayload;

    @Before
    public void setUp() throws Exception {
        payloadStore = new SharedPrefsPayloadStore(context);
        testPayload = new Payload(testBytes);
        jsonPayload = new Payload(testJson.getBytes());
    }

    @After
    public void tearDown() throws Exception {
        payloadStore.clear();
    }

    @Test
    public void getStoreFilename() throws Exception {
        Assert.assertEquals("Should return shared prefs name", payloadStore.getStoreFilename(), SharedPrefsPayloadStore.STORE_FILE);
    }

    @Test
    public void store() throws Exception {
        payloadStore.store(testPayload);
        List<Payload> list = payloadStore.fetchAll();
        Assert.assertNotNull("Should store payload", list.contains(testPayload));
    }

    @Test
    public void fetchAll() throws Exception {
        payloadStore.store(testPayload);
        payloadStore.store(jsonPayload);
        payloadStore.store(testPayload);
        List<Payload> list = payloadStore.fetchAll();
        Assert.assertNotNull("Should store payload", list.contains(testPayload));
        Assert.assertEquals("Should not store duplicates", 2, payloadStore.fetchAll().size());
    }

    @Test
    public void count() throws Exception {
        payloadStore.store(testPayload);
        payloadStore.store(jsonPayload);
        payloadStore.store(testPayload);
        List<Payload> list = payloadStore.fetchAll();
        Assert.assertEquals("Should not store duplicates", 2, list.size());
    }

    @Test
    public void clear() throws Exception {
        payloadStore.store(testPayload);
        payloadStore.store(jsonPayload);
        payloadStore.store(testPayload);
        Assert.assertEquals("Should contains payload(s)", 2, payloadStore.fetchAll().size());

        payloadStore.clear();
        Assert.assertTrue("Should remove all payloads", payloadStore.fetchAll().isEmpty());
    }

    @Test
    public void delete() throws Exception {
        payloadStore.store(testPayload);
        payloadStore.store(jsonPayload);
        Assert.assertEquals("Should contain 2 payloads", 2, payloadStore.fetchAll().size());

        Assert.assertTrue("Should include testPayload", payloadStore.fetchAll().contains(testPayload));

        payloadStore.delete(testPayload);
        Assert.assertFalse("Should remove testPayload", payloadStore.fetchAll().contains(testPayload));

        payloadStore.delete(jsonPayload);
        Assert.assertFalse("Should remove jsonPayload", payloadStore.fetchAll().contains(jsonPayload));

        Assert.assertEquals("Should contain 0 payloads", 0, payloadStore.fetchAll().size());
    }

    @Test
    public void encodePayload() throws Exception {
        String encodedByteAsString = payloadStore.encodePayload(testPayload);
        Assert.assertNotNull("Should return encoded string", encodedByteAsString);
        Assert.assertNotEquals("Should return encoded bytes", ByteBuffer.wrap(encodedByteAsString.getBytes()), ByteBuffer.wrap(testPayload.getBytes()));

        String encodedString = payloadStore.encodePayload(jsonPayload);
        Assert.assertNotNull("Should return encoded string", encodedString);
        Assert.assertNotEquals("Should return encoded bytes", ByteBuffer.wrap(encodedByteAsString.getBytes()), ByteBuffer.wrap(jsonPayload.getBytes()));
    }

    @Test
    public void decodePayload() throws Exception {
        String encodedBytes = payloadStore.encodePayload(testPayload);
        byte[] decodedBytes = payloadStore.decodePayload(encodedBytes);
        Assert.assertEquals("Should return decoded bytes", ByteBuffer.wrap(decodedBytes), ByteBuffer.wrap(testPayload.getBytes()));

        String encodedString = payloadStore.encodePayload(jsonPayload);
        byte[] decodedString = payloadStore.decodePayload(encodedString);
        Assert.assertEquals("Should return decoded bytes", ByteBuffer.wrap(decodedString), ByteBuffer.wrap(jsonPayload.getBytes()));
        Assert.assertEquals("Should return decoded string", payloadStore.decodePayloadToString(decodedString), testJson);
    }

    @Test
    public void testFlatBufferPersistence() throws Exception {
        final Map<String, Object> sessionAttributes = new HashMap<String, Object>() {{
            put("a string", "hello");
            put("dbl", 666.6666666);
            put("lng", 12323435456463233L);
            put("yes", true);
            put("int", 3);
        }};

        final Map<String, Object> hex = new HashMap<String, Object>() {{
            put(HexAttribute.HEX_ATTR_APP_UUID_HI, "abc");
            put(HexAttribute.HEX_ATTR_APP_UUID_LO, 123);
            put(HexAttribute.HEX_ATTR_SESSION_ID, "bad-beef");
            put(HexAttribute.HEX_ATTR_CLASS_NAME, "HexDemo");
            put(HexAttribute.HEX_ATTR_METHOD_NAME, "demo");
            put(HexAttribute.HEX_ATTR_FILENAME, "MyClass.java");
            put(HexAttribute.HEX_ATTR_LINE_NUMBER, 100L);
            put(HexAttribute.HEX_ATTR_TIMESTAMP_MS, System.currentTimeMillis());
            put(HexAttribute.HEX_ATTR_MESSAGE, "Handled");
            put(HexAttribute.HEX_ATTR_CAUSE, "idk");
            put(HexAttribute.HEX_ATTR_NAME, "Beelzebub");
        }};

        Set<Map<String, Object>> set = new HashSet<>();
        set.add(hex);

        FlatBufferBuilder flat = AgentDataBuilder.startAndFinishAgentData(sessionAttributes, set);

        Payload hexPayload = new Payload(flat.sizedByteArray());
        payloadStore.store(hexPayload);

        List<Payload> payloads = payloadStore.fetchAll();
        Payload cachedPayload = payloads.get(payloads.indexOf(hexPayload));
        Assert.assertNotNull("Should find payload in store", cachedPayload);

        HexAgentDataBundle hexBundle = HexAgentDataBundle.getRootAsHexAgentDataBundle(ByteBuffer.wrap(hexPayload.getBytes()));
        HexAgentDataBundle cachedBundle = HexAgentDataBundle.getRootAsHexAgentDataBundle(ByteBuffer.wrap(cachedPayload.getBytes()));

        Assert.assertEquals("Should deserialize hex payload", hexBundle.getByteBuffer(), cachedBundle.getByteBuffer());
        Assert.assertEquals("Should deserialize agentData instances", hexBundle.hexAgentDataLength(), cachedBundle.hexAgentDataLength());

        HexAgentData hexAgentData = hexBundle.hexAgentData(0);
        HexAgentData cachedAgentData = cachedBundle.hexAgentData(0);
        Assert.assertNotNull(hexAgentData);
        Assert.assertNotNull(cachedAgentData);
        Assert.assertEquals("Should deserialize agentData", hexAgentData.getByteBuffer(), cachedAgentData.getByteBuffer());
        Assert.assertEquals("Should deserialize handles exception", hexAgentData.handledExceptions(0).getByteBuffer(), cachedAgentData.handledExceptions(0).getByteBuffer());
    }
}
