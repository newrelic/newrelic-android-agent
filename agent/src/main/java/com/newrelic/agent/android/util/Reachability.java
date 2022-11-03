/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.util;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import java.net.InetSocketAddress;
import java.net.Socket;

public class Reachability {
    private static final int REACHABILITY_TIMEOUT = 500;        // 0.5 seconds

    @SuppressLint("NewApi")
    public static boolean hasReachableNetworkConnection(final Context context, final String reachableHost) {
        boolean isReachable = false;
        try {
            ConnectivityManager cm =
                    (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                NetworkInfo activeNetwork = cm.getActiveNetworkInfo();

                if (activeNetwork != null) {
                    isReachable = activeNetwork.isConnectedOrConnecting();
                }

                // if connection is active and a host was specified, verify it is reachable
                if (isReachable && reachableHost != null) {
                    try (Socket reachableSocket = new Socket()) {
                        // will throw if unreachable
                        reachableSocket.connect(new InetSocketAddress(reachableHost, 443), REACHABILITY_TIMEOUT);
                    }
                }
            } else {
                // couldn't get the ConnectivityManager, so return true and hope for the best
                isReachable = true;
            }

        } catch (Exception e) {
            isReachable = false;
        }

        return isReachable;
    }

}
