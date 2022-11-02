/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.agentdata.builder;

import com.google.flatbuffers.FlatBufferBuilder;
import com.newrelic.agent.android.agentdata.HexAttribute;
import com.newrelic.mobile.fbs.HexAgentData;
import com.newrelic.mobile.fbs.HexAgentDataBundle;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AgentDataBuilderTest {

    @Test
    public void toAgentDataAndBackToMap() throws Exception {
        final Map<String, Object> sessionAttributes = new HashMap<String, Object>() {{
            put("a string", "hello");
            put("dbl", 666.6666666);
            put("lng", 12323435456463233L);
            put("yes", true);
            put("int", 3);
        }};

        final List<Map<String, Object>> thread = new ArrayList<>();
        final Map<String, Object> frames = new LinkedHashMap<>();
        frames.put(HexAttribute.HEX_ATTR_LINE_NUMBER, 1);
        frames.put(HexAttribute.HEX_ATTR_FILENAME, "MyClass.java");
        frames.put(HexAttribute.HEX_ATTR_CLASS_NAME, "MyTestClass");
        frames.put(HexAttribute.HEX_ATTR_METHOD_NAME, "run");
        thread.add(frames);

        final Map<String, Object> handledException = new HashMap<String, Object>() {{
            put(HexAttribute.HEX_ATTR_TIMESTAMP_MS, System.currentTimeMillis());
            put(HexAttribute.HEX_ATTR_NAME, "NullPointerException");
            put(HexAttribute.HEX_ATTR_MESSAGE, "Handled");
            put(HexAttribute.HEX_ATTR_CAUSE, "idk");
            put(HexAttribute.HEX_ATTR_THREAD, thread);
        }};

        UUID buildUuid = UUID.fromString("aaa27edf-ed2c-0ed6-60c9-a6e3be5d403b");
        handledException.put(HexAttribute.HEX_ATTR_APP_UUID_HI, buildUuid.getMostSignificantBits());
        handledException.put(HexAttribute.HEX_ATTR_APP_UUID_LO, buildUuid.getLeastSignificantBits());

        Set<Map<String, Object>> hexSet = new HashSet<>();
        hexSet.add(handledException);
        FlatBufferBuilder finishedFlat = AgentDataBuilder.startAndFinishAgentData(sessionAttributes, hexSet);
        final ByteBuffer byteBuffer = finishedFlat.dataBuffer();
        assert byteBuffer != null;

        HexAgentDataBundle agentDataBundle = HexAgentDataBundle.getRootAsHexAgentDataBundle(byteBuffer);
        HexAgentData agentData = agentDataBundle.hexAgentData(0);

        assertEquals(1, agentData.handledExceptions(0).threads(0).frames(0).lineNumber());
        assertEquals("MyClass.java", agentData.handledExceptions(0).threads(0).frames(0).fileName());
        assertEquals("MyTestClass", agentData.handledExceptions(0).threads(0).frames(0).className());
        assertEquals("run", agentData.handledExceptions(0).threads(0).frames(0).methodName());
        assertEquals(0, agentData.applicationInfo().platform());

        Map<String, Object> map = AgentDataBuilder.attributesMapFromAgentData(agentData);

        assertTrue(map.containsKey("a string"));
        assertTrue(map.containsKey("dbl"));
        assertTrue(map.containsKey("lng"));
        assertTrue(map.containsKey("yes"));
        assertTrue(map.containsKey("int"));
        assertTrue(map.containsKey("thread 0"));
        assertEquals("hello", map.get("a string"));
        assertEquals(666.6666666, (Double) map.get("dbl"), 0.00000001);
        assertEquals(12323435456463233L,map.get("lng") );
        assertEquals(true, map.get("yes"));
        assertEquals(3L, map.get("int"));
        assertEquals(-6151214640812781866L, map.get(HexAttribute.HEX_ATTR_APP_UUID_HI));
        assertEquals(6974288995041493051L, map.get(HexAttribute.HEX_ATTR_APP_UUID_LO));
        assertEquals("NullPointerException", map.get(HexAttribute.HEX_ATTR_NAME));
    }
}