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
import android.net.NetworkRequest;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

public class Reachability {
    private static final int REACHABILITY_TIMEOUT = 500;        // 0.5 seconds

    // Fail-open default: matches prior behavior when ConnectivityManager is unavailable,
    // and avoids false-negative "offline" tags before the first callback fires.
    private static final AtomicBoolean hasReachableNetwork = new AtomicBoolean(true);
    private static final AtomicBoolean callbackRegistered = new AtomicBoolean(false);

    /**
     * Register a default-network callback so reachability is updated asynchronously
     * instead of re-queried via Binder IPC on every call. Safe to invoke multiple times;
     * subsequent calls are no-ops. Uses {@code registerNetworkCallback(NetworkRequest, ...)}
     * which is available since API 21 (some customers override minSdk to 21 for Fire TV).
     */
    public static void init(final Context context) {
        if (context == null || !callbackRegistered.compareAndSet(false, true)) {
            return;
        }
        try {
            ConnectivityManager cm =
                    (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) {
                return;
            }
            NetworkRequest request = new NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build();
            cm.registerNetworkCallback(request, new ConnectivityManager.NetworkCallback() {
                @Override
                public void onCapabilitiesChanged(Network network, NetworkCapabilities capabilities) {
                    hasReachableNetwork.set(hasRelevantTransport(capabilities));
                }

                @Override
                public void onLost(Network network) {
                    hasReachableNetwork.set(false);
                }
            });
        } catch (NoSuchMethodError e) {
            // Network callback APIs unavailable on this device; stay fail-open.
            callbackRegistered.set(false);
        } catch (Exception e) {
            // Registration failed; allow a retry on the next init() call.
            callbackRegistered.set(false);
        }
    }

    static boolean hasRelevantTransport(NetworkCapabilities capabilities) {
        return capabilities != null &&
                (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN));
    }

    @SuppressLint("NewApi")
    public static boolean hasReachableNetworkConnection(final Context context, final String reachableHost) {
        boolean isReachable = hasReachableNetwork.get();

        // if connection is active and a host was specified, verify it is reachable
        if (isReachable && reachableHost != null) {
            try (Socket reachableSocket = new Socket()) {
                // will throw if unreachable
                reachableSocket.connect(new InetSocketAddress(reachableHost, 443), REACHABILITY_TIMEOUT);
            } catch (Exception e) {
                isReachable = false;
            }
        }

        return isReachable;
    }
}