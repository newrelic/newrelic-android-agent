/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android

import com.android.build.api.artifact.MultipleArtifact
import com.android.build.api.artifact.SingleArtifact
import com.newrelic.agent.util.BuildId
import org.gradle.api.Task
import org.gradle.api.UnknownTaskException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.Logger
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.TaskProvider
import org.gradle.util.GradleVersion

class DexGuardHelper {
    static final String minSupportedVersion = "9.0.0"

    static final String PLUGIN_EXTENSION_NAME = "dexguard"
    static final String DEXGUARD_TASK = "dexguard"
    static final String DEXGUARD_BUNDLE_TASK = "dexguardBundle"
    static final String DEXGUARD_APK_TASK = "dexguardApk"
    static final String DEXGUARD_AAB_TASK = "dexguardAab"
    static final String DEXGUARD_DESUGAR_TASK = "transformClassesWithDesugarFor"
    static final String DEXGUARD_PLUGIN_ORDER_ERROR_MSG = "The New Relic plugin must be applied *after* the DexGuard plugin in the project Gradle build file."

    final BuildHelper buildHelper
    final protected ObjectFactory objectFactory
    final MapProperty<String, Object> variantConfigurations
    final Logger logger
    final def extension

    String currentVersion = "9.0.0"
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
        this.objectFactory = buildHelper.project.objects
        this.variantConfigurations = objectFactory.mapProperty(String, Object)

