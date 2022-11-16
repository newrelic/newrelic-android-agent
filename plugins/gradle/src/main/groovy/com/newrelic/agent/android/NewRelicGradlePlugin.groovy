/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android

import com.newrelic.agent.InstrumentationAgent
import com.newrelic.agent.android.obfuscation.Proguard
import com.newrelic.agent.util.BuildId
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.UnknownTaskException
import org.gradle.api.logging.Logger
import org.gradle.api.tasks.StopActionException
import org.gradle.util.GradleVersion

class NewRelicGradlePlugin implements Plugin<Project> {
    private static Logger logger

    public static final String PLUGIN_EXTENSION_NAME = "newrelic"

    public static final String DEX_PROGUARD = "proguard"
    public static final String DEX_R8 = "r8"

    private NewRelicExtension pluginExtension
    private NewRelicTransform newrelicTransform
    private BuildHelper buildHelper

    private def variantList
    private def buildIdMap

    static Logger getLogger() {
        return logger
    }

    @Override
    void apply(Project project) {
        logger = project.getLogger()
        buildHelper = new BuildHelper(project)

        BuildId.invalidate();

        def agentArgs = ""
        def prop = buildHelper.getSystemPropertyProvider(InstrumentationAgent.NR_AGENT_ARGS_KEY)

        if (prop.present) {
            agentArgs = prop.get()

        } else {
            if (logger.isDebugEnabled()) {
                agentArgs = "loglevel=DEBUG"
            } else if (logger.isInfoEnabled()) {
                agentArgs = "loglevel=INFO"
            } else if (logger.isWarnEnabled()) {
                agentArgs = "loglevel=WARN"
            } else if (logger.isErrorEnabled()) {
                agentArgs = "loglevel=ERROR"
            } else if (logger.isLifecycleEnabled()) {
                agentArgs = "loglevel=VERBOSE"
            }
        }

        InstrumentationAgent.parseAgentArgs(agentArgs)

        if (!project.hasProperty("android")) {
            throw new StopActionException("The New Relic agent plugin depends on the Android plugin." + BuildHelper.NEWLN +
                    "Please apply the Android plugin before the New Relic agent: " + BuildHelper.NEWLN +
                    "apply plugin: 'com.android.application' ('com.android.library', 'com.android.feature' or 'com.android.dynamic-feature')" + BuildHelper.NEWLN +
                    "apply plugin: 'newrelic'")
        }

        project.configure(project) {

            logger.info("[newrelic.info] New Relic Agent version: " + InstrumentationAgent.getVersion())

            pluginExtension = project.extensions.create(PLUGIN_EXTENSION_NAME, NewRelicExtension, project.getObjects())
            newrelicTransform = new NewRelicTransform(project, pluginExtension)

            if (project.hasProperty("android") && pluginExtension.isEnabled()) {

                project.afterEvaluate {

                    logger.debug("[newrelic.debug] Android Gradle plugin version: " + buildHelper.agpVersion)
                    logger.debug("[newrelic.debug] Gradle version: " + buildHelper.currentGradleVersion.version)
                    logger.debug("[newrelic.debug] Java version: " + buildHelper.getSystemPropertyProvider('java.version').get())

                    logger.debug("[newrelic.debug] Gradle configuration cache supported: " + buildHelper.configurationCacheSupported())
                    logger.debug("[newrelic.debug] Gradle configuration cache enabled: " + buildHelper.configurationCacheEnabled())

                    try {
                        // set global enable flag
                        BuildId.setVariantMapsEnabled(pluginExtension.variantMapsEnabled)

                        variantList = getProjectVariants(project)
                        buildIdMap = getDefaultBuildMap(project)

                        if (checkInstantApps(project)) {
                            logger.debug("[newrelic.debug] InstantApp detected.")
                        }

                        if (checkDexGuard(project)) {
                            logger.info("[newrelic.info] DexGuard detected.")
                            buildHelper.withDexGuardHelper(new DexGuardHelper(project))
                            configureDexGuardTasks(project)
                        }

                        configureConfigTasks(project)
                        configureTransformTasks(project)
                        configureMapUploadTasks(project)

                        // add extension to project's ext data
                        project.ext.newrelic = pluginExtension

                        logger.info("[newrelic.info] New Relic plugin loaded.")

                    } catch (MissingPropertyException e) {
                        logger.warn("[newrelic.warn] Not supported: " + e)
                    }
                }

                // Register the New Relic transformer
                logger.debug("[newrelic.debug] TransformAPI: registering NewRelicTransform(" + agentArgs + ")")
                android.registerTransform(newrelicTransform)

            } else {
                logger.info("[newrelic.info] New Relic Agent is disabled.")
            }
        }
    }

