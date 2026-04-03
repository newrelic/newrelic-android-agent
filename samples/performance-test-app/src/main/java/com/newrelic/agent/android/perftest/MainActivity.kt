/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.perftest

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.newrelic.agent.android.perftest.metrics.PerformanceMetricsCollector
import com.newrelic.agent.android.perftest.navigation.AppNavigation
import com.newrelic.agent.android.perftest.ui.theme.PerformanceTestTheme

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private var metricsTextView: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            PerformanceTestTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }

        // Add hidden TextView to expose metrics (like iOS hidden UILabel)
        addMetricsTextView()
    }

    /**
     * Add a hidden TextView that exposes performance metrics as JSON
     * Equivalent to iOS hidden UILabel with accessibility ID "performance_metrics_json"
     * WebDriverIO tests will read this to get metrics data
     */
    private fun addMetricsTextView() {
        try {
            metricsTextView = TextView(this).apply {
                // Make it tiny and nearly transparent but VISIBLE for Appium
                visibility = View.VISIBLE
                textSize = 1f  // 1sp - extremely small
                alpha = 0.01f  // Nearly transparent

                // Set content description for Appium/WebDriverIO to find it
                contentDescription = "performance_metrics_json"

                // Set initial text
                text = "[]"
            }

            // Add to the root view
            addContentView(
                metricsTextView,
                android.view.ViewGroup.LayoutParams(1, 1)
            )

            Log.d(TAG, "Metrics TextView created with contentDescription: performance_metrics_json")

            // Update metrics TextView periodically
            startMetricsUpdate()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create metrics TextView", e)
        }
    }

    /**
     * Update the metrics TextView every 5 seconds with latest metrics JSON
     */
    private fun startMetricsUpdate() {
        val updateInterval = 5000L // 5 seconds

        window.decorView.postDelayed(object : Runnable {
            override fun run() {
                try {
                    val metricsCollector = PerformanceMetricsCollector.getInstance(applicationContext)
                    val metricsJson = metricsCollector.getMetricsJson()

                    metricsTextView?.text = metricsJson

                    if (metricsCollector.getMetricsCount() % 20 == 0) {
                        Log.d(TAG, "Updated metrics TextView with ${metricsCollector.getMetricsCount()} samples")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to update metrics TextView", e)
                }

                // Schedule next update
                window.decorView.postDelayed(this, updateInterval)
            }
        }, updateInterval)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Stop collecting metrics when activity is destroyed
        try {
            PerformanceMetricsCollector.getInstance(applicationContext).stopCollecting()
            Log.d(TAG, "Stopped metrics collection")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping metrics collection", e)
        }
    }
}
