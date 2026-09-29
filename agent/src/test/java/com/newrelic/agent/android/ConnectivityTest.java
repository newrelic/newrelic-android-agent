/**
 * Copyright 2021 New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.telephony.TelephonyManager;

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

    private TelephonyManager useCellularTransport() {
        Network network = mock(Network.class);
        NetworkCapabilities capabilities = mock(NetworkCapabilities.class);
        when(capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)).thenReturn(true);
        when(connectivityManager.getActiveNetwork()).thenReturn(network);
        when(connectivityManager.getNetworkCapabilities(network)).thenReturn(capabilities);
        return (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
    }

    @Test
    public void testWanTypeCellularSubtypeResolved() {
        TelephonyManager telephonyManager = useCellularTransport();
        when(telephonyManager.getDataNetworkType()).thenReturn(TelephonyManager.NETWORK_TYPE_EDGE);
        Assert.assertEquals(WanType.EDGE, Connectivity.wanType(context));
    }

    @Test
    public void testWanTypeCellularNr() {
        TelephonyManager telephonyManager = useCellularTransport();
        when(telephonyManager.getDataNetworkType()).thenReturn(20); // NETWORK_TYPE_NR
        Assert.assertEquals(WanType.NR, Connectivity.wanType(context));
    }

    @Test
    public void testWanTypeCellularPermissionDenied() {
        TelephonyManager telephonyManager = useCellularTransport();
        when(telephonyManager.getDataNetworkType()).thenThrow(new SecurityException("READ_PHONE_STATE"));
        when(telephonyManager.getNetworkType()).thenThrow(new SecurityException("READ_PHONE_STATE"));
        Assert.assertEquals(WanType.CELLULAR, Connectivity.wanType(context));
    }

    @Test
    public void testWanTypeCellularUnmappedSubtype() {
        TelephonyManager telephonyManager = useCellularTransport();
        when(telephonyManager.getDataNetworkType()).thenReturn(TelephonyManager.NETWORK_TYPE_UNKNOWN);
        Assert.assertEquals(WanType.CELLULAR, Connectivity.wanType(context));
    }

    @Test
    public void testWanTypeCellularNoTelephonyManager() {
        useCellularTransport();
        when(context.getSystemService(Context.TELEPHONY_SERVICE)).thenReturn(null);
        Assert.assertEquals(WanType.CELLULAR, Connectivity.wanType(context));
    }
}
