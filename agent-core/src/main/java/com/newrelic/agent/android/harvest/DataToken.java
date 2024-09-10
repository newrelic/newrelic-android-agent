/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.harvest;

import com.google.gson.JsonArray;
import com.google.gson.JsonPrimitive;
import com.newrelic.agent.android.harvest.type.HarvestableArray;

/**
 * A {@link HarvestableArray} which holds application identifying information.
 */
public class DataToken extends HarvestableArray {
    private int accountId;
    private int agentId;

    public DataToken() {
        super();
        clear();
    }

    public DataToken(final int accountId, final int agentId) {
        super();
        this.accountId = accountId;
        this.agentId = agentId;
    }

    @Override
    public JsonArray asJsonArray() {
        JsonArray array = new JsonArray();
        array.add(new JsonPrimitive(accountId));
        array.add(new JsonPrimitive(agentId));
        return array;
    }

    public void clear() {
        accountId = 0;
        agentId = 0;
    }

    public int getAccountId() {
        return accountId;
    }

    public void setAccountId(final int accountId) {
        this.accountId = accountId;
    }

    public int getAgentId() {
        return agentId;
    }

    public void setAgentId(final int agentId) {
        this.agentId = agentId;
    }

    public boolean isValid() {
        return accountId > 0 && agentId > 0;
    }

    public static DataToken newFromJson(JsonArray jsonArray) {
        final DataToken dataToken = new DataToken();
        dataToken.setAccountId(jsonArray.get(0).getAsInt());
        dataToken.setAgentId(jsonArray.get(1).getAsInt());
        return dataToken;
    }

    @Override
    public String toString() {
        return "DataToken{" +
                "accountId=" + accountId +
                ", agentId=" + agentId +
                '}';
    }

    public int[] asIntArray() {
        return new int[]{accountId, agentId};
    }

}
