/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.util;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;

import com.newrelic.agent.android.SpyContext;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.robolectric.RobolectricTestRunner;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;


@RunWith(RobolectricTestRunner.class)
public class ReachabilityTest {

    SpyContext spyContext;
    Context context;
    ConnectivityManager connectivityManager;
    Network network;
    NetworkCapabilities networkCapabilities;

    @Before
    public void setUp() throws Exception {
        spyContext = new SpyContext();
        context = spyContext.getContext();
        connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        network = mock(Network.class);
        networkCapabilities = mock(NetworkCapabilities.class);

        resetReachabilityState();
    }

    private void resetReachabilityState() throws Exception {
        setStaticAtomic("hasReachableNetwork", true);
        setStaticAtomic("callbackRegistered", false);
    }

    private void setStaticAtomic(String name, boolean value) throws Exception {
        Field field = Reachability.class.getDeclaredField(name);
        field.setAccessible(true);
        AtomicBoolean atomic = (AtomicBoolean) field.get(null);
        atomic.set(value);
    }

    private ConnectivityManager.NetworkCallback captureRegisteredCallback() {
        ArgumentCaptor<ConnectivityManager.NetworkCallback> captor =
                ArgumentCaptor.forClass(ConnectivityManager.NetworkCallback.class);
        verify(connectivityManager).registerNetworkCallback(any(NetworkRequest.class), captor.capture());
        return captor.getValue();
    }

    @Test
    public void defaultReachabilityIsTrueBeforeInit() {
        Assert.assertTrue(Reachability.hasReachableNetworkConnection(context, null));
    }

    @Test
    public void initRegistersNetworkCallbackOnce() {
        Reachability.init(context);
        Reachability.init(context);  // second call is a no-op
        verify(connectivityManager).registerNetworkCallback(any(NetworkRequest.class), any(ConnectivityManager.NetworkCallback.class));
    }

    @Test
    public void wifiCapabilitiesMarkNetworkReachable() {
        when(networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)).thenReturn(true);

        Reachability.init(context);
        captureRegisteredCallback().onCapabilitiesChanged(network, networkCapabilities);

        Assert.assertTrue(Reachability.hasReachableNetworkConnection(context, null));
    }

    @Test
    public void cellularCapabilitiesMarkNetworkReachable() {
        when(networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)).thenReturn(true);

        Reachability.init(context);
        captureRegisteredCallback().onCapabilitiesChanged(network, networkCapabilities);

        Assert.assertTrue(Reachability.hasReachableNetworkConnection(context, null));
    }

    @Test
    public void ethernetCapabilitiesMarkNetworkReachable() {
        when(networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)).thenReturn(true);

        Reachability.init(context);
        captureRegisteredCallback().onCapabilitiesChanged(network, networkCapabilities);

        Assert.assertTrue(Reachability.hasReachableNetworkConnection(context, null));
    }

    @Test
    public void onLostMarksNetworkUnreachable() {
        when(networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)).thenReturn(true);

        Reachability.init(context);
        ConnectivityManager.NetworkCallback callback = captureRegisteredCallback();
        callback.onCapabilitiesChanged(network, networkCapabilities);
        Assert.assertTrue(Reachability.hasReachableNetworkConnection(context, null));

        callback.onLost(network);
        Assert.assertFalse(Reachability.hasReachableNetworkConnection(context, null));
    }

    @Test
    public void capabilitiesWithoutRelevantTransportMarkNetworkUnreachable() {
        // capabilities with no transport bits set → unreachable
        Reachability.init(context);
        captureRegisteredCallback().onCapabilitiesChanged(network, networkCapabilities);

        Assert.assertFalse(Reachability.hasReachableNetworkConnection(context, null));
    }

    @Test
    public void initStaysFailOpenWhenConnectivityManagerUnavailable() {
        when(context.getSystemService(Context.CONNECTIVITY_SERVICE)).thenReturn(null);
        Reachability.init(context);
        Assert.assertTrue(Reachability.hasReachableNetworkConnection(context, null));
    }

    @Test
    public void initRetriesAfterRegistrationFailure() {
        doAnswer(invocation -> { throw new RuntimeException("boom"); })
                .when(connectivityManager)
                .registerNetworkCallback(any(NetworkRequest.class), any(ConnectivityManager.NetworkCallback.class));

        Reachability.init(context);
        // state should allow a retry; re-enable normal behavior and try again
        doAnswer(invocation -> null)
                .when(connectivityManager)
                .registerNetworkCallback(any(NetworkRequest.class), any(ConnectivityManager.NetworkCallback.class));
        Reachability.init(context);

        verify(connectivityManager, org.mockito.Mockito.times(2))
                .registerNetworkCallback(any(NetworkRequest.class), any(ConnectivityManager.NetworkCallback.class));
    }
}