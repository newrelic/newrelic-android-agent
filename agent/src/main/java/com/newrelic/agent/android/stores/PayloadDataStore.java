/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.stores;

import android.content.Context;

import com.newrelic.agent.android.payload.Payload;
import com.newrelic.agent.android.payload.PayloadStore;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

public class PayloadDataStore extends DataStoreHelpler implements PayloadStore<Payload> {
    public static final String STORE_FILE = "NRPayloadStore";

    public PayloadDataStore(Context context) {
        this(context, STORE_FILE);
    }

    public PayloadDataStore(Context context, String storeFilename) {
        super(context, storeFilename);
    }

    public boolean store(Payload payload) {
        final String stringSet = toJsonString(payload);
        return super.store(payload.getUuid(), stringSet);
    }

    @Override
    public List<Payload> fetchAll() {
        final List<Payload> payloads = new ArrayList<Payload>();

        for (Object object : super.fetchAll()) {
            try {
                if (object instanceof String) {
                    JsonObject payloadObj = new Gson().fromJson((String) object, JsonObject.class);
                    Payload payload = new Gson().fromJson(payloadObj.get("payload").getAsString(), Payload.class);
                    payload.putBytes(decodePayload(payloadObj.get("encodedPayload").toString()));
                    payloads.add(payload);
                } else if (object instanceof HashSet<?>) {
                    @SuppressWarnings("unchecked")
                    HashSet<String> stringSet = (HashSet<String>) object;
                    Iterator<String> iter = stringSet.iterator();
                    Payload payload = new Gson().fromJson(iter.next(), Payload.class);
                    payload.putBytes(decodePayload(iter.next()));
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        return payloads;
    }

    @Override
    public void delete(Payload payload) {
        super.delete(payload.getUuid());
    }

    protected String encodePayload(Payload payload) {
        return encodeBytes(payload.getBytes());
    }

    protected byte[] decodePayload(String encodedString) {
        return decodeStringToBytes(encodedString);
    }

    protected String decodePayloadToString(byte[] decodedString) {
        return decodeBytesToString(decodedString);
    }

    /**
     * Return the payload as JSON string representing the payload metadata
     * and encoded bytes. Changes made 7/22/2021
     *
     * @param payload
     * @return LinkedHashSet<String> containing sthe payload's meta and byte values
     */
    private String toJsonString(Payload payload) {
        JsonObject stringObj = new JsonObject();
        stringObj.addProperty("payload", payload.asJsonMeta());
        stringObj.addProperty("encodedPayload", encodePayload(payload));
        return stringObj.toString();
    }

}
