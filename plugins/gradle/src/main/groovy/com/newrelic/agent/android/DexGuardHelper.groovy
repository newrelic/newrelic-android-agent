/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android

import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.gradle.util.GradleVersion

class DexGuardHelper {
    static final GradleVersion minSupported = GradleVersion.version('8.1.0')

    static final String PLUGIN_EXTENSION_NAME = "dexguard"
    static final String DEXGUARD_TASK = "dexguard"
    static final String DEXGUARD_BUNDLE_TASK = "dexguardBundle"
    static final String DEXGUARD_APK_TASK = "dexguardApk"
    static final String DEXGUARD_AAB_TASK = "dexguardAab"
    static final String DEXGUARD_PLUGIN_ORDER_ERROR_MSG = "The New Relic plugin must be applied *after* the DexGuard plugin in the project Gradle build file."

    static final def dexguard9Tasks = [DEXGUARD_APK_TASK, DEXGUARD_AAB_TASK]

    final Project project
    final Logger logger

    final def enabled
    final def extension
    final GradleVersion version

    /**
     * Must be called after project has been evaluated (project.afterEvaluate())
     *
     * @param project
     */
    DexGuardHelper(Project project) {
        this.project = project
        this.logger = NewRelicGradlePlugin.LOGGER
        try {
            this.enabled = project.plugins.hasPlugin(PLUGIN_EXTENSION_NAME)
            if (this.enabled) {
                this.extension = project.extensions.getByName(PLUGIN_EXTENSION_NAME)
                this.version = GradleVersion.version(this.extension.version)
                if (this.version < minSupported) {
                    logger.warn("The New Relic plugin may not be compatible with DexGuard version ${this.version}.")
                }
            }
        } catch (Exception e) {
            // version not found in configs
        }
    }

    boolean isDexGuard9() {
        return enabled && (version && version >= GradleVersion.version("9.0"))
    }

    boolean isLegacyDexGuard() {
        return enabled && (!version || version < GradleVersion.version("9.0"))
    }

    def getDefaultMapPath(def variant) {
        if (isDexGuard9()) {
            // caller must replace target with [apk, bundle]
            return project.file("${project.buildDir}/outputs/dexguard/mapping/<target>/${variant.dirName}/mapping.txt")
        }

        // Legacy DG report through AGP to this location (unless overridden in dexguard.project settings)
        return project.file("${project.buildDir}/outputs/mapping/${variant.dirName}/mapping.txt")
    }
}
