/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android

import com.newrelic.agent.compile.RewriterAgent
import com.newrelic.agent.obfuscation.Proguard
import com.newrelic.agent.util.BuildId
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.UnknownTaskException
import org.gradle.api.logging.Logger
import org.gradle.api.tasks.StopActionException
import org.gradle.util.GradleVersion


class NewRelicGradlePlugin implements Plugin<Project> {
    private static Logger logger

    public static final String PLUGIN_EXTENSION_NAME = "newrelic"
    public static final String NR_AGENT_ARGS_KEY = "NewRelic.AgentArgs"

    public static final String DEX_PROGUARD = "proguard"
    public static final String DEX_R8 = "r8"

    private NewRelicExtension pluginExtension
    private NewRelicTransform newrelicTransform
    private BuildHelper buildHelper

    private def variantList
    private def buildIdMap
    private def buildTypesMap

    static Logger getLogger() {
        return logger
    }

    @Override
    void apply(Project project) {
        final String newLn = System.getProperty("line.separator")

        logger = project.getLogger()
        buildHelper = new BuildHelper(project)

        BuildId.invalidate();
        System.clearProperty(NR_AGENT_ARGS_KEY)

        if (logger.isDebugEnabled()) {
            System.setProperty(NR_AGENT_ARGS_KEY, "loglevel=DEBUG")
        } else if (logger.isInfoEnabled()) {
            System.setProperty(NR_AGENT_ARGS_KEY, "loglevel=INFO")
        } else if (logger.isWarnEnabled()) {
            System.setProperty(NR_AGENT_ARGS_KEY, "loglevel=WARN")
        } else if (logger.isErrorEnabled()) {
            System.setProperty(NR_AGENT_ARGS_KEY, "loglevel=ERROR")
        } else if (logger.isLifecycleEnabled()) {
            System.setProperty(NR_AGENT_ARGS_KEY, "loglevel=VERBOSE")
        }

        if (!project.hasProperty("android")) {
            throw new StopActionException("The New Relic agent plugin depends on the Android plugin." + newLn +
                    "Please apply the Android plugin before the New Relic agent: " + newLn +
                    "apply plugin: 'com.android.application' ('com.android.library' or 'com.android.feature')" + newLn +
                    "apply plugin: 'newrelic'")
        }

        project.configure(project) {

            final String agentArgs = System.getProperty(NR_AGENT_ARGS_KEY)

            logger.info("[newrelic.info] New Relic Agent version: " + RewriterAgent.getVersion())

            pluginExtension = project.extensions.create(PLUGIN_EXTENSION_NAME, NewRelicExtension, project)
            newrelicTransform = new NewRelicTransform(project, pluginExtension, agentArgs)

            if (project.hasProperty("android") && pluginExtension.isEnabled()) {

                project.afterEvaluate {

                    logger.debug("[newrelic.debug] Android Gradle plugin version: " + buildHelper.agpVersion)
                    logger.debug("[newrelic.debug] Gradle version: " + buildHelper.currentGradleVersion.version)
                    logger.debug("[newrelic.debug] Java version: " + System.properties['java.version'])

                    try {
                        // set global enable flag
                        BuildId.setVariantMapsEnabled(pluginExtension.variantMapsEnabled)

                        variantList = getProjectVariants(project)
                        buildIdMap = getDefaultBuildMap(project)
                        buildTypesMap = getProjectBuildTypes(project)

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
                        // throw new StopActionException(DexGuardHelper.DEXGUARD_PLUGIN_ORDER_ERROR_MSG)
                    }
                }
            }
        }
    }

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
                    inputVariant = variant
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

    protected def injectMapUploadFinalizer(Project project, Task targetTask, def variant, Closure closure) {
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
                        mapUploadTask = project.tasks.register(injectedTaskName, NewRelicMapUploadTask) {
                            inputVariantName = variant.name
                            mapProvider = getMapProvider(project)
                            mapInput = mappingFile
                            onlyIf { mappingFile.exists() }
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
                                targetTask.doFirst {
                                    targetTask.transform.withTransformState(variant.name, shouldExcludeVariant)
                                }
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

    protected void configureMapUploadTasks(Project project) {
        try {
            // map projects do not produce maps
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
                    def buildType = getBuildTypeFromVariant(project, variant)

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

    protected void configureConfigTasks(Project project) {
        try {
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
                        def genSrcFolder = project.file("${project.buildDir}/generated/source/newrelicConfig/${variant.dirName}")
                        def taskProvider = project.tasks.register("newrelicConfig${variantNameCap}", NewRelicConfigTask) {
                            inputVariantName = variant.name
                            mapProvider = getMapProvider(project)
                            buildTypeMinified = getBuildTypeFromVariant(project, variant).minifyEnabled
                            sourceOutputDir = genSrcFolder
                        }

                        // update the variant model
                        variant.registerJavaGeneratingTask(taskProvider.get(), genSrcFolder)
                        variant.addJavaSourceFoldersToModel(genSrcFolder)

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

                        buildConfigTask.finalizedBy taskProvider.get()

                    } else {
                        logger.error("[newrelic.error] buildConfig NOT finalized: buildConfig task was not found")
                    }
                } catch (UnknownTaskException e) {
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

    private String getMapProvider(Project project) {

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

    private getProjectBuildTypes(Project project) {
        Map<String, String> buildTypesCache = [:]
        try {
            def android = project.getProperties().get("android")
            if (android) {
                android.buildTypes.each { buildType ->
                    buildTypesCache.putIfAbsent(buildType.name.toLowerCase(), buildType)
                }
            }
        } catch (Exception e) {
            logger.debug("[newrelic.warn] getProjectBuildTypes: " + e)
        }
        return buildTypesCache
    }

    private getBuildTypeFromVariant(Project project, def variant) {
        try {
            def android = project.getProperties().get("android")
            if (android) {
                return android.buildTypes[variant.buildType.name]
            }
        } catch (Exception e) {
            logger.debug("[newrelic.warn] getBuildConfigFromVariant: " + e)
        }
        return null
    }
}