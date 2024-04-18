/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.util;

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

import java.text.MessageFormat;

public final class Connectivity {
    private static final String ANDROID = "Android";

    private static AgentLog log = AgentLogManager.getAgentLog();

    public static String carrierNameFromContext(final Context context) {
        final android.net.NetworkInfo networkInfo;
        final NetworkCapabilities networkCapabilities;
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Network nw = cm.getActiveNetwork();
            if (nw != null) {
                networkCapabilities = cm.getNetworkCapabilities(nw);
                if (!isConnected(networkCapabilities)) {
                    return CarrierType.NONE;
                } else if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                    return CarrierType.ETHERNET;
                } else if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                    return carrierNameFromTelephonyManager(context);
                } else if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    return CarrierType.WIFI;
                } else if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) {
                    return CarrierType.BLUETOOTH;
                } else {
                    log.warn("Unknown network type.");
                    return CarrierType.UNKNOWN;
                }
            } else {
                log.warn("Network is not available");
                return null;
            }
        } else {
            try {
                networkInfo = getNetworkInfo(context);
            } catch (SecurityException e) {
                return CarrierType.UNKNOWN;
            }

            if (!isConnected(networkInfo)) {
                return CarrierType.NONE;
            } else if (isHardwired(networkInfo)) {
                return CarrierType.ETHERNET;
            } else if (isWan(networkInfo)) {
                return carrierNameFromTelephonyManager(context);
            } else if (isWifi(networkInfo)) {
                return CarrierType.WIFI;
            } else if (isBluetooth(networkInfo)) {
                return CarrierType.BLUETOOTH;
            } else {
                log.warn(MessageFormat.format("Unknown network type: {0} [{1}]", networkInfo.getTypeName(), networkInfo.getType()));
                return CarrierType.UNKNOWN;
            }
        }
    }

    public static String wanType(final Context context) {
        final android.net.NetworkInfo networkInfo;
        try {
            networkInfo = getNetworkInfo(context);
        } catch (SecurityException e) {
            return WanType.UNKNOWN;
        }

        if (!isConnected(networkInfo)) {
            return WanType.NONE;
        } else if (isWifi(networkInfo)) {
            return WanType.WIFI;
        } else if (isHardwired(networkInfo)) {
            return connectionNameFromNetworkSubtype(networkInfo.getSubtype());
        } else if (isWan(networkInfo)) {
            return connectionNameFromNetworkSubtype(networkInfo.getSubtype());
        } else {
            return WanType.UNKNOWN;
        }
    }

    private static boolean isConnected(final android.net.NetworkInfo networkInfo) {
        return networkInfo != null && networkInfo.isConnected();
    }

    private static boolean isConnected(final NetworkCapabilities networkCapabilities) {
        return networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                || networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                || networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET);
    }

    private static boolean isWan(final android.net.NetworkInfo networkInfo) {
        switch (networkInfo.getType()) {
            case ConnectivityManager.TYPE_MOBILE:
            case ConnectivityManager.TYPE_MOBILE_DUN:
            case ConnectivityManager.TYPE_MOBILE_HIPRI:
            case ConnectivityManager.TYPE_MOBILE_MMS:
            case ConnectivityManager.TYPE_MOBILE_SUPL:
            case ConnectivityManager.TYPE_ETHERNET:
                return true;
            default:
                return false;
        }
    }

    private static boolean isWifi(final android.net.NetworkInfo networkInfo) {
        switch (networkInfo.getType()) {
            case ConnectivityManager.TYPE_WIFI:
            case ConnectivityManager.TYPE_WIMAX:
                return true;
            default:
                return false;
        }
    }

    private static boolean isHardwired(android.net.NetworkInfo networkInfo) {
        switch (networkInfo.getType()) {
            case ConnectivityManager.TYPE_ETHERNET:
            case ConnectivityManager.TYPE_MOBILE_DUN:   // Maybe? (tethering)
                return true;
            default:
                return false;
        }
    }

    private static boolean isBluetooth(android.net.NetworkInfo networkInfo) {
        switch (networkInfo.getType()) {
            case ConnectivityManager.TYPE_BLUETOOTH:
                return true;
            default:
                return false;
        }
    }

    private static android.net.NetworkInfo getNetworkInfo(final Context context) throws SecurityException {
        final ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        try {
            return connectivityManager.getActiveNetworkInfo();
        } catch (SecurityException e) {
            log.warn("Cannot determine network state. Enable android.permission.ACCESS_NETWORK_STATE in your manifest.");
            throw e;
        }
    }

    private static String carrierNameFromTelephonyManager(final Context context) {
        final TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
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
}
