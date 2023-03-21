/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android
// gradle-api 7.2

import com.newrelic.agent.InstrumentationAgent
import com.newrelic.agent.android.obfuscation.Proguard
import com.newrelic.agent.compile.HaltBuildException
import com.newrelic.agent.util.BuildId
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.UnknownTaskException
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.util.GradleVersion

class NewRelicGradlePlugin implements Plugin<Project> {
    public static Logger LOGGER = Logging.getLogger(PLUGIN_EXTENSION_NAME)

    public static final String PLUGIN_EXTENSION_NAME = "newrelic"
    public static final String DEX_PROGUARD = "proguard"
    public static final String DEX_R8 = "r8"

    private NewRelicExtension plugin
    private BuildHelper buildHelper

    @Override
    void apply(Project project) {
        project.getLogging().captureStandardOutput(LogLevel.WARN)

        // bind the instrumentation agent's logger to the plugin's logger
        InstrumentationAgent.LOGGER = LOGGER

        plugin = NewRelicExtension.register(project)
        if (!plugin.getEnabled()) {
            return
        }

        buildHelper = BuildHelper.register(project)

        project.configure(project) {
            // Gradle now has a complete task execution graph for the requested tasks

            if (plugin.getEnabled()) {

                project.afterEvaluate {
                    buildHelper.variantAdapter.configure(plugin)

                    logBuildMetrics()

                    // set global enable flag
                    BuildId.setVariantMapsEnabled(plugin.variantMapsEnabled.get())

                    def buildMap = getDefaultBuildMap()

                    try {
                        // FIXME
                        if (checkDexGuard(project)) {
                            buildHelper.withDexGuardHelper(new DexGuardHelper(project))
                            configureDexGuardTasks(project)
                        }

                        configureConfigTasks(project)
                        configureTransformTasks(project)

                        configureMapUploadTasks(project)
                        // FIXME

                        // add extension to project's ext data
                        project.ext.newrelic = plugin

                        LOGGER.debug("New Relic plugin loaded.")

                    } catch (MissingPropertyException e) {
                        LOGGER.warn("Not supported: " + e)
                    }
                }

            } else {
                LOGGER.info("New Relic Agent is disabled.")
            }
        }
    }

    void logBuildMetrics() {
        LOGGER.info("New Relic Agent version: " + buildHelper.agentVersion)

        LOGGER.debug("Android Gradle plugin version: " + buildHelper.agpVersion)
        LOGGER.debug("Gradle version: " + buildHelper.gradleVersion)
        LOGGER.debug("Java version: " + buildHelper.getSystemPropertyProvider('java.version').get())
        LOGGER.debug("Gradle configuration cache supported: " + buildHelper.configurationCacheSupported())
        LOGGER.debug("Gradle configuration cache enabled: " + buildHelper.configurationCacheEnabled())

        if (checkInstantApps(buildHelper.project)) {
            LOGGER.debug("InstantApp detected.")
        }

        if (checkDexGuard(buildHelper.project)) {
            LOGGER.info("DexGuard detected.")
        }

        if (buildHelper.shouldApplyLegacyTransform()) {
            def agentArgs = parseLegacyAgentArgs(buildHelper.project)
            LOGGER.debug("AGP TransformAPI: registering NewRelicTransform(" + agentArgs + ")")
        }

        // TODO Turn this into a metric payload and persist for agent to pick up
        // LOGGER.info("BuildMetrics[${buildHelper.buildMetricsAsJson()}]")
    }

