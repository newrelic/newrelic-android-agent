/**
 * Copyright 2021 New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android;

import static org.mockito.Mockito.when;

import android.content.Context;
import android.net.ConnectivityManager;

import com.newrelic.agent.android.api.common.CarrierType;
import com.newrelic.agent.android.api.common.WanType;
import com.newrelic.agent.android.util.Connectivity;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class ConnectivityTest {
    private SpyContext spyContext;
    private Context context;
    private ConnectivityManager connectivityManager;

    @Before
    public void setUp() throws Exception {
        spyContext = new SpyContext();
        context = spyContext.getContext();
        connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    @Test
    public void carrierNameFromContext() throws Exception {
        String carrierName = Connectivity.carrierNameFromContext(context);
        Assert.assertEquals(carrierName, CarrierType.WIFI);
    }

    @Test
    public void testCarrierNameNoSuchMethodError() {
        when(connectivityManager.getActiveNetwork()).thenThrow(new NoSuchMethodError("getActiveNetwork"));
        String carrierName = Connectivity.carrierNameFromContext(context);
        Assert.assertEquals(CarrierType.UNKNOWN, carrierName);
    }

    @Test
    public void testWanTypeNoSuchMethodError() {
        when(connectivityManager.getActiveNetwork()).thenThrow(new NoSuchMethodError("getActiveNetwork"));
        String wanType = Connectivity.wanType(context);
        Assert.assertEquals(WanType.UNKNOWN, wanType);
    }
}