/*
 * Copyright (c) 2026-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.util

import android.content.Context
import android.os.Build
import android.telephony.TelephonyCallback
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import com.newrelic.agent.android.api.common.WanType
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.`when` as whenever
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.Executor

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.S])
class CellularNetworkMonitorTest {
    private lateinit var context: Context
    private lateinit var telephonyManager: TelephonyManager

    @Before
    fun setUp() {
        CellularNetworkMonitor.reset()
        context = mock(Context::class.java)
        telephonyManager = mock(TelephonyManager::class.java)
        whenever(context.getSystemService(Context.TELEPHONY_SERVICE)).thenReturn(telephonyManager)
    }

    @After
    fun tearDown() {
        CellularNetworkMonitor.reset()
    }

    private fun displayInfo(networkType: Int, overrideNetworkType: Int): TelephonyDisplayInfo {
        val displayInfo = mock(TelephonyDisplayInfo::class.java)
        whenever(displayInfo.networkType).thenReturn(networkType)
        whenever(displayInfo.overrideNetworkType).thenReturn(overrideNetworkType)
        return displayInfo
    }

    private fun startAndCaptureListener(): TelephonyCallback.DisplayInfoListener {
        CellularNetworkMonitor.start(context)
        val captor = ArgumentCaptor.forClass(TelephonyCallback::class.java)
        verify(telephonyManager).registerTelephonyCallback(any(Executor::class.java), captor.capture())
        return captor.value as TelephonyCallback.DisplayInfoListener
    }

    @Test
    fun toWanTypeMapsNetworkTypeWhenThereIsNoOverride() {
        Assert.assertEquals(
            WanType.LTE,
            CellularNetworkMonitor.toWanType(
                displayInfo(TelephonyManager.NETWORK_TYPE_LTE, TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE)
            )
        )
        Assert.assertEquals(
            WanType.EDGE,
            CellularNetworkMonitor.toWanType(
                displayInfo(TelephonyManager.NETWORK_TYPE_EDGE, TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE)
            )
        )
    }

    @Test
    fun toWanTypeReportsNonStandaloneFiveGAsNr() {
        // NSA 5G rides on an LTE anchor, so the network type alone says LTE
        Assert.assertEquals(
            WanType.NR,
            CellularNetworkMonitor.toWanType(
                displayInfo(TelephonyManager.NETWORK_TYPE_LTE, TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA)
            )
        )
        Assert.assertEquals(
            WanType.NR,
            CellularNetworkMonitor.toWanType(
                displayInfo(TelephonyManager.NETWORK_TYPE_LTE, TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED)
            )
        )
    }

    @Test
    @Suppress("DEPRECATION")
    fun toWanTypeReportsDeprecatedMmWaveOverrideAsNr() {
        Assert.assertEquals(
            WanType.NR,
            CellularNetworkMonitor.toWanType(
                displayInfo(TelephonyManager.NETWORK_TYPE_LTE, TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA_MMWAVE)
            )
        )
    }

    @Test
    fun toWanTypeKeepsLteForLteCarrierAggregationOverrides() {
        Assert.assertEquals(
            WanType.LTE,
            CellularNetworkMonitor.toWanType(
                displayInfo(TelephonyManager.NETWORK_TYPE_LTE, TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_CA)
            )
        )
    }

    @Test
    fun toWanTypeReportsUnknownForUnmappedNetworkType() {
        Assert.assertEquals(
            WanType.UNKNOWN,
            CellularNetworkMonitor.toWanType(
                displayInfo(TelephonyManager.NETWORK_TYPE_UNKNOWN, TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE)
            )
        )
    }

    @Test
    fun wanTypeIsNullUntilTheListenerReports() {
        startAndCaptureListener()
        Assert.assertNull(CellularNetworkMonitor.getWanType())
    }

    @Test
    fun listenerUpdatesWanType() {
        val listener = startAndCaptureListener()

        listener.onDisplayInfoChanged(
            displayInfo(TelephonyManager.NETWORK_TYPE_LTE, TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA)
        )
        Assert.assertEquals(WanType.NR, CellularNetworkMonitor.getWanType())

        listener.onDisplayInfoChanged(
            displayInfo(TelephonyManager.NETWORK_TYPE_LTE, TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE)
        )
        Assert.assertEquals(WanType.LTE, CellularNetworkMonitor.getWanType())
    }

    @Test
    fun startingTwiceRegistersOneCallback() {
        CellularNetworkMonitor.start(context)
        CellularNetworkMonitor.start(context)

        verify(telephonyManager, times(1))
            .registerTelephonyCallback(any(Executor::class.java), any(TelephonyCallback::class.java))
    }

    @Test
    fun stopUnregistersTheRegisteredCallback() {
        val listener = startAndCaptureListener()

        CellularNetworkMonitor.stop(context)

        verify(telephonyManager).unregisterTelephonyCallback(listener as TelephonyCallback)
    }

    @Test
    fun stopKeepsTheLastReportedWanType() {
        val listener = startAndCaptureListener()
        listener.onDisplayInfoChanged(
            displayInfo(TelephonyManager.NETWORK_TYPE_LTE, TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA)
        )

        CellularNetworkMonitor.stop(context)

        Assert.assertEquals(WanType.NR, CellularNetworkMonitor.getWanType())
    }

    @Test
    fun stopWithoutStartDoesNothing() {
        CellularNetworkMonitor.stop(context)

        verifyNoInteractions(telephonyManager)
    }

    @Test
    fun stopWithoutTelephonyManagerKeepsTheCallbackForALaterAttempt() {
        val listener = startAndCaptureListener()
        whenever(context.getSystemService(Context.TELEPHONY_SERVICE)).thenReturn(null)

        CellularNetworkMonitor.stop(context)
        verify(telephonyManager, never()).unregisterTelephonyCallback(any(TelephonyCallback::class.java))

        whenever(context.getSystemService(Context.TELEPHONY_SERVICE)).thenReturn(telephonyManager)
        CellularNetworkMonitor.stop(context)
        verify(telephonyManager).unregisterTelephonyCallback(listener as TelephonyCallback)
    }

    @Test
    fun startSwallowsRegistrationFailures() {
        doThrow(SecurityException("not allowed"))
            .`when`(telephonyManager)
            .registerTelephonyCallback(any(Executor::class.java), any(TelephonyCallback::class.java))

        CellularNetworkMonitor.start(context)

        Assert.assertNull(CellularNetworkMonitor.getWanType())
    }

    @Test
    fun startWithoutTelephonyManagerDoesNothing() {
        whenever(context.getSystemService(Context.TELEPHONY_SERVICE)).thenReturn(null)

        CellularNetworkMonitor.start(context)

        verifyNoInteractions(telephonyManager)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.R])
    fun startDoesNothingBelowApi31() {
        CellularNetworkMonitor.start(context)

        verifyNoInteractions(telephonyManager)
    }
}
