/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android

import com.android.build.api.artifact.MultipleArtifact
import com.newrelic.agent.util.BuildId
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.UnknownTaskException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.Logger
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.TaskProvider
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
                if (GradleVersion.version(getCurrentVersion()) < GradleVersion.version(minSupportedVersion)) {
                    buildHelper.warnOrHalt("The New Relic plugin may not be compatible with DexGuard version ${this.currentVersion}.")
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
    RegularFileProperty getDefaultMapPathProvider(String variantDirName) {
        if (isDexGuard9()) {
            // caller must replace target with [apk, bundle]
            return buildHelper.project.objects.fileProperty().fileValue(buildHelper.project.layout.getBuildDirectory().file("/outputs/dexguard/mapping/<target>/${variantDirName}/mapping.txt"))
        }

        // Legacy DG report through AGP to this location (unless overridden in dexguard.project settings)
        return buildHelper.project.objects.fileProperty().fileValue(buildHelper.project.layout.getBuildDirectory().file("/outputs/mapping/${variantDirName}/mapping.txt"))
    }

    protected configureDexGuard9Task(String variantName) {
        def variantNameCap = variantName.capitalize()
        DexGuardHelper.dexguard9Tasks.each { taskName ->
            try {
                buildHelper.project.tasks.named("${taskName}${variantNameCap}").configure() { dependencyTask ->
                    /* FIXME
                    buildHelper.finalizeMapUploadTask(dependencyTask, variantName, { RegularFileProperty mappingFile ->
                        if (dependencyTask.name.startsWith(DexGuardHelper.DEXGUARD_APK_TASK)) {
                            buildHelper.project.objects.fileProperty().fileValue(mappingFile.getAsFile().getAbsolutePath().replace("<target>", "apk"))
                        } else if (dependencyTask.name.startsWith(DexGuardHelper.DEXGUARD_AAB_TASK)) {
                            buildHelper.project.objects.fileProperty().set(mappingFile.getAsFile().getAbsolutePath().replace("<target>", "bundle"))
                        }
                    })
                    /* FIXME */
                }

            } catch (Exception e) {
                // DexGuard task hasn't been created
                logger.error("configureDexGuard: " + e)
                logger.error(DEXGUARD_PLUGIN_ORDER_ERROR_MSG)
            }
        }
    }

    protected configureDexGuard9Tasks(String variantName) {
        variantName
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
                def transformerTask = buildHelper.variantAdapter.getTransformProvider(variant.name) {
                    it.classDirectories.set(variant.artifacts.getAll(MultipleArtifact.ALL_CLASSES_DIRS.INSTANCE))
                    it.classJars.set(variant.artifacts.getAll(MultipleArtifact.ALL_CLASSES_JARS.INSTANCE))
                }

                def desugarTaskName = "${DEXGUARD_DESUGAR_TASK}${variantNameCap}"
                try {
                    project.tasks.named(desugarTaskName) { task ->
                        task.finalizedBy(transformerTask)
                        task.dependsOn(transformerTask)
                        injectMapUploadFinalizer(buildHelper.project, task, variant, null)
                    }

                } catch (Exception e) {
                    logger.debug("configureDexGuardTasks: Desugaring task [" + desugarTaskName + "]: " + e)
                }

                buildHelper.variantAdapter.getJavaCompileProvider(variant.name).configure {
it.finalizedBy transformerTask
                    logger.info("Task [${dexguardTask.getName()}] has been configured for New Relic instrumentation.")
                }

                // FIXME bundleRelease -> dexguardBundleBundle -> dexguardRelease
                finalizeMapUploadTask("${DEXGUARD_BUNDLE_TASK}${variantNameCap}", variant.name)

            } catch (UnknownTaskException e) {
                // task for this variant not available
                logger.debug("configureDexGuardTasks: " + e)
            }
            /* AGP4 */
        }
    }

    Set<String> getMapUploadTaskDependencies(String variantName) {
        def buildType = buildHelper.variantAdapter.getBuildTypeProvider(variantName)
        def taskSet = NewRelicMapUploadTask.getDependentTaskNames(variantName.capitalize())

        if (buildType.get().minified) {
            [DEXGUARD_TASK, DEXGUARD_BUNDLE_TASK, DEXGUARD_APK_TASK, DEXGUARD_AAB_TASK].each { taskName ->
                try {
                    buildHelper.project.tasks.named("${taskName}${variantName.capitalize()}").configure {
                        taskSet.add(it as String)
                    }
                } catch (Exception ignored) {
                }
            }
        }

        taskSet
    }

    // FIXME Refactor:
    void finalizeMapUploadTask(String targetTaskName, String variantName, Closure closure = null) {
        try {
            finalizeMapUploadTask(buildHelper.project.tasks.named(targetTaskName).get(), variantName, closure)
        } catch (UnknownTaskException ignored) {
            // task for this variant not available
        }
    }

    void finalizeMapUploadTask(Task dependencyTask, String variantName, Closure closure = {}) {
        try {
            def mapUploadTaskName = "${NewRelicMapUploadTask.NAME}${variantName.capitalize()}"

            buildHelper.project.tasks.named(mapUploadTaskName, NewRelicMapUploadTask.class).with { mapUploadTaskProvider ->
                finalizeMapUploadProvider(project.tasks.named(dependencyTask.name), mapUploadTaskProvider, variantName, closure)
            }
        } catch (UnknownTaskException ignored) {
            // task for this variant not available or other configuration error
        }
    }

    void finalizeMapUploadProvider(TaskProvider dependencyTaskProvider, TaskProvider<NewRelicMapUploadTask> mapUploadTaskProvider, String variantName, Closure closure) {
        try {
            mapUploadTaskProvider.configure { mapUploadTask ->
                try {
                    // update the map file iif needed
                    if (closure) {
                        def updated = closure(mapUploadTask.mappingFile.getAsFile())
                        mapUploadTask.mappingFile.fileValue(updated as File)
                    }

                    if (!mapUploadTask.buildId.isPresent()) {
                        mapUploadTask.buildId.set(BuildId.getBuildId(variantName))
                    }

                } catch (Exception e) {
                    logger.error("finalizeMapUploadProvider: $e")
                }

                dependencyTaskProvider.configure {
                    mapUploadTask.dependsOn(it)
                    it.finalizedBy(mapUploadTask)
                }
            }

        } catch (UnknownTaskException e) {
            // task for this variant not available or other configuration error
            logger.error("finalizeMapUploadProvider: $e")
        }
    }

}
