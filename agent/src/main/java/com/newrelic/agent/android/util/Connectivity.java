/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.util;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.telephony.TelephonyManager;

import com.newrelic.agent.android.api.common.CarrierType;
import com.newrelic.agent.android.api.common.WanType;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;


public final class Connectivity {
    private static final String ANDROID = "Android";

    private static AgentLog log = AgentLogManager.getAgentLog();

    public static String carrierNameFromContext(final Context context) {         // Modern approach for Android M (API 23) and above
            return getCarrierName(context);

    }

    public static String wanType(final Context context) {
            return getWanType(context);

    }


    private static String getCarrierName(final Context context) {
        final ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) {
            return CarrierType.UNKNOWN;
        }

        try {
            Network network = connectivityManager.getActiveNetwork();
            if (network == null) {
                return CarrierType.NONE;
            }

            NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
            if (capabilities == null) {
                return CarrierType.UNKNOWN;
            }

            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                return CarrierType.ETHERNET;
            } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                return carrierNameFromTelephonyManager(context);
            } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                return CarrierType.WIFI;
            } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) {
                return CarrierType.BLUETOOTH;
            } else {
                return CarrierType.UNKNOWN;
            }
        } catch (SecurityException e) {
            log.warn("Cannot determine network state. Enable android.permission.ACCESS_NETWORK_STATE in your manifest.");
            return CarrierType.UNKNOWN;
        }
    }

    private static String getWanType(final Context context) {
        final ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) {
            return WanType.UNKNOWN;
        }

        try {
            Network network = connectivityManager.getActiveNetwork();
            if (network == null) {
                return WanType.NONE;
            }

            NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
            if (capabilities == null) {
                return WanType.UNKNOWN;
            }

            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                return WanType.WIFI;
            } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
                if (telephonyManager != null) {
                    return connectionNameFromNetworkSubtype(getNetworkType(telephonyManager));
                }
            } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                return WanType.ETHERNET;
            }
            
            return WanType.UNKNOWN;
        } catch (SecurityException e) {
            log.warn("Cannot determine network state. Enable android.permission.ACCESS_NETWORK_STATE in your manifest.");
            return WanType.UNKNOWN;
        }
    }

    private static String carrierNameFromTelephonyManager(final Context context) {
        final TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        if (telephonyManager == null) {
            return CarrierType.UNKNOWN;
        }
        
        final String networkOperator = telephonyManager.getNetworkOperatorName();

        if (networkOperator == null || networkOperator.isEmpty()) {
            return CarrierType.UNKNOWN;
        }

        final boolean smellsLikeAnEmulator = (Build.PRODUCT.equals("google_sdk") || Build.PRODUCT.equals("sdk") || Build.PRODUCT.equals("sdk_x86") || Build.FINGERPRINT.startsWith("generic"));

        if (networkOperator.equals(ANDROID) && smellsLikeAnEmulator) {
            //
            // Emulator gives us a fake network operator. Pretend that we're on wifi align with the iOS agent.
            //
            return CarrierType.WIFI;
        } else {
            return networkOperator;
        }
    }

    private static String connectionNameFromNetworkSubtype(final int subType) {
        switch (subType) {
            case TelephonyManager.NETWORK_TYPE_1xRTT:
                return WanType.RTT; // ?
            case TelephonyManager.NETWORK_TYPE_CDMA:
                return WanType.CDMA;
            case TelephonyManager.NETWORK_TYPE_EDGE:
                return WanType.EDGE;
            case TelephonyManager.NETWORK_TYPE_EVDO_0:
                return WanType.EVDO_REV_0;
            case TelephonyManager.NETWORK_TYPE_EVDO_A:
                return WanType.EVDO_REV_A;
            case TelephonyManager.NETWORK_TYPE_GPRS:
                return WanType.GPRS;
            case TelephonyManager.NETWORK_TYPE_HSDPA:
                return WanType.HSDPA;
            case TelephonyManager.NETWORK_TYPE_HSPA:
                return WanType.HSPA;
            case TelephonyManager.NETWORK_TYPE_HSUPA:
                return WanType.HSUPA;
            case TelephonyManager.NETWORK_TYPE_UMTS:
                return WanType.UMTS;
            case TelephonyManager.NETWORK_TYPE_IDEN: // API level 8
                return WanType.IDEN;
            /*
             * the following require API > 8, which we to not target
            */
            case 12:// TelephonyManager.NETWORK_TYPE_EVDO_B: (API level 9)
                return WanType.EVDO_REV_B;
            case 15://TelephonyManager.NETWORK_TYPE_HSPAP: (API level 13)
                return WanType.HSPAP;
            case 14:// TelephonyManager.NETWORK_TYPE_EHRPD: (API level 11)
                return WanType.HRPD;
            case 13:// TelephonyManager.NETWORK_TYPE_LTE: (API level 11)
                return WanType.LTE;
            case TelephonyManager.NETWORK_TYPE_UNKNOWN:
            default:
                return WanType.UNKNOWN;
        }
    }

    /**
     * Gets the network type from TelephonyManager in a way that handles API deprecation.
     * 
     * @param telephonyManager The TelephonyManager instance
     * @return The network type as an integer
     */
    @SuppressLint("MissingPermission")
    @SuppressWarnings("deprecation")
    private static int getNetworkType(TelephonyManager telephonyManager) {
            // For API 24 (Android 7.0) and above, we should use getDataNetworkType
            // This requires READ_PHONE_STATE permission
            try {
                return telephonyManager.getDataNetworkType();
            } catch (SecurityException e) {
                // Fall back to deprecated method if permission is not granted
                log.warn("Cannot determine network type. Enable android.permission.READ_PHONE_STATE in your manifest.");
                return telephonyManager.getNetworkType();
            }
    }
}