    // TODO refactor this out
    private parseLegacyAgentArgs(Project project) {
        def prop = buildHelper.getSystemPropertyProvider(InstrumentationAgent.NR_AGENT_ARGS_KEY)
        def agentArgs = ""

        if (prop.present) {
            agentArgs = prop.get()

        } else {
            if (project.logger.isDebugEnabled()) {
                agentArgs = "loglevel=DEBUG"
            } else if (project.logger.isInfoEnabled()) {
                agentArgs = "loglevel=INFO"
            } else if (project.logger.isWarnEnabled()) {
                agentArgs = "loglevel=WARN"
            } else if (project.logger.isErrorEnabled()) {
                agentArgs = "loglevel=ERROR"
            } else {
                agentArgs = "loglevel=TRACE"
            }
        }

        Throwable argsError = InstrumentationAgent.withAgentArgs(agentArgs)
        if (argsError != null) {
            LOGGER.error(argsError.message)
        }

        agentArgs
    }

    // FIXME Migrate to DexguardHelper
    // called during config phase
    protected configureDexGuard9Tasks(Project project) {
        getProjectVariants().each { variant ->
            if (plugin.shouldIncludeMapUpload(variant.name)) {
                def variantNameCap = variant.name.capitalize()
                DexGuardHelper.dexguard9Tasks.each { taskName ->
                    try {
                        def task = project.tasks.getByName("${taskName}${variantNameCap}")
                        injectMapUploadFinalizer(project, task, variant, { File mappingFile ->
                            if (task.name.startsWith(DexGuardHelper.DEXGUARD_APK_TASK)) {
                                project.file(mappingFile.getAbsolutePath().replace("<target>", "apk"))
                            } else if (task.name.startsWith(DexGuardHelper.DEXGUARD_AAB_TASK)) {
                                project.file(mappingFile.getAbsolutePath().replace("<target>", "bundle"))
                            }
                        })
                    } catch (Exception e) {
                        // DexGuard task hasn't been created
                        LOGGER.error("configureDexGuard: " + e)
                        LOGGER.error(DexGuardHelper.DEXGUARD_PLUGIN_ORDER_ERROR_MSG)
                    }
                }
            }
        }
    }

    // FIXME Migrate to DexguardHelper
    // called during config phase
    protected configureDexGuardTasks(Project project) {
        LOGGER.debug("Dexguard version: " + buildHelper.dexguardHelper.currentVersion)

        if (buildHelper.dexguardHelper.isDexGuard9()) {
            return configureDexGuard9Tasks(project)
        }

        // FIXME
        getProjectVariants().each { variant ->
            def variantNameCap = variant.name.capitalize()
            /* AGP4 */
            try {
                // throws if DexGuard not present
                def dexguardTask = project.tasks.getByName("${DexGuardHelper.DEXGUARD_TASK}${variantNameCap}")

                def classRewriterTaskName = "newrelicClassRewriter${variantNameCap}"
                def classRewriterTask = project.tasks.register(classRewriterTaskName, NewRelicClassRewriterTask) {
                    it.inputVariant = variant
                }

                def desugarTaskName = "transformClassesWithDesugarFor${variantNameCap}"
                try {
                    def desugarTask = project.tasks.getByName(desugarTaskName)
                    desugarTask.finalizedBy classRewriterTask

                } catch (UnknownTaskException) {
                    // not desugaring, ignore

                } catch (Exception e) {
                    LOGGER.debug("desugaring task [" + desugarTaskName + "]: " + e)

                } finally {
                    dexguardTask.dependsOn(classRewriterTask)
                    injectMapUploadFinalizer(project, dexguardTask, variant, null)
                }

                def javaCompileTask = BuildHelper.getVariantCompileTaskProvider(variant)
                if (javaCompileTask) {
                    javaCompileTask.finalizedBy classRewriterTask
                    LOGGER.info("Task [" + dexguardTask.getName() +
                            "] has been configured for New Relic instrumentation.")
                }

                // bundleRelease -> dexguardBundleBundle -> dexguardRelease
                injectMapUploadFinalizer(project, "${DexGuardHelper.DEXGUARD_BUNDLE_TASK}${variantNameCap}", variant)

            } catch (UnknownTaskException e) {
                // task for this variant not available
                LOGGER.debug("configureDexGuard: " + e)
            }
            /* AGP4 */
        }
    }

