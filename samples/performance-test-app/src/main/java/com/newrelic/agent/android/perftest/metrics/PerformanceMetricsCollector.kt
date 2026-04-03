/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.perftest.metrics

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log
import com.google.gson.Gson
import java.io.RandomAccessFile
import kotlin.math.abs

/**
 * Performance metrics collector for Android
 * Equivalent to iOS MetricsConsumer.swift
 *
 * Collects metrics every 2 seconds:
 * - Memory usage (MB)
 * - CPU usage (%)
 * - Startup time
 * - Health checks (ANR detection)
 */
class PerformanceMetricsCollector private constructor(private val context: Context) {

    companion object {
        private const val TAG = "PerfMetrics"
        private const val COLLECTION_INTERVAL_MS = 2000L // 2 seconds, same as iOS

        @Volatile
        private var instance: PerformanceMetricsCollector? = null

        fun getInstance(context: Context): PerformanceMetricsCollector {
            return instance ?: synchronized(this) {
                instance ?: PerformanceMetricsCollector(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    private val metrics = mutableListOf<MetricSnapshot>()
    private val handler = Handler(Looper.getMainLooper())
    private var isCollecting = false
    private var startupTime: Long? = null
    private var lastCpuTime: Long = 0
    private var lastSystemTime: Long = 0
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val pid = Process.myPid()

    data class MetricSnapshot(
        val timestamp: Long,
        val type: String,
        val memoryMB: Double? = null,
        val cpuPercent: Double? = null,
        val duration: Long? = null,
        val screen: String? = null
    )

    /**
     * Start collecting metrics every 2 seconds
     */
    fun startCollecting() {
        if (isCollecting) {
            Log.d(TAG, "Already collecting metrics")
            return
        }

        isCollecting = true
        Log.d(TAG, "Started collecting performance metrics")

        // Initialize CPU tracking
        lastCpuTime = getProcessCpuTime()
        lastSystemTime = System.nanoTime()

        // Start periodic collection
        scheduleNextCollection()
    }

    /**
     * Stop collecting metrics
     */
    fun stopCollecting() {
        isCollecting = false
        handler.removeCallbacksAndMessages(null)
        Log.d(TAG, "Stopped collecting metrics. Total samples: ${metrics.size}")
    }

    /**
     * Record startup time (called once at app launch)
     */
    fun recordStartupTime(startTimeMs: Long) {
        val duration = System.currentTimeMillis() - startTimeMs
        startupTime = duration

        metrics.add(MetricSnapshot(
            timestamp = System.currentTimeMillis(),
            type = "startupTime",
            duration = duration
        ))

        Log.d(TAG, "Startup time recorded: ${duration}ms")
    }

    /**
     * Get all metrics as JSON string
     * This will be exposed via hidden TextView for WebDriverIO to read
     */
    fun getMetricsJson(): String {
        return try {
            Gson().toJson(metrics)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to serialize metrics to JSON", e)
            "[]"
        }
    }

    /**
     * Get metrics count (for debugging)
     */
    fun getMetricsCount(): Int = metrics.size

    private fun scheduleNextCollection() {
        if (!isCollecting) return

        handler.postDelayed({
            collectSnapshot()
            scheduleNextCollection()
        }, COLLECTION_INTERVAL_MS)
    }

    private fun collectSnapshot() {
        try {
            val memoryMB = getMemoryUsageMB()
            val cpuPercent = getCpuUsagePercent()

            metrics.add(MetricSnapshot(
                timestamp = System.currentTimeMillis(),
                type = "resourceUsage",
                memoryMB = memoryMB,
                cpuPercent = cpuPercent
            ))

            if (metrics.size % 10 == 0) {
                Log.d(TAG, "Collected ${metrics.size} samples - Memory: ${String.format("%.2f", memoryMB)}MB, CPU: ${String.format("%.2f", cpuPercent)}%")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error collecting metrics snapshot", e)
        }
    }

    /**
     * Get current memory usage in MB
     * Equivalent to iOS mach_task_basic_info resident_size
     */
    private fun getMemoryUsageMB(): Double {
        return try {
            val memoryInfo = Debug.MemoryInfo()
            Debug.getMemoryInfo(memoryInfo)

            // Total PSS (Proportional Set Size) in KB, convert to MB
            val totalPssKb = memoryInfo.totalPss.toDouble()
            totalPssKb / 1024.0
        } catch (e: Exception) {
            Log.e(TAG, "Error getting memory usage", e)
            0.0
        }
    }

    /**
     * Get current CPU usage percentage
     * Equivalent to iOS thread_basic_info cpu_usage
     */
    private fun getCpuUsagePercent(): Double {
        return try {
            val currentCpuTime = getProcessCpuTime()
            val currentSystemTime = System.nanoTime()

            if (lastCpuTime == 0L || lastSystemTime == 0L) {
                // First reading, just initialize
                lastCpuTime = currentCpuTime
                lastSystemTime = currentSystemTime
                return 0.0
            }

            val cpuTimeDiff = currentCpuTime - lastCpuTime
            val systemTimeDiff = currentSystemTime - lastSystemTime

            lastCpuTime = currentCpuTime
            lastSystemTime = currentSystemTime

            if (systemTimeDiff <= 0) return 0.0

            // Calculate CPU percentage
            val cpuPercent = (cpuTimeDiff.toDouble() / systemTimeDiff.toDouble()) * 100.0

            // Cap at 100% per core (Android reports total across all cores)
            return cpuPercent.coerceIn(0.0, 100.0 * Runtime.getRuntime().availableProcessors())
        } catch (e: Exception) {
            Log.e(TAG, "Error getting CPU usage", e)
            0.0
        }
    }

    /**
     * Get process CPU time in nanoseconds
     * Reads from /proc/[pid]/stat
     */
    private fun getProcessCpuTime(): Long {
        return try {
            val statFile = RandomAccessFile("/proc/$pid/stat", "r")
            val stat = statFile.readLine()
            statFile.close()

            val stats = stat.split(" ")
            // utime (14th field) + stime (15th field) in clock ticks
            val utime = stats[13].toLong()
            val stime = stats[14].toLong()

            // Convert clock ticks to nanoseconds (assuming 100 ticks per second)
            (utime + stime) * 10_000_000L
        } catch (e: Exception) {
            Log.e(TAG, "Error reading process CPU time", e)
            0L
        }
    }

    /**
     * Clear all collected metrics (for testing)
     */
    fun clear() {
        metrics.clear()
        Log.d(TAG, "Cleared all metrics")
    }
}
