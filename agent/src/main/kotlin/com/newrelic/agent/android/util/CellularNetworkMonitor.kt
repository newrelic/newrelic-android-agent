/*
 * Copyright (c) 2026-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.util

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.telephony.TelephonyCallback
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import androidx.annotation.VisibleForTesting
import com.newrelic.agent.android.api.common.WanType
import com.newrelic.agent.android.logging.AgentLogManager

/**
 * Tracks the cellular network technology the device is using for data, without requiring
 * READ_PHONE_STATE.
 *
 * From Android 12 (API 31) a [TelephonyCallback.DisplayInfoListener] can be registered with no
 * permission. It also reports the override network type, which is the only place a non-standalone
 * 5G connection shows up: `TelephonyManager.getDataNetworkType()` reports LTE for those.
 *
 * Before API 31 this class does nothing, and [Connectivity] falls back to reading the network
 * type directly when the host app already holds READ_PHONE_STATE.
 */
object CellularNetworkMonitor {
    private val log = AgentLogManager.getAgentLog()

    private val lock = Any()

    /**
     * Declared as [Any] so that loading this class on API < 31 never has to resolve a type that
     * does not exist there.
     */
    private var displayInfoCallback: Any? = null

    /**
     * The last reported technology. Kept after [stop]: the registration is dropped while the app
     * is backgrounded, and from API 31 the listener is the only permission-free source, so
     * clearing it would report "unknown" for every event until the listener fires again.
     */
    @Volatile
    private var cachedWanType: String? = null

    /**
     * The WanType of the current cellular technology, or null if it is not (yet) known.
     */
    @JvmStatic
    fun getWanType(): String? = cachedWanType

    /**
     * Drops the registration state and the cached technology.
     */
    @JvmStatic
    @VisibleForTesting
    fun reset() {
        synchronized(lock) {
            displayInfoCallback = null
            cachedWanType = null
        }
    }

    /**
     * Starts listening for display info changes on API 31 and above. Safe to call repeatedly.
     */
    @JvmStatic
    @SuppressLint("NewApi")
    fun start(context: Context?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || context == null) {
            return
        }

        synchronized(lock) {
            if (displayInfoCallback != null) {
                return
            }

            val telephonyManager = getTelephonyManager(context) ?: return

            try {
                val callback = DisplayInfoCallback()
                // The callback only maps and stores a value, so run it on the binder thread the
                // framework calls it on instead of handing it an executor that could go stale
                telephonyManager.registerTelephonyCallback({ it.run() }, callback)
                displayInfoCallback = callback
                log.debug("CellularNetworkMonitor: listening for cellular network technology changes")
            } catch (e: RuntimeException) {
                // Devices without telephony support, or processes not allowed to listen. The
                // technology is optional data, so fall back to the permission-gated path.
                rethrowUnlessTelephonyFailure(e)
                log.debug("CellularNetworkMonitor: cannot listen for cellular network technology changes: $e")
            }
        }
    }

    /**
     * Stops listening for display info changes.
     */
    @JvmStatic
    @SuppressLint("NewApi")
    fun stop(context: Context?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || context == null) {
            return
        }

        synchronized(lock) {
            val callback = displayInfoCallback ?: return

            // Keep the reference if the manager is gone, so a later stop can still reach the
            // registered callback
            val telephonyManager = getTelephonyManager(context) ?: return

            try {
                telephonyManager.unregisterTelephonyCallback(callback as TelephonyCallback)
                displayInfoCallback = null
                log.debug("CellularNetworkMonitor: stopped listening for cellular network technology changes")
            } catch (e: RuntimeException) {
                rethrowUnlessTelephonyFailure(e)
                log.debug("CellularNetworkMonitor: cannot stop listening for cellular network technology changes: $e")
            }
        }
    }

    /**
     * Maps a display info to a WanType. The override network type wins, so a device that shows 5G
     * to the user is also reported as 5G.
     */
    @VisibleForTesting
    @SuppressLint("NewApi")
    @Suppress("DEPRECATION")
    internal fun toWanType(displayInfo: TelephonyDisplayInfo): String =
        when (displayInfo.overrideNetworkType) {
            TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA,
            TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED,
                // Deprecated in favor of NR_ADVANCED but still reported on Android 11 and 12. Its
                // underlying network type is LTE, so without this case mmWave 5G is reported as LTE.
            TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA_MMWAVE -> WanType.NR

            else -> Connectivity.connectionNameFromNetworkSubtype(displayInfo.networkType)
        }

    private fun getTelephonyManager(context: Context): TelephonyManager? =
        context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

    private fun rethrowUnlessTelephonyFailure(e: RuntimeException) {
        if (e !is SecurityException && e !is IllegalStateException && e !is UnsupportedOperationException) {
            throw e
        }
    }

    /**
     * Caches the display info so that reading the technology never blocks on telephony.
     */
    @SuppressLint("NewApi")
    private class DisplayInfoCallback : TelephonyCallback(), TelephonyCallback.DisplayInfoListener {
        override fun onDisplayInfoChanged(telephonyDisplayInfo: TelephonyDisplayInfo) {
            cachedWanType = toWanType(telephonyDisplayInfo)
        }
    }
}
