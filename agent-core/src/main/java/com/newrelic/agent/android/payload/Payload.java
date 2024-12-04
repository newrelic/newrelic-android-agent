/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.payload;

import com.google.gson.JsonObject;
import com.newrelic.agent.android.util.SafeJsonPrimitive;

import java.nio.ByteBuffer;
import java.util.UUID;


public class Payload {
    protected String uuid;
    protected long timestamp;           // in millisecs
    protected boolean isPersistable;    // save to store
    protected ByteBuffer payload;

    public Payload() {
        this.uuid = UUID.randomUUID().toString();
        this.timestamp = System.currentTimeMillis();
        this.isPersistable = true;
        this.payload = ByteBuffer.wrap("".getBytes());
    }

    public Payload(byte[] bytes) {
        this();
        this.payload = ByteBuffer.wrap(bytes);
    }

    public byte[] getBytes() {
        return payload.array();
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getUuid() {
        return uuid;
    }

    public boolean isStale(final long ttl) {
        return (getTimestamp() + ttl) <= System.currentTimeMillis();
    }

    public void putBytes(byte[] payloadBytes) {
        payload = ByteBuffer.wrap(payloadBytes);
    }

    public void setPersisted(boolean isPersistable) {
        this.isPersistable = isPersistable;
    }

    public boolean isPersisted() {
        return isPersistable;
    }

    @Override
    public boolean equals(Object object) {
        if (object != null && object instanceof Payload) {
            return uuid.equalsIgnoreCase(((Payload) object).uuid);
        }
        return false;
    }

    public JsonObject asJsonObject() {
        JsonObject jsonObj = new JsonObject();
        jsonObj.add("timestamp", SafeJsonPrimitive.factory(timestamp));
        jsonObj.add("uuid", SafeJsonPrimitive.factory(uuid));
        return jsonObj;
    }

    public String asJsonMeta() {
        return asJsonObject().toString();
    }

    public long size() {
        return payload.array().length;
    }
}
