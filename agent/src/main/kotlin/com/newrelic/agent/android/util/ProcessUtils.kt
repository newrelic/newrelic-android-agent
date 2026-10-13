/*
 * Copyright (c) 2026-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.util

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Process
import com.newrelic.agent.android.logging.AgentLogManager

/**
 * Tells the main process from secondary processes (`android:process` services and the like).
 *
 * The main process is the one named like the app's default process: the package name, unless the
 * manifest sets `android:process` on `<application>`. Secondary processes are either
 * `<package>:<name>` or a bare custom name such as `com.example.worker`, so the check is an
 * equality test, never `contains(":")`.
 *
 * Every path fails open: when the process name cannot be determined the process is treated as the
 * main one. A phantom session is a data-quality annoyance; a misfiring guard is total telemetry loss.
 */
object ProcessUtils {

    @JvmStatic
    fun isMainProcess(context: Context): Boolean = try {
        isMainProcessName(
            defaultProcessName(context),
            resolveProcessName(
                Build.VERSION.SDK_INT,
                { platformProcessName() },
                {
                    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                    findProcessName(activityManager?.runningAppProcesses, Process.myPid())
                }
            )
        )
    } catch (t: Throwable) {
        AgentLogManager.getAgentLog().debug("ProcessUtils: could not determine the process, assuming main: $t")
        true
    }

    @SuppressLint("NewApi") // only reached when resolveProcessName sees sdkInt >= P
    private fun platformProcessName(): String? = Application.getProcessName()

    /** The app's default process name: `applicationInfo.processName`, else the package name. */
    internal fun defaultProcessName(context: Context): String =
        context.applicationInfo?.processName?.takeIf { it.isNotEmpty() } ?: context.packageName

    internal fun isMainProcessName(defaultProcessName: String?, processName: String?): Boolean {
        if (defaultProcessName.isNullOrEmpty() || processName.isNullOrEmpty()) {
            return true
        }
        return defaultProcessName == processName
    }

    /**
     * API 28+ has a public accessor. Below that, find our own entry in
     * `ActivityManager.runningAppProcesses` by pid, as Datadog's SDK does.
     * `ActivityThread.currentProcessName()` is a restricted hidden API.
     */
    internal fun resolveProcessName(
        sdkInt: Int,
        platformName: () -> String?,
        activityManagerName: () -> String?
    ): String? = if (sdkInt >= Build.VERSION_CODES.P) platformName() else activityManagerName()

    /** `runningAppProcesses` can be null; our pid may be missing from it. Both mean "unknown". */
    internal fun findProcessName(
        processes: List<ActivityManager.RunningAppProcessInfo>?,
        pid: Int
    ): String? = processes?.firstOrNull { it.pid == pid }?.processName
}