    // FIXME replace with variant API task
    // called during config phase
    protected def injectMapUploadFinalizer(Project project, def targetTask, def variant, Closure closure) {

        if (plugin.shouldIncludeMapUpload(variant.name)) {

            try {
                def targetNameCap = targetTask.name.capitalize()
                def mappingFile = buildHelper.getVariantMappingFile(variant)

                if (mappingFile) {
                    if (closure) {
                        mappingFile = closure(mappingFile)
                    }
                    def injectedTaskName = "newrelicMapUpload${targetNameCap}"
                    def mapUploadTask = project.tasks.findByName(injectedTaskName)
                    if (mapUploadTask == null) {
                        mapUploadTask = project.tasks.register(injectedTaskName, NewRelicMapUploadTask) { uploadTask ->
                            uploadTask.variantName = variant.name
                            uploadTask.mapProvider = getMapProvider(project)
                            uploadTask.mapFile = mappingFile
                            uploadTask.projectRoot = project.layout.projectDirectory

                            // connect config task buildId to map upload task
                            try {
                                def variantNameCap = variant.name.capitalize()
                                project.tasks.findByName("newrelicConfig${variantNameCap}").with { configTask ->
                                    uploadTask.buildId = configTask.getBuildId()
                                    uploadTask.dependsOn configTask
                                }
                            } catch (Exception e) {
                                uploadTask.buildId = BuildId.getBuildId(variant.name)
                                LOGGER.error("injectMapUploadFinalizer: $e")
                            }

                            onlyIf {
                                // Execute the task only if the given spec is satisfied. The spec will
                                // be evaluated at task execution time, not during configuration.
                                mappingFile.exists()
                            }
                            uploadTask.dependsOn targetTask
                        }
                    }
                    targetTask.finalizedBy mapUploadTask
                    return mapUploadTask
                } else {
                    LOGGER.debug("Variant[$variant.name] mapping file is null!")
                }
            } catch (UnknownTaskException) {
                // task for this variant not available
            }
        }
    }

    // FIXME replace with variant API task
    // called during config phase
    protected def injectMapUploadFinalizer(Project project, String targetTaskName, def variant) {
        try {
            project.tasks.getByName(targetTaskName) { targetTask ->
                return injectMapUploadFinalizer(project, targetTask, variant, { File mappingFile ->
                    LOGGER.debug("[injectMapUploadFinalizer] Injected NewRelicMapUploadTask[${mappingFile}] as finalizer to ${targetTask.name}")
                    mappingFile
                })
            }
        } catch (UnknownTaskException) {
            // task for this variant not available
        }
    }

    // FIXME replace with variant API task
    // called during config phase
    protected void configureTransformTasks(Project project) {
        if (buildHelper.shouldApplyLegacyTransform()) {
            try {
                def transformer = NewRelicTransform.TRANSFORMER_NAME.capitalize()

                getProjectVariants().each {
                    final boolean shouldExcludeVariant = plugin.shouldExcludeVariant(variant.name) ||
                            plugin.shouldExcludeVariant(variant.buildType.name)

                    if (shouldExcludeVariant) {
                        LOGGER.info("Excluding instrumentation of variant [" + variant.name + "]")
                    }

                    def variantNameCap = variant.name.capitalize()
                    def triggerTasks = ["transformClassesWith${transformer}For${variantNameCap}"]

                    triggerTasks.each { targetTaskName ->
                        try {
                            project.tasks.getByName(targetTaskName) { targetTask ->
                                if (targetTask.transform && targetTask.transform instanceof NewRelicTransform) {
                                    /* Will be disabled until AGP8 support is added:
                                    targetTask.doFirst {
                                        targetTask.transform.withTransformState(variant.name, shouldExcludeVariant)
                                    }
                                    */
                                } else {
                                    LOGGER.error("Could not set state on transform task [${targetTask.name}]")
                                }
                            }
                        } catch (Exception e) {
                            // task for this variant not available. Log if not excluded
                            LOGGER.debug("configureTransformTasks: " + e)
                        }
                    }
                }
            } catch (UnknownTaskException) {
                // ignored: task doesn't exist if proguard not enabled
            }
        }
    }

