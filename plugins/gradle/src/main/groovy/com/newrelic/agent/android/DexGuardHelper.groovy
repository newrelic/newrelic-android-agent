/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android

import org.gradle.api.UnknownTaskException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.Logger
import org.gradle.util.GradleVersion

class DexGuardHelper {
    static final String minSupportedVersion = "8.1.0"

    static final String PLUGIN_EXTENSION_NAME = "dexguard"
    static final String DEXGUARD_TASK = "dexguard"
    static final String DEXGUARD_BUNDLE_TASK = "dexguardBundle"
    static final String DEXGUARD_APK_TASK = "dexguardApk"
    static final String DEXGUARD_AAB_TASK = "dexguardAab"
    static final String DEXGUARD_PLUGIN_ORDER_ERROR_MSG = "The New Relic plugin must be applied *after* the DexGuard plugin in the project Gradle build file."
    static final String DEXGUARD_DESUGAR_TASK = "transformClassesWithDesugarFor"

    static final def dexguard9Tasks = [DEXGUARD_APK_TASK, DEXGUARD_AAB_TASK]

    final BuildHelper buildHelper
    final Logger logger
    final def extension

    String currentVersion
    def enabled = false

    static DexGuardHelper register(BuildHelper buildHelper) {
        return new DexGuardHelper(buildHelper)
    }

    /**
     * Must be called after project has been evaluated (project.afterEvaluate())
     *
     * @param project
     */
    DexGuardHelper(BuildHelper buildHelper) {
        this.buildHelper = buildHelper
        this.logger = buildHelper.logger

        try {
            this.extension = project.extensions.getByName(PLUGIN_EXTENSION_NAME)?.with() { ext ->
                this.currentVersion = GradleVersion.version(ext.currentVersion)
                if (GradleVersion.version(getCurrentVersion()) < GradleVersion.version(DexGuardHelper.minSupportedVersion)) {
                    logger.warn("The New Relic plugin may not be compatible with DexGuard version ${this.currentVersion}.")
                }
                this.enabled = true
            }
        } catch (Exception e) {
            // version not found in configs
        }
    }

    boolean isDexGuard9() {
        return enabled && (currentVersion && GradleVersion.version(currentVersion) >= GradleVersion.version("9.0"))
    }

    /**
     * Returns a RegularFile property representing the correct mapping file location
     * @param variantDirName
     * @return RegularFileProperty
     */
    RegularFileProperty getDefaultMapPathProvider(String variantDirName) {
        if (isDexGuard9()) {
            // caller must replace target with [apk, bundle]
            return buildHelper.project.objects.fileProperty().set("${buildHelper.project.buildDir}/outputs/dexguard/mapping/<target>/${variantDirName}/mapping.txt")
        }

        // Legacy DG report through AGP to this location (unless overridden in dexguard.project settings)
        return buildHelper.project.objects.fileProperty().set("${buildHelper.project.buildDir}/outputs/mapping/${variantDirName}/mapping.txt")
    }

    protected configureDexGuard9Task(def variantName) {
            def variantNameCap = variantName.capitalize()
            DexGuardHelper.dexguard9Tasks.each { taskName ->
                try {
                    buildHelper.project.tasks.named("${taskName}${variantNameCap}").configure {
                        buildHelper.injectMapUploadFinalizer(task, variantName, { File mappingFile ->
                            if (task.name.startsWith(DexGuardHelper.DEXGUARD_APK_TASK)) {
                                buildHelper.project.objects.fileProperty().set(mappingFile.getAbsolutePath().replace("<target>", "apk"))
                            } else if (task.name.startsWith(DexGuardHelper.DEXGUARD_AAB_TASK)) {
                                buildHelper.project.objects.fileProperty().set(mappingFile.getAbsolutePath().replace("<target>", "bundle"))
                            }
                        })
                    }

                } catch (Exception e) {
                    // DexGuard task hasn't been created
                    logger.error("configureDexGuard: " + e)
                    logger.error(DexGuardHelper.DEXGUARD_PLUGIN_ORDER_ERROR_MSG)
                }
        }
    }

    protected configureDexGuardTasks() {
        logger.debug("Dexguard version: " + buildHelper.dexguardHelper.currentVersion)

        if (isDexGuard9()) {
            buildHelper.variantAdapter.getVariantValues().each { variant -> configureDexGuard9Tasks(variant.name) }
            return
        }

        // FIXME
        buildHelper.variantAdapter.getVariantValues().each { variant ->
            def variantNameCap = variant.name.capitalize()
            /* AGP4 */
            try {
                // throws if DexGuard not present
                def dexguardTask = buildHelper.project.tasks.named("${DexGuardHelper.DEXGUARD_TASK}${variantNameCap}")
                def classRewriterTask = buildHelper.project.tasks.register("${NewRelicClassRewriterTask.NAME}${variantNameCap}",
                        NewRelicClassRewriterTask) {
                    it.variantName.set(variant.name)
                }

                def desugarTaskName = "${DexGuardHelper.DEXGUARD_DESUGAR_TASK}${variantNameCap}"
                try {
                    project.tasks.named(desugarTaskName) {
                        finalizedBy classRewriterTask
                    }

                } catch (UnknownTaskException ignored) {
                    // not desugaring, ignore

                } catch (Exception e) {
                    logger.debug("configureDexGuardTasks: Desugaring task [" + desugarTaskName + "]: " + e)

                } finally {
                    dexguardTask.configure { task->
                        task.dependsOn(classRewriterTask)
                        injectMapUploadFinalizer(buildHelper.project, task, variant, null)
                    }
                }

                def javaCompileTask = buildHelper.variantAdapter.getJavaCompileProvider(variant.name).configure {
                    finalizedBy classRewriterTask
                    logger.info("Task [" + dexguardTask.getName() +
                            "] has been configured for New Relic instrumentation.")
                }

                // bundleRelease -> dexguardBundleBundle -> dexguardRelease
                injectMapUploadFinalizer(project, "${DexGuardHelper.DEXGUARD_BUNDLE_TASK}${variantNameCap}", variant)

            } catch (UnknownTaskException e) {
                // task for this variant not available
                logger.debug("configureDexGuardTasks: " + e)
            }
            /* AGP4 */
        }
    }

}
