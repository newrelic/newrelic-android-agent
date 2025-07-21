/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.util;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import com.newrelic.agent.android.SpyContext;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;


@RunWith(RobolectricTestRunner.class)
public class ReachabilityTest {

    SpyContext spyContext;
    Context context;
    ConnectivityManager connectivityManager;
    
    // For modern API (API 23+)
    Network network;
    NetworkCapabilities networkCapabilities;


    @Before
    public void setUp() throws Exception {
        spyContext = new SpyContext();
        context = spyContext.getContext();
        connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        
        // Setup for modern API
        network = mock(Network.class);
        networkCapabilities = mock(NetworkCapabilities.class);
    }


    @Test
    public void testWifiReachabilityModern() {
        when(connectivityManager.getActiveNetwork()).thenReturn(network);
        when(connectivityManager.getNetworkCapabilities(network)).thenReturn(networkCapabilities);
        when(networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)).thenReturn(true);

        Assert.assertTrue(Reachability.hasReachableNetworkConnection(context, null));
    }

    @Test
    public void testCellularReachabilityModern()  {
        when(connectivityManager.getActiveNetwork()).thenReturn(network);
        when(connectivityManager.getNetworkCapabilities(network)).thenReturn(networkCapabilities);
        when(networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)).thenReturn(true);

        Assert.assertTrue(Reachability.hasReachableNetworkConnection(context, null));
    }

    @Test
    public void testEthernetReachabilityModern()  {
        when(connectivityManager.getActiveNetwork()).thenReturn(network);
        when(connectivityManager.getNetworkCapabilities(network)).thenReturn(networkCapabilities);
        when(networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)).thenReturn(true);

        Assert.assertTrue(Reachability.hasReachableNetworkConnection(context, null));
    }

    @Test
    public void testWithoutConnectivityManager()  {
        when(context.getSystemService(Context.CONNECTIVITY_SERVICE)).thenReturn(null);
        Assert.assertTrue(Reachability.hasReachableNetworkConnection(context, null));
    }

    @Test
    public void testWithReachableHost() {
        Assert.assertTrue(Reachability.hasReachableNetworkConnection(context, "newrelic.com"));
    }

}