    // FIXME replace with variant API task
    // called during config phase
    protected void configureMapUploadTasks(Project project) {
        try {
            // library projects do not produce maps
            if (checkLibrary(project)) {
                return
            }

            // dexguard maps are handled separately
            if (buildHelper.dexguardHelper.enabled) {
                return
            }

            def enabledVariantTypeNames = plugin.variantMapUploadList ?: ['release']

            // do all the variants if variantMapsEnabled, or only those provided in the extension. Default is *release*.
            if (!plugin.variantMapsEnabled.get() || enabledVariantTypeNames == null) {
                LOGGER.debug("configureMapUploadTasks: all variants")
                enabledVariantTypeNames = []
            } else {
                LOGGER.debug("Maps will be tagged and uploaded for variants ${enabledVariantTypeNames}")
            }

            // FIXME
            getProjectVariants().each { variant ->
                if (enabledVariantTypeNames.isEmpty() ||
                        enabledVariantTypeNames.contains(variant.name.toLowerCase()) ||
                        enabledVariantTypeNames.contains(variant.buildType.name.toLowerCase())) {

                    def buildType = buildHelper.variantAdapter.getBuildType(variant.name)

                    if (buildType && buildType.minifyEnabled) {
                        def variantNameCap = variant.name.capitalize()

                        [DEX_PROGUARD, DEX_R8].each { dexName ->
                            injectMapUploadFinalizer(project,
                                    "transformClassesAndResourcesWith${dexName.capitalize()}For${variantNameCap}", variant)
                            injectMapUploadFinalizer(project,
                                    "minify${variantNameCap}With${dexName.capitalize()}", variant)
                        }
                    } else {
                        if (!buildHelper.dexguardHelper.enabled) {
                            LOGGER.debug("Map upload ignored: build type[$variant.buildType.name] is not minified.")
                        }
                    }
                } else {
                    LOGGER.debug("Map upload ignored for variant[$variant.name]")
                }
            }
        } catch (UnknownTaskException) {
            // ignored: task doesn't exist if proguard not enabled
        }
    }

    // FIXME replace with variant API task
    // called during config phase
    protected void configureConfigTasks(Project project) {
        try {
            // only inject config class into apps
            if (checkLibrary(project)) {
                return
            }

            getProjectVariants().each { variant ->

                def buildId = BuildId.getBuildId(variant.name)
                def variantNameCap = variant.name.capitalize()
                LOGGER.debug("newrelicConfig${variantNameCap} buildId[${buildId}]")
                try {
                    def buildConfigTask = buildHelper.getVariantBuildConfigTask(variant)

                    // FIXME https://developer.android.com/studio/releases/gradle-plugin-api-updates#support_for_adding_generated_classes_to_your_app
                    // https://github.com/android/gradle-recipes/blob/agp-7.2/Kotlin/addToAllClasses/app/build.gradle.kts
                    if (buildConfigTask) {
                        def genSrcFolder = project.layout.buildDirectory.dir("generated/source/newrelicConfig/${variant.dirName}")
                        def taskProvider = project.tasks.register("newrelicConfig${variantNameCap}", NewRelicConfigTask) { configTask ->
                            configTask.buildId = buildId
                            configTask.sourceOutputDir.convention(genSrcFolder)
                            configTask.mapProvider = getMapProvider(project)
                            configTask.minifyEnabled = variant.buildType.minifyEnabled
                        }

                        /**
                         * Update the variant model
                         * @see{https://android.googlesource.com/platform/tools/build/+/master/gradle/src/main/groovy/com/android/build/gradle/api/BaseVariant.java}*
                         */
                        try {
                            variant.registerJavaGeneratingTask(taskProvider, genSrcFolder.get().asFile)
                        } catch (Exception e) {
                            LOGGER.error("" + e.message)
                        }

                        try {
                            variant.addJavaSourceFoldersToModel(genSrcFolder.get().asFile)
                        } catch (Exception e) {
                            LOGGER.warn("" + e.message)
                        }

                        // must manually update the Kotlin compile tasks sourcesets (per variant)
                        try {
                            project.tasks.getByName("compile${variantNameCap}Kotlin") { kotlinCompileTask ->
                                kotlinCompileTask.dependsOn taskProvider
                                kotlinCompileTask.source project.objects.sourceDirectorySet(taskProvider.get().name,
                                        taskProvider.get().name).srcDir(genSrcFolder)
                            }
                        } catch (UnknownTaskException) {
                            // Kotlin source not present
                        }

                        buildConfigTask.get().finalizedBy taskProvider

                    } else {
                        LOGGER.error("buildConfig NOT finalized: buildConfig task was not found")
                    }
                } catch (Exception e) {
                    // task for this variant not available
                    LOGGER.warn("configureConfigTasks: " + e.message)
                }

            }
        } catch (MissingPropertyException e) {
            // has no android closure or applicationVariants property
            LOGGER.warn("configureConfigTasks: " + e.message)

        } catch (Exception e) {
            // ignored: task doesn't exist if proguard not enabled
            LOGGER.warn("configureConfigTasks: " + e.message)
        }
    }

