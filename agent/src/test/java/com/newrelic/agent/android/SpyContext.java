/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Debug;
import android.os.Process;
import android.telephony.TelephonyManager;

import androidx.test.core.app.ApplicationProvider;

import com.newrelic.agent.android.api.common.CarrierType;

import org.junit.Assert;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

public class SpyContext {

    public static final String APP_VERSION_NAME = "1.1";
    public static final int APP_VERSION_CODE = 99;
    public static final long APP_LONG_VERSION_CODE = 99L;
    public static final int APP_MEMORY = 0xbadf00d;

    private Context context = ApplicationProvider.getApplicationContext();
    private Context contextSpy = spy(context);
    private PackageManager packageManager = ApplicationProvider.getApplicationContext().getPackageManager();

    public SpyContext() {
        provideSpyContext();
    }

    public void provideSpyContext() {
        contextSpy = spy(context.getApplicationContext());
        packageManager = spy(context.getPackageManager());

        when(contextSpy.getPackageManager()).thenReturn(packageManager);

        provideActivityManagers(contextSpy);

        final PackageInfo packageInfo = providePackageInfo(contextSpy);
        final ApplicationInfo applicationInfo = provideApplicationInfo(contextSpy);
        try {
            when(packageManager.getPackageInfo(contextSpy.getPackageName(), 0)).thenReturn(packageInfo);
            when(packageManager.getApplicationInfo(contextSpy.getPackageName(), 0)).thenReturn(applicationInfo);
        } catch (PackageManager.NameNotFoundException e) {
        }
    }

    public Context getContext() {
        return contextSpy;
    }

    private void provideActivityManagers(Context context) {
        int[] pids = {Process.myPid()};
        final Debug.MemoryInfo[] memInfo = new Debug.MemoryInfo[2];

        memInfo[0] = mock(Debug.MemoryInfo.class);
        memInfo[1] = mock(Debug.MemoryInfo.class);
        for (Debug.MemoryInfo memoryInfo : memInfo) {
            when(memoryInfo.getTotalPss()).thenReturn(APP_MEMORY);
        }

        final ConnectivityManager connectivityManager = spy((ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE));
        
        // Setup connectivity based on API level
        setupConnectivity(context, connectivityManager);
        
        when(context.getSystemService(Context.CONNECTIVITY_SERVICE)).thenReturn(connectivityManager);

        final TelephonyManager telephonyManager = spy((TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE));
        when(telephonyManager.getNetworkOperatorName()).thenReturn(CarrierType.WIFI);
        when(context.getSystemService(Context.TELEPHONY_SERVICE)).thenReturn(telephonyManager);

        final ActivityManager activityManager = spy((ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE));
        when(activityManager.getProcessMemoryInfo(pids)).thenReturn(memInfo);
        when(context.getSystemService(Context.ACTIVITY_SERVICE)).thenReturn(activityManager);
    }
    
    /**
     * Sets up connectivity information based on API level
     */
    private void setupConnectivity(Context context, ConnectivityManager connectivityManager) {
            // For API 23+ (Android 6.0+), use the new NetworkCapabilities API
            setupModernConnectivity(connectivityManager);

    }
    
    /**
     * Sets up connectivity using the modern NetworkCapabilities API (API 23+)
     */
    private void setupModernConnectivity(ConnectivityManager connectivityManager) {
        Network network = mock(Network.class);
        NetworkCapabilities networkCapabilities = mock(NetworkCapabilities.class);
        
        // Mock a WiFi connection
        when(networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)).thenReturn(true);
        when(networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)).thenReturn(true);
        when(networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)).thenReturn(true);
        
        when(connectivityManager.getActiveNetwork()).thenReturn(network);
        when(connectivityManager.getNetworkCapabilities(network)).thenReturn(networkCapabilities);
        
        // Also set up legacy NetworkInfo for backward compatibility
    }

    private PackageInfo providePackageInfo(Context context) {
        PackageManager packageManager = context.getPackageManager();
        PackageInfo packageInfo = null;

        try {
            packageInfo = packageManager.getPackageInfo(context.getPackageName(), 0);
            packageInfo.versionName = APP_VERSION_NAME;
            
            // Set version code based on API level
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // For API 28 (Android 9.0) and above, use the new method
                setLongVersionCode(packageInfo, APP_LONG_VERSION_CODE);
            } else {
                // For older Android versions, use the deprecated field
                setLegacyVersionCode(packageInfo, APP_VERSION_CODE);
            }
        } catch (PackageManager.NameNotFoundException e) {
            Assert.fail();
        }

        return packageInfo;
    }
    
    /**
     * Sets the long version code for API 28+ using reflection to avoid direct dependency
     * on API 28 methods which would cause compilation issues on older build tools.
     * 
     * @param packageInfo The PackageInfo object
     * @param versionCode The long version code to set
     */
    private void setLongVersionCode(PackageInfo packageInfo, long versionCode) {
        try {
            // Try to use the new API if available
            packageInfo.getClass().getMethod("setLongVersionCode", long.class)
                    .invoke(packageInfo, versionCode);
        } catch (Exception e) {
            // Fall back to the deprecated field if the new API is not available
            setLegacyVersionCode(packageInfo, (int) versionCode);
        }
    }
    
    /**
     * Sets the legacy version code for backward compatibility
     * 
     * @param packageInfo The PackageInfo object
     * @param versionCode The int version code to set
     */
    @SuppressWarnings("deprecation")
    private void setLegacyVersionCode(PackageInfo packageInfo, int versionCode) {
        packageInfo.versionCode = versionCode;
    }

    private ApplicationInfo provideApplicationInfo(Context context) {
        PackageManager packageManager = context.getPackageManager();
        ApplicationInfo applicationInfo = null;

        try {
            applicationInfo = spy(packageManager.getApplicationInfo(context.getPackageName(), 0));
            applicationInfo.name = getClass().getSimpleName();
        } catch (PackageManager.NameNotFoundException e) {
            Assert.fail();
        }

        return applicationInfo;
    }

}
