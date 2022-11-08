/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import com.newrelic.agent.android.SpyContext;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class ReachabilityTest {

    SpyContext spyContext = new SpyContext();
    Context context = spyContext.getContext();
    ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    NetworkInfo networkInfo = spy(connectivityManager.getActiveNetworkInfo());

    @Before
    public void setUp() throws Exception {
        spyContext = new SpyContext();
        context = spyContext.getContext();
        connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        networkInfo = spy(connectivityManager.getActiveNetworkInfo());
    }

    @Test
    public void testWifiReachability() throws Exception {
        when(connectivityManager.getActiveNetworkInfo()).thenReturn(networkInfo);
        when(networkInfo.getType()).thenReturn(ConnectivityManager.TYPE_WIFI);

        Assert.assertTrue(Reachability.hasReachableNetworkConnection(context, null));
    }

    @Test
    public void testCellularReachability() throws Exception {
        when(connectivityManager.getActiveNetworkInfo()).thenReturn(networkInfo);
        when(networkInfo.getType()).thenReturn(ConnectivityManager.TYPE_MOBILE);

        Assert.assertTrue(Reachability.hasReachableNetworkConnection(context, null));
    }

    @Test
    public void testEthernetReachability() throws Exception {
        when(connectivityManager.getActiveNetworkInfo()).thenReturn(networkInfo);
        when(networkInfo.getType()).thenReturn(ConnectivityManager.TYPE_ETHERNET);

        Assert.assertTrue(Reachability.hasReachableNetworkConnection(context, null));
    }

    @Test
    public void testWithoutConnectivityManager() throws Exception {
        when(context.getSystemService(Context.CONNECTIVITY_SERVICE)).thenReturn(null);
        Assert.assertTrue(Reachability.hasReachableNetworkConnection(context, null));
    }

    @Test
    public void testWithReachableHost() throws Exception {
        Assert.assertTrue(Reachability.hasReachableNetworkConnection(context, "newrelic.com"));
    }

}