    protected boolean checkDexGuard(Project project) {
        return project.plugins.hasPlugin("dexguard")
    }

    protected boolean checkInstantApps(Project project) {
        return project.plugins.hasPlugin("com.android.instantapp") ||
                project.plugins.hasPlugin("com.android.feature") ||
                project.plugins.hasPlugin("com.android.dynamic-feature")
    }

    private boolean checkLibrary(Project project) {
        return project.plugins.hasPlugin("com.android.library")
    }

    /**
     * Seed the default variant build ID map.
     *
     * Currently, all variants use the same build ID
     *
     * @return Map of variant to build ID
     */
    protected Map<String, String> getDefaultBuildMap() {
        def buildIdCache = [:] as HashMap<String, String>

        getProjectVariants().each { variant ->
            buildIdCache.put(variant.name, BuildId.getBuildId(variant.name))
        }

        return buildIdCache
    }

    // FIXME
    private def getProjectVariants() {
        return buildHelper.variantAdapter.variants.get().values()
    }

    /**
     * Returns literal name of obfuscation compiler
     * @param project
     * @return Compiler name (R8, Proguard_603 or DexGuard)
     */
    String getMapProvider(Project project) {

        if (buildHelper.dexguardHelper.enabled) {
            return Proguard.Provider.DEXGUARD
        }

        if (GradleVersion.version(buildHelper.agpVersion) < GradleVersion.version("3.3")) {
            return Proguard.Provider.PROGUARD_603
        }

        // Gradle 3.3 was experimental R8, driven by properties
        if (checkLibrary(project) && project.hasProperty("android.enableR8.libraries")) {
            return (project.getProperty("android.enableR8.libraries").toLowerCase().equals("false") ? Proguard.Provider.PROGUARD_603 : Proguard.Provider.R8)
        }

        if (project.hasProperty("android.enableR8")) {
            return (project.getProperty("android.enableR8").toLowerCase().equals("false") ? Proguard.Provider.PROGUARD_603 : Proguard.Provider.R8)
        }

        // Gradle 3.4+ uses proguard by default, unless enabled by properties above
        if (GradleVersion.version(buildHelper.agpVersion) < GradleVersion.version("3.4")) {
            return Proguard.Provider.PROGUARD_603
        }

        // Gradle 3.4+ uses r8 by default, unless disabled by properties above
        return Proguard.Provider.DEFAULT
    }

    // FIXME
    private def getBuildTypeFromVariant(def variant) {
        try {
            return variant.buildType
        } catch (Exception e) {
            LOGGER.debug("getBuildConfigFromVariant: " + e)
        }
        return null
    }
}