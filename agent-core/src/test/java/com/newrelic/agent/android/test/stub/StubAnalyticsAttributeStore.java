/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.test.stub;

import com.newrelic.agent.android.analytics.AnalyticsAttribute;
import com.newrelic.agent.android.analytics.AnalyticsAttributeStore;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class StubAnalyticsAttributeStore implements AnalyticsAttributeStore {
    private Set<AnalyticsAttribute> store = Collections.synchronizedSet(new HashSet<AnalyticsAttribute>());

    @Override
    public synchronized boolean store(AnalyticsAttribute attribute) {
        if (attribute.isPersistent()) {
            store.add(attribute);
        }
        return true;
    }

    @Override
    public synchronized List<AnalyticsAttribute> fetchAll() {
        AnalyticsAttribute[] attrs = store.toArray(new AnalyticsAttribute[store.size()]);
        List<AnalyticsAttribute> attrList = Arrays.asList(attrs);
        return attrList;
    }

    @Override
    public synchronized int count() {
        return store.size();
    }

    @Override
    public synchronized void clear() {
        store.clear();
    }

    @Override
    public synchronized void delete(AnalyticsAttribute attribute) {
        store.remove(attribute);
    }
}