        try {
            this.extension = buildHelper.project.extensions.getByName(DexGuardHelper.PLUGIN_EXTENSION_NAME)
            this.extension?.with() { ext ->
                ext.version?.with() { version ->
                    try {
                        this.currentVersion = version
                        if (GradleVersion.version(currentVersion) < GradleVersion.version(DexGuardHelper.minSupportedVersion)) {
                            buildHelper.warnOrHalt("The New Relic plugin may not be compatible with DexGuard version ${currentVersion}.")
                        }
                    } catch (IllegalArgumentException e) {
                        // wildcards not parsed, replace with 0
                        this.currentVersion = currentVersion.replace('+', '0')
                    }
                }
                ext.configurations?.each { conf ->
                    if (buildHelper.extension.shouldIncludeMapUpload(conf.name)) {
                        this.variantConfigurations.put(conf.name.toLowerCase(), conf)
                    }
                }
                this.enabled = true
            }
        } catch (Exception ignored) {
            // version not found in configs
        }
    }

    boolean isDexGuard9() {
        return enabled && (getCurrentVersion() && GradleVersion.version(getCurrentVersion()) >= GradleVersion.version("9.0"))
    }

    /**
     * Returns a RegularFile property representing the correct mapping file location
     * @param variantDirName
     * @return RegularFileProperty
     */
    RegularFileProperty getMappingFileProvider(String variantName) {
        def variant = buildHelper.variantAdapter.withVariant(variantName)

        if (isDexGuard9()) {
            // caller must replace target with [apk, bundle]
            return objectFactory.fileProperty().value(buildHelper.project.layout
                    .getBuildDirectory()
                    .file("outputs/dexguard/mapping/<target>/${variant.dirName}/mapping.txt"))
        }

        // Legacy DG report through AGP to this location (unless overridden in dexguard.project settings)
        return objectFactory.fileProperty().value(buildHelper.project.layout
                .getBuildDirectory()
                .file("outputs/mapping/${variant.dirName}/mapping.txt"))
    }

    protected configureDexGuard9Tasks(String variantName) {
        try {
            def mapUploadProvider = buildHelper.variantAdapter.wiredWithMapUploadProvider(variantName)
            buildHelper.project.afterEvaluate {
                def wiredTaskNames = DexGuardHelper.wiredTaskNames(variantName.capitalize())
                buildHelper.wireTaskProviderToDependencyNames(wiredTaskNames) { taskProvider ->
                    if (taskProvider.name.startsWith(DexGuardHelper.DEXGUARD_APK_TASK)) {
                        finalizeMapUploadProvider(taskProvider, variantName) {
                            it.replace("<target>", "apk")
                        }
                    } else if (taskProvider.name.startsWith(DexGuardHelper.DEXGUARD_BUNDLE_TASK)) {
                        finalizeMapUploadProvider(taskProvider, variantName) {
                            it.replace("<target>", "bundle")
                        }
                    }
                }
            }

        } catch (Exception e) {
            // DexGuard task hasn't been created
            logger.error("configureDexGuard: " + e)
            logger.error(DEXGUARD_PLUGIN_ORDER_ERROR_MSG)
        }
    }

    void configureDexGuard() {
        logger.debug("Dexguard version: " + buildHelper.dexguardHelper.currentVersion)
        if (isDexGuard9()) {
            buildHelper.variantAdapter.getVariantValues().each { variant ->
                configureDexGuard9Tasks(variant.name)
            }
            return
        }

        // DG 8:
        buildHelper.variantAdapter.getVariantValues().each { variant ->
            def vnc = variant.name.capitalize()
            try {
                // throws if DexGuard not present
                def dexguardTask = buildHelper.project.tasks.named("${DexGuardHelper.DEXGUARD_TASK}${vnc}")
                def transformerTask = buildHelper.variantAdapter.getTransformProvider(variant.name) {
                    it.classDirectories.set(variant.artifacts.getAll(MultipleArtifact.ALL_CLASSES_DIRS.INSTANCE))
                    it.classJars.set(variant.artifacts.getAll(MultipleArtifact.ALL_CLASSES_JARS.INSTANCE))
                }

                def desugarTaskName = "${DEXGUARD_DESUGAR_TASK}${vnc}"
                try {
                    project.tasks.named(desugarTaskName) { task ->
                        task.finalizedBy(transformerTask)
                        task.dependsOn(transformerTask)
                    }

                } catch (Exception e) {
                    logger.debug("configureDexGuardTasks: Desugaring task [" + desugarTaskName + "]: " + e)
                }

                buildHelper.variantAdapter.getJavaCompileProvider(variant.name).configure {
                    it.finalizedBy transformerTask
                    logger.info("Task [${dexguardTask.getName()}] has been configured for New Relic instrumentation.")
                }

                // FIXME bundleRelease -> dexguardBundleBundle -> dexguardRelease
                try {
                    finalizeMapUploadProvider(
                            buildHelper.project.tasks.named("${DEXGUARD_BUNDLE_TASK}${vnc}"),
                            variant.name)

                } catch (UnknownTaskException ignored) {
                    // task for this variant not available
                }


            } catch (UnknownTaskException e) {
                // task for this variant not available
                logger.debug("configureDexGuardTasks: " + e)
            }
        }
    }

    void finalizeMapUploadProvider(TaskProvider dependencyTaskProvider, String variantName, Closure closure = null) {
        try {
            def mapUploadTaskProvider = buildHelper.variantAdapter.getMapUploadProvider(variantName)

            mapUploadTaskProvider.configure { mapUploadTask ->
                mapUploadTask.dependsOn(dependencyTaskProvider)
                try {
                    // update the map file path if needed
                    if (closure) {
                        def mapPath = closure(mapUploadTask.mappingFile.get().asFile.absolutePath)
                        mapUploadTask.mappingFile.set(buildHelper.project.file(mapPath))
                    }

                    if (!mapUploadTask.buildId.isPresent()) {
                        mapUploadTask.buildId.set(BuildId.getBuildId(variantName))
                    }

                } catch (Exception e) {
                    logger.error("finalizeMapUploadProvider: $e")
                }

            }

            dependencyTaskProvider.configure {
                it.finalizedBy(mapUploadTaskProvider)
            }

        } catch (UnknownTaskException e) {
            // task for this variant not available or other configuration error
            logger.error("finalizeMapUploadProvider: $e")
        }
    }

    static Set<String> wiredTaskNames(String vnc) {
        return Set.of(
                "${DEXGUARD_AAB_TASK}${vnc}",
                "${DEXGUARD_APK_TASK}${vnc}",
        )
    }
}
