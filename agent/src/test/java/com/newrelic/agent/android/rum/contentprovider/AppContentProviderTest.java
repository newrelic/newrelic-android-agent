/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.rum.contentprovider;

import android.test.ProviderTestCase2;
import android.test.mock.MockContentResolver;

import junit.framework.Assert;

import org.junit.Test;

public class AppContentProviderTest extends ProviderTestCase2<NewRelicAppContentProvider> {
    private MockContentResolver resolver;

    /**
     * Constructor.
     *
     * @param providerClass     The class name of the provider under test
     * @param providerAuthority The provider's authority string
     */
    public AppContentProviderTest(Class<NewRelicAppContentProvider> providerClass, String providerAuthority) {
        super(providerClass, providerAuthority);
    }

    public AppContentProviderTest() {
        super(NewRelicAppContentProvider.class, "com.newrelic.agent.android.rum.contentprovider.NewRelicAppContentProvider");
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        resolver = this.getMockContentResolver();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void testOnCreate() {
        NewRelicAppContentProvider provider = new NewRelicAppContentProvider();
        Assert.assertFalse(provider.onCreate());
    }
}
