/*
 * Copyright (c) 2026-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.util

import android.content.Context
import com.newrelic.agent.android.AgentConfiguration
import com.newrelic.agent.android.ApplicationFramework
import com.newrelic.agent.android.logging.AgentLogManager

/**
 * Decides the application framework before anything caches or reports it.
 *
 * Precedence: an explicit withApplicationFramework() call, then what the app looks like, then
 * Native. Detection only ever fills the gap left by a hybrid SDK that never called in.
 */
object ApplicationFrameworkResolver {

    @JvmStatic
    fun resolve(context: Context, agentConfiguration: AgentConfiguration) {
        resolve(agentConfiguration) { PlatformDetector.forContext(context).detect() }
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
            } else {
                log.debug("No hybrid application framework detected ($elapsedMicros us)")
            }
        } catch (t: Throwable) {
            log.error("Application framework detection failed, reporting Native: $t")
            fallBackToNative(agentConfiguration)
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
