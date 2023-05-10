/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.payload;

import com.google.gson.Gson;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.UUID;

public class PayloadTest {
    private final String testBytes = "TestBytes";
    private final String moreTestBytes = "Another byte string";

    private Payload payload;

    @Before
    public void setUp() throws Exception {
        payload = new Payload(testBytes.getBytes());
    }

    @Test
    public void getUuid() throws Exception {
        Assert.assertNotNull("Should create UUID", payload.getUuid());
        Assert.assertFalse("Should create UUID", payload.getUuid().isEmpty());
        try {
            Assert.assertNotNull("Should create valid UUID", UUID.fromString(payload.getUuid()));
        } catch (Exception e) {
            Assert.fail();
        }
    }

    @Test
    public void getBytes() throws Exception {
        Assert.assertEquals("Should return payload bytes", ByteBuffer.wrap(payload.getBytes()), ByteBuffer.wrap(testBytes.getBytes()));
    }

    @Test
    public void isStale() throws Exception {
        final long ttl = 1000 * 2;      // 2 sec ttl
        Assert.assertFalse("New payload should not be stale", payload.isStale(ttl));
        Thread.sleep(ttl);
        Assert.assertTrue("New payload should now be stale", payload.isStale(ttl));
    }

    @Test
    public void putBytes() throws Exception {
        payload.putBytes(moreTestBytes.getBytes());
        Assert.assertEquals("Should set payload bytes", ByteBuffer.wrap(payload.getBytes()), ByteBuffer.wrap(moreTestBytes.getBytes()));
    }

    @Test
    public void testJsonMetaObject() throws Exception {
        String payloadMeta = payload.asJsonMeta();
        Assert.assertFalse("Should return payload meta as json string", payloadMeta.isEmpty());

        Payload json = new Gson().fromJson(payloadMeta, Payload.class);
        Assert.assertEquals("Should deserialize payload timestamp", payload.getTimestamp(), json.getTimestamp());
        Assert.assertEquals("Should deserialize payload uuid", payload.getUuid(), json.getUuid());
    }

    @Test
    public void testEquals() throws Exception {
        String payloadMeta = payload.asJsonMeta();

        Payload thisPayload = new Gson().fromJson(payloadMeta, Payload.class);
        Assert.assertFalse(payload == thisPayload);
        Assert.assertEquals(payload, thisPayload);

        Payload thatPayload = new Payload(moreTestBytes.getBytes());
        Assert.assertFalse(payload == thatPayload);
        Assert.assertNotEquals(payload, thatPayload);
    }

    @Test
    public void testTimestamp() throws Exception {
        Payload payload = new Payload();
        Assert.assertEquals("Should create payload now", payload.getTimestamp(), System.currentTimeMillis(), 100);
    }

    @Test
    public void testPersisted() throws Exception {
        payload.setPersisted(true);
        Assert.assertTrue("Should persist payload", payload.isPersisted());

        payload.setPersisted(false);
        Assert.assertFalse("Should not persist payload", payload.isPersisted());

        Assert.assertTrue("Default should persist", new Payload().isPersisted());
    }
}