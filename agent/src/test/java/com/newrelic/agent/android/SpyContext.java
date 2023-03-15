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
import android.net.NetworkInfo;
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
        final NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        when(connectivityManager.getActiveNetworkInfo()).thenReturn(networkInfo);
        when(context.getSystemService(Context.CONNECTIVITY_SERVICE)).thenReturn(connectivityManager);

        final TelephonyManager telephonyManager = spy((TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE));
        when(telephonyManager.getNetworkOperatorName()).thenReturn(CarrierType.WIFI);
        when(context.getSystemService(Context.TELEPHONY_SERVICE)).thenReturn(telephonyManager);

        final ActivityManager activityManager = spy((ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE));
        when(activityManager.getProcessMemoryInfo(pids)).thenReturn(memInfo);
        when(context.getSystemService(Context.ACTIVITY_SERVICE)).thenReturn(activityManager);
    }

    private PackageInfo providePackageInfo(Context context) {
        PackageManager packageManager = context.getPackageManager();
        PackageInfo packageInfo = null;

        try {
            packageInfo = packageManager.getPackageInfo(context.getPackageName(), 0);
            packageInfo.versionName = APP_VERSION_NAME;
            packageInfo.versionCode = APP_VERSION_CODE;
        } catch (PackageManager.NameNotFoundException e) {
            Assert.fail();
        }

        return packageInfo;
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
