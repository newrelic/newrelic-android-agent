/**
 * Copyright 2021 New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyDisplayInfo;
import android.telephony.TelephonyManager;

import com.newrelic.agent.android.api.common.CarrierType;
import com.newrelic.agent.android.api.common.WanType;
import com.newrelic.agent.android.util.CellularNetworkMonitor;
import com.newrelic.agent.android.util.Connectivity;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.S})
public class ConnectivityTest {
    private SpyContext spyContext;
    private Context context;
    private ConnectivityManager connectivityManager;

    @Before
    public void setUp() throws Exception {
        spyContext = new SpyContext();
        context = spyContext.getContext();
        connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        CellularNetworkMonitor.reset();
    }

    @After
    public void tearDown() {
        CellularNetworkMonitor.reset();
    }

    private void grantPhoneState(boolean granted) {
        int result = granted ? PackageManager.PERMISSION_GRANTED : PackageManager.PERMISSION_DENIED;
        doReturn(result).when(context).checkSelfPermission(Manifest.permission.READ_PHONE_STATE);
        doReturn(result).when(context).checkSelfPermission(Manifest.permission.READ_BASIC_PHONE_STATE);
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
        grantPhoneState(true);
        when(telephonyManager.getDataNetworkType()).thenReturn(TelephonyManager.NETWORK_TYPE_EDGE);
        Assert.assertEquals(WanType.EDGE, Connectivity.wanType(context));
    }

    @Test
    public void testWanTypeCellularNr() {
        TelephonyManager telephonyManager = useCellularTransport();
        grantPhoneState(true);
        when(telephonyManager.getDataNetworkType()).thenReturn(20); // NETWORK_TYPE_NR
        Assert.assertEquals(WanType.NR, Connectivity.wanType(context));
    }

    @Test
    public void testWanTypeCellularPermissionDenied() {
        TelephonyManager telephonyManager = useCellularTransport();
        grantPhoneState(false);
        Assert.assertEquals(WanType.UNKNOWN, Connectivity.wanType(context));
        // Without the permission the telephony manager is never asked, neither for the data
        // network type nor for the deprecated network type that needs the same permission
        verify(telephonyManager, never()).getDataNetworkType();
        verify(telephonyManager, never()).getNetworkType();
    }

    @Test
    public void testWanTypeCellularPermissionRevokedAfterCheck() {
        TelephonyManager telephonyManager = useCellularTransport();
        grantPhoneState(true);
        when(telephonyManager.getDataNetworkType()).thenThrow(new SecurityException("READ_PHONE_STATE"));
        Assert.assertEquals(WanType.UNKNOWN, Connectivity.wanType(context));
        verify(telephonyManager, never()).getNetworkType();
    }

    @Test
    public void testWanTypeCellularTelephonyUnsupported() {
        TelephonyManager telephonyManager = useCellularTransport();
        grantPhoneState(true);
        when(telephonyManager.getDataNetworkType()).thenThrow(new UnsupportedOperationException());
        Assert.assertEquals(WanType.UNKNOWN, Connectivity.wanType(context));
    }

    @Test
    public void testWanTypeCellularMonitoredTechnologyNeedsNoPermission() {
        TelephonyManager telephonyManager = useCellularTransport();
        grantPhoneState(false);
        reportMonitoredTechnology(telephonyManager, TelephonyManager.NETWORK_TYPE_LTE,
                TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE);
        Assert.assertEquals(WanType.LTE, Connectivity.wanType(context));
        verify(telephonyManager, never()).getDataNetworkType();
    }

    @Test
    public void testWanTypeCellularMonitoredNonStandaloneFiveG() {
        TelephonyManager telephonyManager = useCellularTransport();
        grantPhoneState(true);
        // NSA 5G is reported as LTE by getDataNetworkType, the display info knows better
        when(telephonyManager.getDataNetworkType()).thenReturn(TelephonyManager.NETWORK_TYPE_LTE);
        reportMonitoredTechnology(telephonyManager, TelephonyManager.NETWORK_TYPE_LTE,
                TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA);
        Assert.assertEquals(WanType.NR, Connectivity.wanType(context));
    }

    @Test
    public void testWanTypeCellularFallsBackWhenMonitorHasNotReported() {
        TelephonyManager telephonyManager = useCellularTransport();
        grantPhoneState(true);
        when(telephonyManager.getDataNetworkType()).thenReturn(TelephonyManager.NETWORK_TYPE_HSPA);
        Assert.assertEquals(WanType.HSPA, Connectivity.wanType(context));
    }

    private void reportMonitoredTechnology(TelephonyManager telephonyManager, int networkType, int overrideNetworkType) {
        TelephonyDisplayInfo displayInfo = mock(TelephonyDisplayInfo.class);
        when(displayInfo.getNetworkType()).thenReturn(networkType);
        when(displayInfo.getOverrideNetworkType()).thenReturn(overrideNetworkType);

        CellularNetworkMonitor.start(context);
        ArgumentCaptor<TelephonyCallback> captor = ArgumentCaptor.forClass(TelephonyCallback.class);
        verify(telephonyManager).registerTelephonyCallback(any(), captor.capture());
        ((TelephonyCallback.DisplayInfoListener) captor.getValue()).onDisplayInfoChanged(displayInfo);
    }
}
