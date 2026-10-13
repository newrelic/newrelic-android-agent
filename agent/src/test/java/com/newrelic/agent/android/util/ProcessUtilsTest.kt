/*
 * Copyright (c) 2026-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.util

import android.app.ActivityManager
import android.app.ActivityManager.RunningAppProcessInfo
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.Mockito.`when` as whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowApplication

@RunWith(RobolectricTestRunner::class)
class ProcessUtilsTest {

    private val packageName = "com.example.app"

    // --- isMainProcessName: the comparison ---

    @Test
    fun processNameEqualToPackageNameIsMain() {
        assertTrue(ProcessUtils.isMainProcessName(packageName, packageName))
    }

    @Test
    fun colonSuffixedProcessIsSecondary() {
        assertFalse(ProcessUtils.isMainProcessName(packageName, "$packageName:remote"))
    }

    @Test
    fun colonlessCustomProcessIsSecondary() {
        // android:process="com.example.worker" has no colon; a contains(":") test would call it main
        assertFalse(ProcessUtils.isMainProcessName(packageName, "com.example.worker"))
    }

    @Test
    fun nullProcessNameFailsOpen() {
        assertTrue(ProcessUtils.isMainProcessName(packageName, null))
    }

    @Test
    fun emptyProcessNameFailsOpen() {
        assertTrue(ProcessUtils.isMainProcessName(packageName, ""))
    }

    @Test
    fun nullOrEmptyPackageNameFailsOpen() {
        assertTrue(ProcessUtils.isMainProcessName(null, "com.example.app:remote"))
        assertTrue(ProcessUtils.isMainProcessName("", "com.example.app:remote"))
    }

    // --- resolveProcessName: the API-level branch ---

    private fun resolve(sdkInt: Int): Pair<String?, List<String>> {
        val calls = mutableListOf<String>()
        val name = ProcessUtils.resolveProcessName(
            sdkInt,
            { calls.add("platform"); "platform-name" },
            { calls.add("activity-manager"); "activity-manager-name" }
        )
        return name to calls
    }

    @Test
    fun api24And27UseActivityManagerOnly() {
        for (sdk in intArrayOf(24, 27)) {
            val (name, calls) = resolve(sdk)
            assertEquals("sdk $sdk", "activity-manager-name", name)
            assertEquals("sdk $sdk", listOf("activity-manager"), calls)
        }
    }

    @Test
    fun api28And34UsePlatformNameOnly() {
        for (sdk in intArrayOf(28, 34)) {
            val (name, calls) = resolve(sdk)
            assertEquals("sdk $sdk", "platform-name", name)
            assertEquals("sdk $sdk", listOf("platform"), calls)
        }
    }

    // --- findProcessName: pick our own entry out of runningAppProcesses ---

    private fun process(name: String, pid: Int) = RunningAppProcessInfo(name, pid, arrayOf(packageName))

    @Test
    fun findsTheEntryWithMatchingPid() {
        val processes = listOf(
            process("$packageName:remote", 100),
            process(packageName, 200),
            process("com.other.app", 300)
        )
        assertEquals("$packageName:remote", ProcessUtils.findProcessName(processes, 100))
        assertEquals(packageName, ProcessUtils.findProcessName(processes, 200))
    }

    @Test
    fun noMatchingPidIsNull() {
        assertNull(ProcessUtils.findProcessName(listOf(process(packageName, 200)), 999))
    }

    @Test
    fun nullOrEmptyProcessListIsNull() {
        assertNull(ProcessUtils.findProcessName(null, 200))
        assertNull(ProcessUtils.findProcessName(emptyList(), 200))
    }

    // --- isMainProcess(Context): wiring + fail-open ---

    @Test
    fun contextThatThrowsFailsOpen() {
        val context = Mockito.mock(Context::class.java)
        whenever(context.packageName).thenThrow(RuntimeException("boom"))
        assertTrue(ProcessUtils.isMainProcess(context))
    }

    @Test
    fun mainProcessIsDetectedViaApplicationGetProcessName() {
        // Robolectric defaults the process name to the package name
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertTrue(ProcessUtils.isMainProcess(context))
    }

    @Test
    fun colonSuffixedProcessIsDetectedViaApplicationGetProcessName() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        ShadowApplication.setProcessName("${context.packageName}:remote")
        assertFalse(ProcessUtils.isMainProcess(context))
    }

    @Test
    fun colonlessProcessIsDetectedViaApplicationGetProcessName() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        ShadowApplication.setProcessName("com.example.worker")
        assertFalse(ProcessUtils.isMainProcess(context))
    }

    // --- isMainProcess(Context) below API 28: ActivityManager.runningAppProcesses path ---
    // sdk 26 (8.0) is below P and its android-all jar is already in the local Robolectric cache.

    private fun seedProcesses(context: Context, vararg processes: RunningAppProcessInfo) {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        shadowOf(activityManager).setProcesses(processes.toList())
    }

    @Test
    @Config(sdk = [26])
    fun preApi28MainProcessIsDetectedViaRunningAppProcesses() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        seedProcesses(context, process(context.packageName, android.os.Process.myPid()))
        assertTrue(ProcessUtils.isMainProcess(context))
    }

    @Test
    @Config(sdk = [26])
    fun preApi28SecondaryProcessIsDetectedViaRunningAppProcesses() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        seedProcesses(
            context,
            process("${context.packageName}:remote", android.os.Process.myPid()),
            process(context.packageName, android.os.Process.myPid() + 1)
        )
        assertFalse(ProcessUtils.isMainProcess(context))
    }

    @Test
    @Config(sdk = [26])
    fun preApi28UnresolvableProcessFailsOpen() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        seedProcesses(context) // no entry for our pid
        assertTrue(ProcessUtils.isMainProcess(context))
    }
}
