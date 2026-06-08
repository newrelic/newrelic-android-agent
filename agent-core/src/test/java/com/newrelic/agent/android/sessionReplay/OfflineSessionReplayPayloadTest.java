/*
 * Copyright (c) 2026-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.sessionReplay;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class OfflineSessionReplayPayloadTest {

    private static Map<String, String> sampleAttrs() {
        Map<String, String> a = new LinkedHashMap<>();
        a.put("entityGuid", "MTIzfE1PQklMRXxBUFBMSUNBVElPTnw0NTY3");
        a.put("sessionId", "abc-123");
        a.put("replay.firstTimestamp", "1733000000000");
        a.put("replay.lastTimestamp", "1733000005000");
        a.put("hasMeta", "true");
        a.put("isFirstChunk", "false");
        return a;
    }

    @Test
    public void asJsonObject_thenFromJsonObject_roundTrips() {
        String uuid = UUID.randomUUID().toString();
        long capturedAt = 1733000000000L;
        long urlTimestamp = 1733000001000L;
        Map<String, String> attrs = sampleAttrs();
        byte[] body = "hello world gzip body".getBytes();

        OfflineSessionReplayPayload original = new OfflineSessionReplayPayload(
                uuid, capturedAt, urlTimestamp, attrs, body);

        String json = original.asJsonObject().toString();
        JsonObject parsed = JsonParser.parseString(json).getAsJsonObject();
        OfflineSessionReplayPayload restored = OfflineSessionReplayPayload.fromJsonObject(parsed);

        Assert.assertEquals(uuid, restored.getUuid());
        Assert.assertEquals(capturedAt, restored.getCapturedAt());
        Assert.assertEquals(urlTimestamp, restored.getUrlTimestamp());
        Assert.assertEquals(attrs, restored.getAttributes());
        Assert.assertArrayEquals(body, restored.getBody());
    }

    @Test
    public void body_survivesBinaryRoundTrip() {
        // Construct a non-UTF-8-safe binary payload covering the byte range.
        byte[] binary = new byte[256];
        for (int i = 0; i < 256; i++) {
            binary[i] = (byte) i;
        }

        OfflineSessionReplayPayload original = new OfflineSessionReplayPayload(
                "id-bin", 1L, 2L, sampleAttrs(), binary);

        String json = original.asJsonObject().toString();
        OfflineSessionReplayPayload restored = OfflineSessionReplayPayload.fromJsonObject(
                JsonParser.parseString(json).getAsJsonObject());

        Assert.assertArrayEquals(binary, restored.getBody());
    }

    @Test
    public void isStale_trueWhenOlderThanTtl() {
        long longAgo = System.currentTimeMillis() - 10_000L;
        OfflineSessionReplayPayload p = new OfflineSessionReplayPayload(
                "id", longAgo, longAgo, sampleAttrs(), new byte[]{1, 2, 3});

        Assert.assertTrue(p.isStale(5_000L));
    }

    @Test
    public void isStale_falseWhenFresh() {
        long now = System.currentTimeMillis();
        OfflineSessionReplayPayload p = new OfflineSessionReplayPayload(
                "id", now, now, sampleAttrs(), new byte[]{1, 2, 3});

        // 10 minutes TTL — definitely fresh.
        Assert.assertFalse(p.isStale(10 * 60 * 1000L));
    }

    @Test
    public void nullAttributesAndBody_areTreatedAsEmpty() {
        OfflineSessionReplayPayload p = new OfflineSessionReplayPayload(
                "id", 1L, 2L, null, null);

        Assert.assertNotNull(p.getAttributes());
        Assert.assertTrue(p.getAttributes().isEmpty());
        Assert.assertNotNull(p.getBody());
        Assert.assertEquals(0, p.getBody().length);

        // round-trip an empty payload too
        OfflineSessionReplayPayload restored = OfflineSessionReplayPayload.fromJsonObject(
                JsonParser.parseString(p.asJsonObject().toString()).getAsJsonObject());
        Assert.assertEquals("id", restored.getUuid());
        Assert.assertEquals(0, restored.getBody().length);
        Assert.assertTrue(restored.getAttributes().isEmpty());
    }

    @Test
    public void getAttributes_returnsCopy_notInternalReference() {
        Map<String, String> input = sampleAttrs();
        OfflineSessionReplayPayload p = new OfflineSessionReplayPayload(
                "id", 1L, 2L, input, new byte[0]);

        // mutating the original input must not change the payload
        input.put("attackerKey", "attackerValue");
        Assert.assertFalse(p.getAttributes().containsKey("attackerKey"));
    }

    @Test
    public void emptyBodyStillSerializes() {
        OfflineSessionReplayPayload p = new OfflineSessionReplayPayload(
                "id", 1L, 2L, sampleAttrs(), new byte[0]);
        String json = p.asJsonObject().toString();
        Assert.assertTrue(json.contains("\"body\""));
        OfflineSessionReplayPayload restored = OfflineSessionReplayPayload.fromJsonObject(
                JsonParser.parseString(json).getAsJsonObject());
        Assert.assertEquals(0, restored.getBody().length);
    }

    @Test
    public void fromJsonObject_nullThrows() {
        try {
            OfflineSessionReplayPayload.fromJsonObject(null);
            Assert.fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    @Test
    public void attributesPreserveInsertionOrder() {
        Map<String, String> attrs = sampleAttrs();
        OfflineSessionReplayPayload p = new OfflineSessionReplayPayload(
                "id", 1L, 2L, attrs, new byte[]{0});
        OfflineSessionReplayPayload restored = OfflineSessionReplayPayload.fromJsonObject(
                JsonParser.parseString(p.asJsonObject().toString()).getAsJsonObject());

        Assert.assertEquals(
                Arrays.asList(attrs.keySet().toArray()),
                Arrays.asList(restored.getAttributes().keySet().toArray()));
    }
}