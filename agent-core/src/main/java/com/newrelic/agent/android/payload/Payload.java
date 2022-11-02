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
    private final long timestamp;   // in millisecs
    private final String uuid;
    private ByteBuffer payload;
    private boolean isPersistable = true;       // write to store

    public Payload() {
        this.timestamp = System.currentTimeMillis();
        this.uuid = UUID.randomUUID().toString();
        this.isPersistable = true;
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
        return (timestamp + ttl) <= System.currentTimeMillis();
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

}
