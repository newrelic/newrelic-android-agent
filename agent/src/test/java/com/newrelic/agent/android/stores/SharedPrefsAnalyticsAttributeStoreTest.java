/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.stores;

import android.content.Context;
import android.content.SharedPreferences;

import com.newrelic.agent.android.SpyContext;
import com.newrelic.agent.android.analytics.AnalyticsAttribute;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

@RunWith(RobolectricTestRunner.class)
public class SharedPrefsAnalyticsAttributeStoreTest {
    private Context context = new SpyContext().getContext();
    private SharedPrefsAnalyticsAttributeStore analyticsStore;
    private ArrayList<AnalyticsAttribute> attributesSet;

    @Before
    public void setUp() throws Exception {
        analyticsStore = spy(new SharedPrefsAnalyticsAttributeStore(context, "TestAnalyticsAttributeStore"));

        attributesSet = new ArrayList<AnalyticsAttribute>();
        attributesSet.add(new AnalyticsAttribute("string", "eenie"));
        attributesSet.add(new AnalyticsAttribute("float", 1));
        attributesSet.add(new AnalyticsAttribute("boolean", false));
    }

    @After
    public void tearDown() throws Exception {
        analyticsStore.clear();
        Assert.assertTrue("Should empty shared prefs store", analyticsStore.count() == 0);
    }

    @Test
    public void store() throws Exception {
        for (AnalyticsAttribute analyticsAttribute : attributesSet) {
            Assert.assertTrue(analyticsStore.store(analyticsAttribute));
        }
        Assert.assertEquals("Should contain 3 attributes", analyticsStore.count(), attributesSet.size());

        verify(analyticsStore, atLeastOnce()).applyOrCommitEditor(any(SharedPreferences.Editor.class));
    }

    @Test
    public void fetchAll() throws Exception {
        store();
        List<AnalyticsAttribute> attributes = analyticsStore.fetchAll();
        Assert.assertEquals("Should contain 3 attributes", attributes.size(), attributesSet.size());
    }

    @Test
    public void delete() throws Exception {
        store();
        for (AnalyticsAttribute analyticsAttribute : attributesSet) {
            analyticsStore.delete(analyticsAttribute);
        }
        List<AnalyticsAttribute> attributes = analyticsStore.fetchAll();
        Assert.assertTrue("Should contain no attributes", attributes.size() == 0);
    }

    @Test
    public void unpersistedStore() throws Exception {
        for (AnalyticsAttribute analyticsAttribute : attributesSet) {
            analyticsAttribute.setPersistent(false);
            Assert.assertFalse(analyticsStore.store(analyticsAttribute));
        }
        Assert.assertEquals("Should contain no attributes", analyticsStore.count(), 0);
    }

}