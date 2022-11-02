/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class InstantApps {

    private static Boolean isInstantApp = null;
    private static Context lastApplicationContext = null;
    private static PackageManagerWrapper packageManagerWrapper = null;

    public static boolean isInstantApp(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("Context must be non-null");
        }
        Context applicationContext = context.getApplicationContext();
        if (applicationContext == null) {
            throw new IllegalStateException("Application context is null!");
        }
        if ((isInstantApp != null) && (applicationContext.equals(lastApplicationContext))) {
            return isInstantApp.booleanValue();
        }
        isInstantApp = null;

        Boolean isInstantAppResult = null;
        if (isAtLeastO()) {
            if ((packageManagerWrapper == null) ||
                    (!applicationContext.equals(lastApplicationContext))) {
                packageManagerWrapper = new PackageManagerWrapper(applicationContext.getPackageManager());
            }
            isInstantAppResult = packageManagerWrapper.isInstantApp();
        }
        lastApplicationContext = applicationContext;
        if (isInstantAppResult != null) {
            isInstantApp = isInstantAppResult;
        } else {
            try {
                applicationContext.getClassLoader().loadClass("com.google.android.instantapps.supervisor.InstantAppsRuntime");
                isInstantApp = Boolean.valueOf(true);
            } catch (ClassNotFoundException e) {
                isInstantApp = Boolean.valueOf(false);
            }
        }
        return isInstantApp.booleanValue();
    }

    private static boolean isAtLeastO() {
        return Build.VERSION.SDK_INT >= 26;
    }

    static class PackageManagerWrapper {
        private final PackageManager packageManager;
        private static Method isInstantAppMethod;

        PackageManagerWrapper(PackageManager packageManager) {
            this.packageManager = packageManager;
        }

        Boolean isInstantApp() {
            /*
            if (!InstantApps.access$000()) {
                return null;
            }
            /**/
            if (isInstantAppMethod == null) {
                try {
                    isInstantAppMethod = PackageManager.class.getDeclaredMethod("isInstantApp", new Class[0]);
                } catch (NoSuchMethodException ignored) {
                    return null;
                }
            }
            try {
                return (Boolean) isInstantAppMethod.invoke(this.packageManager, new Object[0]);
            } catch (InvocationTargetException ignored) {
            } catch (IllegalAccessException ignored) {
            }
            return null;
        }
    }



}
