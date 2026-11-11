/*
 * Copyright (c) 2026-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.util

import android.content.Context
import com.newrelic.agent.android.AgentConfiguration
import com.newrelic.agent.android.ApplicationFramework
import com.newrelic.agent.android.logging.AgentLogManager
import com.newrelic.agent.android.metric.MetricNames
import com.newrelic.agent.android.stats.StatsEngine

/**
 * Decides the application framework before anything caches or reports it.
 *
 * Precedence: an explicit withApplicationFramework() call, then the framework a hybrid SDK declares
 * in the app's resources (see [ApplicationFrameworkResource]), then Native. The resource only ever
 * fills the gap left by a hybrid SDK that never called in, so it is not read when a framework was
 * set explicitly.
 *
 * Each time a detected framework is used, one supportability metric is counted
 * (MetricNames.SUPPORTABILITY_MOBILE_ANDROID_HYBRID_PLATFORM_DETECTED). That is how many hybrid
 * apps are not sending their framework name themselves.
 */
object ApplicationFrameworkResolver {

    @JvmStatic
    fun resolve(context: Context, agentConfiguration: AgentConfiguration) {
        resolve(agentConfiguration) { ApplicationFrameworkResource.read(context) }
    }

    internal fun resolve(agentConfiguration: AgentConfiguration, detect: () -> ApplicationFramework?) {
        if (agentConfiguration.isApplicationFrameworkExplicit) {
            return
        }

        val log = AgentLogManager.getAgentLog()
        try {
            val startNanos = System.nanoTime()
            val detected = detect()
            val elapsedMicros = (System.nanoTime() - startNanos) / 1000

            if (detected != null) {
                agentConfiguration.applyDetectedApplicationFramework(detected)
                log.debug("Detected application framework $detected in $elapsedMicros us")
                count(
                    MetricNames.SUPPORTABILITY_MOBILE_ANDROID_HYBRID_PLATFORM_DETECTED
                        .replace(MetricNames.TAG_DETECTED_FRAMEWORK, detected.name)
                )
            } else {
                log.debug("No application framework resource ($elapsedMicros us)")
            }
        } catch (t: Throwable) {
            log.error("Application framework detection failed, reporting Native: $t")
            fallBackToNative(agentConfiguration)
        }
    }

    /** A metric that cannot be recorded must never undo or fail the detection it describes. */
    private fun count(metricName: String) {
        try {
            StatsEngine.SUPPORTABILITY.inc(metricName)
        } catch (t: Throwable) {
            AgentLogManager.getAgentLog().debug("Could not record $metricName: $t")
        }
    }

    /**
     * Whatever a failed detection left behind (for example a value from an earlier start in the
     * same process), the platform is reported as the default. An explicit choice is never touched.
     */
    private fun fallBackToNative(agentConfiguration: AgentConfiguration) {
        if (!agentConfiguration.isApplicationFrameworkExplicit) {
            agentConfiguration.applyDetectedApplicationFramework(ApplicationFramework.Native)
        }
    }
}