    // called during config phase
    protected configureDexGuard9Tasks(Project project) {
        variantList.each { variant ->
            if (pluginExtension.shouldIncludeMapUpload(variant.name)) {
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
                        logger.error("[newrelic.debug] configureDexGuard: " + e)
                        logger.error(DexGuardHelper.DEXGUARD_PLUGIN_ORDER_ERROR_MSG)
                    }
                }
            }
        }
    }

    // called during config phase
    protected configureDexGuardTasks(Project project) {
        logger.debug("[newrelic.debug] Dexguard version: " + buildHelper.dexguardHelper.version)

        if (buildHelper.dexguardHelper.isDexGuard9()) {
            return configureDexGuard9Tasks(project)
        }

        variantList.each { variant ->
            def variantNameCap = variant.name.capitalize()
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

                } catch (UnknownTaskException e) {
                    // not desugaring, ignore

                } catch (Exception e) {
                    logger.debug("[newrelic.debug] desugaring task [" + desugarTaskName + "]: " + e)

                } finally {
                    dexguardTask.dependsOn(classRewriterTask)
                    injectMapUploadFinalizer(project, dexguardTask, variant, null)
                }

                def javaCompileTask = BuildHelper.getVariantCompileTask(variant)
                if (javaCompileTask) {
                    javaCompileTask.finalizedBy classRewriterTask
                    logger.info("[newrelic.info] Task [" + dexguardTask.getName() +
                            "] has been configured for New Relic instrumentation.")
                }

                // bundleRelease -> dexguardBundleBundle -> dexguardRelease
                injectMapUploadFinalizer(project, "${DexGuardHelper.DEXGUARD_BUNDLE_TASK}${variantNameCap}", variant)

            } catch (UnknownTaskException e) {
                // task for this variant not available
                logger.debug("[newrelic.warn] configureDexGuard: " + e)
            }
        }
    }

    // called during config phase
    protected def injectMapUploadFinalizer(Project project, def targetTask, def variant, Closure closure) {
        if (pluginExtension.shouldIncludeMapUpload(variant.name)) {
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
                                logger.error("injectMapUploadFinalizer: $e")
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
                    logger.debug("[newrelic.debug] Variant[$variant.name] mapping file is null!")
                }
            } catch (UnknownTaskException e) {
                // task for this variant not available
            }
        }
    }

    // called during config phase
    protected def injectMapUploadFinalizer(Project project, String targetTaskName, def variant) {
        try {
            project.tasks.getByName(targetTaskName) { targetTask ->
                return injectMapUploadFinalizer(project, targetTask, variant, { File mappingFile ->
                    logger.debug("[injectMapUploadFinalizer] Injected NewRelicMapUploadTask[${mappingFile}] as finalizer to ${targetTask.name}")
                    mappingFile
                })
            }
        } catch (UnknownTaskException e) {
            // task for this variant not available
        }
    }

    // called during config phase
    protected void configureTransformTasks(Project project) {
        try {
            def transformer = NewRelicTransform.TRANSFORMER_NAME.capitalize()
            variantList.each { variant ->
                final boolean shouldExcludeVariant = pluginExtension.shouldExcludeVariant(variant.name) ||
                        pluginExtension.shouldExcludeVariant(variant.buildType.name)

                if (shouldExcludeVariant) {
                    logger.info("[newrelic.info] Excluding instrumentation of variant [" + variant.name + "]")
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
                                logger.error("[newrelic.error] Could not set state on transform task [${targetTask.name}]")
                            }
                        }
                    } catch (Exception e) {
                        // task for this variant not available. Log if not excluded
                        logger.debug("[newrelic.debug] configureTransformTasks: " + e)
                    }
                }
            }
        } catch (UnknownTaskException e) {
            // ignored: task doesn't exist if proguard not enabled
        }
    }

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

            def enabledVariantTypeNames = pluginExtension.variantMapUploadList ?: ['release']

            // do all the variants if variantMapsEnabled, or only those provided in the extension. Default is *release*.
            if (!pluginExtension.variantMapsEnabled || enabledVariantTypeNames == null) {
                logger.debug("[newrelic.debug] configureMapUploadTasks: all variants")
                enabledVariantTypeNames = []
            } else {
                logger.debug("[newrelic.debug] Maps will be tagged and uploaded for variants ${enabledVariantTypeNames}")
            }

            variantList.each { variant ->
                if (enabledVariantTypeNames.isEmpty() ||
                        enabledVariantTypeNames.contains(variant.name.toLowerCase()) ||
                        enabledVariantTypeNames.contains(variant.buildType.name.toLowerCase())) {
                    def buildType = getBuildTypeFromVariant(variant)

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
                            logger.debug("[newrelic.debug] Map upload ignored: build type[$variant.buildType.name] is not minified.")
                        }
                    }
                } else {
                    logger.debug("[newrelic.info] Map upload ignored for variant[$variant.name]")
                }
            }
        } catch (UnknownTaskException e) {
            // ignored: task doesn't exist if proguard not enabled
        }
    }

    // called during config phase
    protected void configureConfigTasks(Project project) {
        try {
            // only inject config class into apps
            if (checkLibrary(project)) {
                return
            }

            variantList.each { variant ->
                def buildId = BuildId.getBuildId(variant.name)
                def variantNameCap = variant.name.capitalize()
                logger.debug("[newrelic.debug] newrelicConfig${variantNameCap} buildId[${buildId}]")
                try {
                    // Branch on Gradle version
                    def buildConfigTask = buildHelper.getVariantBuildConfigTask(variant)

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
                        variant.registerJavaGeneratingTask(taskProvider, genSrcFolder.get().asFile)
                        variant.addJavaSourceFoldersToModel(genSrcFolder.get().asFile)

                        // must manually update the Kotlin compile tasks sourcesets (per variant)
                        try {
                            project.tasks.getByName("compile${variantNameCap}Kotlin") { kotlinCompileTask ->
                                kotlinCompileTask.dependsOn taskProvider
                                kotlinCompileTask.source project.objects.sourceDirectorySet(taskProvider.get().name,
                                        taskProvider.get().name).srcDir(genSrcFolder)
                            }
                        } catch (UnknownTaskException e) {
                            // Kotlin source not present
                        }

                        buildConfigTask.get().finalizedBy taskProvider

                    } else {
                        logger.error("[newrelic.error] buildConfig NOT finalized: buildConfig task was not found")
                    }
                } catch (Exception e) {
                    // task for this variant not available
                    logger.warn("[newrelic.warn] configureConfigTasks: " + e)
                }
            }
        } catch (MissingPropertyException e) {
            // has no android closure or applicationVariants property
            logger.warn("[newrelic.warn] configureConfigTasks: " + e)

        } catch (Exception e) {
            // ignored: task doesn't exist if proguard not enabled
            logger.warn("[newrelic.warn] configureConfigTasks: " + e)
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
 * @param project
 * @return Map of variant to build ID
 */
    protected Map<String, String> getDefaultBuildMap(Project project) {
        Map<String, String> buildIdCache = [:]
        getProjectVariants(project).each { variant ->
            buildIdCache.putIfAbsent(variant.name, BuildId.getBuildId(variant.name))
        }

        return buildIdCache
    }

    private getProjectVariants(Project project) {
        def variants = []

        try {
            def android = project.getProperties().get("android")
            if (android != null) {
                if (android.hasProperty("applicationVariants")) {
                    variants = android.applicationVariants
                } else if (android.hasProperty("featureVariants")) {
                    variants = android.featureVariants
                } else if (android.hasProperty("libraryVariants")) {
                    variants = android.libraryVariants
                }
            }

        } catch (MissingPropertyException) {
            // has no android closure or variants property
            logger.warn("[newrelic.warn] getProjectVariants: " + e)
        }
        catch (Exception e) {
            logger.warn("[newrelic.warn] getProjectVariants: " + e)
        }

        return variants
    }

    String getMapProvider(Project project) {

        if (buildHelper.dexguardHelper.enabled) {
            return Proguard.Provider.DEXGUARD
        }

        if (buildHelper.agpVersion < GradleVersion.version("3.3")) {
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
        if (buildHelper.agpVersion < GradleVersion.version("3.4")) {
            return Proguard.Provider.PROGUARD_603
        }

        // Gradle 3.4+ uses r8 by default, unless disabled by properties above
        return Proguard.Provider.DEFAULT
    }

    private getBuildTypeFromVariant(def variant) {
        try {
            return variant.buildType
        } catch (Exception e) {
            logger.debug("[newrelic.warn] getBuildConfigFromVariant: " + e)
        }
        return null
    }
}