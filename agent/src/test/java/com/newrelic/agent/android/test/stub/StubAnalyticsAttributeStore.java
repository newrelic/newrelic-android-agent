/*
 * Copyright (c) 2024. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.test.stub;

import com.newrelic.agent.android.analytics.AnalyticsAttribute;
import com.newrelic.agent.android.analytics.AnalyticsAttributeStore;

import java.util.ArrayList;
import java.util.List;

public class StubAnalyticsAttributeStore implements AnalyticsAttributeStore {

    @Override
    public boolean store(AnalyticsAttribute attribute) {
        return true;
    }

    @Override
    public List<AnalyticsAttribute> fetchAll() {
        return new ArrayList<AnalyticsAttribute>();
    }

    @Override
    public int count() {
        return 0;
    }

    @Override
    public void clear() {
    }

    @Override
    public void delete(AnalyticsAttribute attribute) {
    }
}

