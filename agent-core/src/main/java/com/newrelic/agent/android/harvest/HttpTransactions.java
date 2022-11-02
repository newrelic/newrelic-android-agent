/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.harvest;

import com.google.gson.JsonArray;
import com.newrelic.agent.android.harvest.type.HarvestableArray;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A managed collection of {@link HttpTransaction} objects.
 */
public class HttpTransactions extends HarvestableArray {
    private final Collection<HttpTransaction> httpTransactions = new CopyOnWriteArrayList<HttpTransaction>();

    public synchronized void add(HttpTransaction httpTransaction) {
        httpTransactions.add(httpTransaction);
    }

    public synchronized void remove(HttpTransaction transaction) {
        httpTransactions.remove(transaction);
    }

    public void clear() {
        httpTransactions.clear();
    }

    @Override
    public JsonArray asJsonArray() {
        JsonArray array = new JsonArray();
        for (HttpTransaction transaction : httpTransactions) {
            array.add(transaction.asJson());
        }
        return array;
    }

    public Collection<HttpTransaction> getHttpTransactions() {
        return httpTransactions;
    }

    public int count() {
        return httpTransactions.size();
    }

    @Override
    public String toString() {
        return "HttpTransactions{" +
                "httpTransactions=" + httpTransactions +
                '}';
    }
}
