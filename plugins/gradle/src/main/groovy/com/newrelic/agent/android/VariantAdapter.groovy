/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android

import com.newrelic.agent.android.agp4.AGP4Adapter
import com.newrelic.agent.android.agp7.AGP74Adapter
import com.newrelic.agent.android.agp9.AGP90Adapter
import com.newrelic.agent.android.obfuscation.Proguard
import com.newrelic.agent.util.BuildId
import org.gradle.api.Action
import org.gradle.api.InvalidUserDataException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.Logger
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.util.GradleVersion

abstract class VariantAdapter {
    final static Logger logger = NewRelicGradlePlugin.LOGGER

    final BuildHelper buildHelper
    final protected ObjectFactory objectFactory
    final protected MapProperty<String, Object> variants
    final protected MapProperty<String, BuildTypeAdapter> buildTypes
    final protected MapProperty<String, Object> metrics

    protected VariantAdapter(BuildHelper buildHelper) {
        this.buildHelper = buildHelper
        this.objectFactory = buildHelper.project.objects
        this.variants = objectFactory.mapProperty(String, Object)
        this.buildTypes = objectFactory.mapProperty(String, BuildTypeAdapter)
        this.metrics = objectFactory.mapProperty(String, Object)
    }

    /**
     * configure is called from the project.afterEvaluate() action
     * @param extension
     * @return
     */
    abstract VariantAdapter configure(NewRelicExtension extension)

    abstract TaskProvider getTransformProvider(String variantName)

    abstract Provider<BuildTypeAdapter> getBuildTypeProvider(String variantName)

    abstract TaskProvider getJavaCompileProvider(String variantName)

    abstract TaskProvider getConfigProvider(String variantName, Action action = null)

    abstract TaskProvider getMapUploadProvider(String variantName, Action action = null)

    abstract RegularFileProperty getMappingFileProvider(String variantName, Action action = null)

    abstract TaskProvider getReactNativeSourceMapUploadProvider(String variantName, Action action = null)

    /**
     * Wire up the correct instrumentation tasks, based on Gradle environment
     * @return VariantAdapter
     */
    static VariantAdapter register(BuildHelper buildHelper) {
        def currentGradleVersion = GradleVersion.version(buildHelper.getGradleVersion())
        def currentAgpVersion = GradleVersion.version(buildHelper.getAgpVersion())

        if (currentAgpVersion < GradleVersion.version("7.4")) {
            return new AGP4Adapter(buildHelper)
        }

        if (currentAgpVersion < GradleVersion.version("9.0")) {
            return new AGP74Adapter(buildHelper)
        }

        return new AGP90Adapter(buildHelper)
    }

    Set<String> getVariantNames() {
        return variants.getOrElse(Map.of()).keySet()
    }

    Collection<Object> getVariantValues() {
        return variants.getOrElse(Map.of()).values()
    }

    def getBuildMetrics() {
        metrics.get()
    }

    /**
     * Returns a cached variant<T>
     * @param variantName
     * @return Instance of this variant, null if missing
     */
    def withVariant(String variantName) {
        return variants.getting(variantName.toLowerCase()).getOrElse(null)
    }

    /**
     * Returns a cached BuildTypeAdapter
     * @param variantName
     * @return Instance for this variant, creating one if needed
     */
    BuildTypeAdapter withBuildType(String variantName) {
        if (!buildTypes.getting(variantName).isPresent()) {
            buildTypes.put(variantName, getBuildTypeProvider(variantName))
        }
        return buildTypes.getting(variantName).getOrElse(null)
    }

    /**
     * Return true if this variant (and optionally build type) should
     * be included for instrumentation
     *
     * @param variantName
     * @param buildType
     * @return
     */
    boolean shouldInstrumentVariant(String variantName, String buildType = variantName) {
        /**
         * We instrument *every* variant unless it's a test variant or excluded in plugin config

         * Per our _on-going spec_, users can specify full variant or build type *names* when
         * specifying what maps to upload.
         */
        if (buildHelper.extension.shouldIncludeVariant(variantName)) {
            return true
        }

        /**
         * Per our _on-going spec_, users can specify full variant or build type *names* in
         * plugin extension to determine what maps to upload.
         */
        return buildHelper.extension.shouldIncludeVariant(buildType)
    }

    boolean shouldUploadVariantMap(String variantName) {
        // do all the variants if variantMapsEnabled are disabled, or only those variants
        // provided in the extension. Default is the release variant or build type
        if (!buildHelper.extension.variantMapsEnabled.get()
                || buildHelper.extension.variantMapUploads.isEmpty()) {
            return true
        }

        /**
         * Per our _on-going spec_, users can specify full variant or build type *names* in
         * plugin extension to determine what maps to upload.
         */
        def variant = withVariant(variantName)
        def buildType = withBuildType(variantName)

        if (buildHelper.dexguardHelper?.getEnabled()) {
            if (buildHelper.dexguardHelper.variantConfigurations.get().containsKey(buildType.name)) {
                // TODO DG can't tell us if mapping disabled
            }
        }

        return (buildType.minified && (buildHelper.extension.shouldIncludeMapUpload(buildType.name) ||
                buildHelper.extension.shouldIncludeMapUpload(buildType.buildType)))
    }

    def wiredWithTransformProvider(String variantName) {
        return getTransformProvider(variantName)
    }

    def wiredWithConfigProvider(String variantName) {
        def configProvider = getConfigProvider(variantName) { configTask ->
            def buildType = withBuildType(variantName)
            def genSrcFolder = buildHelper.project.layout.buildDirectory.dir("generated/java/newrelicConfig${buildType.name.capitalize()}")

            configTask.sourceOutputDir.set(objectFactory.directoryProperty().value(genSrcFolder))
            configTask.mapProvider.set(objectFactory.property(String).value(buildHelper.getMapCompilerName()))
            configTask.minifyEnabled.set(objectFactory.property(Boolean).value(buildType.minified))
            configTask.buildMetrics.set(objectFactory.property(String).value(buildHelper.getBuildMetrics().toString()))
            configTask.configMetadata.set(configTask.sourceOutputDir.file(NewRelicConfigTask.METADATA))

            def uuid = objectFactory.property(String).convention(BuildId.getBuildId(variantName))
            def buildIdProvider = buildHelper.project.providers.fileContents(configTask.configMetadata).asText.orElse(uuid)

            configTask.buildId.set(buildIdProvider)

            configTask.onlyIf {
                def configClass = configTask.sourceOutputDir.file(NewRelicConfigTask.CONFIG_CLASS).get().asFile
                !configClass.exists() || !configClass.text.contains(configTask.buildId.get())
            }

            configTask.outputs.upToDateWhen {
                def meta = configTask.configMetadata.get().asFile
                def configClass = configTask.sourceOutputDir.file(NewRelicConfigTask.CONFIG_CLASS).get().asFile
                configClass.exists() &&
                        configClass.text.contains(configTask.buildId.get())
            }
        }

        return configProvider
    }

    def wiredWithMapUploadProvider(String variantName) {
        def mapUploadProvider = getMapUploadProvider(variantName) { mapUploadTask ->
            def variantMap = getMappingFileProvider(variantName)
            def uuid = objectFactory.property(String).value(BuildId.getBuildId(variantName))

            mapUploadTask.mappingFile.set(variantMap)
            mapUploadTask.taggedMappingFiles.set(buildHelper.project.files())
            mapUploadTask.projectRoot.set(buildHelper.project.layout.projectDirectory)
            mapUploadTask.buildId.convention(uuid)
            mapUploadTask.variantName.set(objectFactory.property(String).value(variantName))
            mapUploadTask.mapProvider.set(objectFactory.property(String).value(buildHelper.getMapCompilerName()))

            mapUploadTask.onlyIf {
                // Execute the task only if the given spec is satisfied. The spec will
                // be evaluated at task execution time, not during configuration.
                def tag = "${Proguard.NR_MAP_PREFIX}${mapUploadTask.buildId.get()}"
                def mf = it.mappingFile.asFile.get()
                def exists = mf.exists()
                def containsTag = exists && mf.text.contains(tag)
                def shouldExecute = exists && !containsTag

                it.logger.lifecycle("NewRelicMapUploadTask.onlyIf: Checking execution conditions for variant [${variantName}]")
                it.logger.lifecycle("NewRelicMapUploadTask.onlyIf: Mapping file path: ${mf.absolutePath}")
                it.logger.lifecycle("NewRelicMapUploadTask.onlyIf: Mapping file exists: ${exists}")
                it.logger.lifecycle("NewRelicMapUploadTask.onlyIf: Build ID tag: ${tag}")
                it.logger.lifecycle("NewRelicMapUploadTask.onlyIf: Mapping file contains tag: ${containsTag}")
                it.logger.lifecycle("NewRelicMapUploadTask.onlyIf: Should execute: ${shouldExecute}")

                return shouldExecute
            }

            mapUploadTask.outputs.upToDateWhen {
                def mf = it.mappingFile.asFile.get()
                def tag = "${Proguard.NR_MAP_PREFIX}${mapUploadTask.buildId.get()}"
                def exists = mf.exists()
                def containsTag = exists && mf.text.contains(tag)
                def isUpToDate = exists && containsTag

                it.logger.lifecycle("NewRelicMapUploadTask.upToDateWhen: Checking up-to-date status for variant [${variantName}]")
                it.logger.lifecycle("NewRelicMapUploadTask.upToDateWhen: Mapping file path: ${mf.absolutePath}")
                it.logger.lifecycle("NewRelicMapUploadTask.upToDateWhen: Mapping file exists: ${exists}")
                it.logger.lifecycle("NewRelicMapUploadTask.upToDateWhen: Contains tag: ${containsTag}")
                it.logger.lifecycle("NewRelicMapUploadTask.upToDateWhen: Is up-to-date: ${isUpToDate}")

                return isUpToDate
            }
        }

        if (buildHelper.checkApplication()) {
            def configProvider = getConfigProvider(variantName)
            def buildIdProvider = configProvider.flatMap { it.buildId }
            mapUploadProvider.configure { mapUploadTask ->
                mapUploadTask.dependsOn(configProvider)
                mapUploadTask.buildId.set(buildIdProvider)
            }
        }

        return mapUploadProvider
    }

    /**
     * Wire up React Native source map upload task for the given variant.
     */
    def wiredWithReactNativeSourceMapUploadProvider(String variantName) {
        def uploadProvider = getReactNativeSourceMapUploadProvider(variantName) { uploadTask ->
            def sourceMapPath = getReactNativeSourceMapPath(variantName)

            uploadTask.sourceMapFile.set(sourceMapPath)
            uploadTask.projectRoot.set(buildHelper.project.layout.projectDirectory)
            uploadTask.variantName.set(objectFactory.property(String).value(variantName))

            // Get build ID from config task if available, otherwise generate new
            def uuid = objectFactory.property(String).value(BuildId.getBuildId(variantName))
            uploadTask.buildId.convention(uuid)

            // Get app version from Android defaultConfig
            def versionName = getAppVersionName()
            uploadTask.appVersionId.set(objectFactory.property(String).value(versionName))

            // Only execute if source map file exists
            uploadTask.onlyIf {
                def sourceMap = it.sourceMapFile.asFile.getOrNull()
                def exists = sourceMap?.exists() ?: false

                it.logger.lifecycle("NewRelicReactNativeSourceMapUploadTask.onlyIf: Checking for variant [${variantName}]")
                it.logger.lifecycle("NewRelicReactNativeSourceMapUploadTask.onlyIf: Source map path: ${sourceMap?.absolutePath ?: 'not set'}")
                it.logger.lifecycle("NewRelicReactNativeSourceMapUploadTask.onlyIf: Source map exists: ${exists}")

                return exists
            }
        }

        // Wire build ID from config task if this is an application module
        if (buildHelper.checkApplication()) {
            def configProvider = getConfigProvider(variantName)
            def buildIdProvider = configProvider.flatMap { it.buildId }
            uploadProvider.configure { uploadTask ->
                uploadTask.dependsOn(configProvider)
                uploadTask.buildId.set(buildIdProvider)
            }
        }

        // Wire to React Native bundle tasks
        buildHelper.project.afterEvaluate {
            def vnc = variantName.capitalize()
            def wiredTaskNames = NewRelicReactNativeSourceMapUploadTask.wiredTaskNames(vnc)

            buildHelper.wireTaskProviderToDependencyNames(wiredTaskNames) { dependencyTask ->
                dependencyTask.configure {
                    finalizedBy(uploadProvider)
                }
                uploadProvider.configure {
                    shouldRunAfter(dependencyTask)
                }
            }
        }

        return uploadProvider
    }

    /**
     * Get the path to React Native source map file for the given variant.
     * Standard: build/generated/sourcemaps/react/release/index.android.bundle.map
     * Flavored: build/generated/sourcemaps/react/<flavorName>/release/index.android.bundle.map
     */
    RegularFileProperty getReactNativeSourceMapPath(String variantName) {
        def buildType = withBuildType(variantName)

        // Build the source map path based on variant structure
        def sourceMapsDir = buildHelper.project.layout.buildDirectory.dir("generated/sourcemaps/react")

        // Check for flavor-based path first, then standard path
        def sourceMapFile
        if (buildType.flavor && buildType.flavor != buildType.buildType) {
            // Flavored build: build/generated/sourcemaps/react/<flavor>/<buildType>/index.android.bundle.map
            sourceMapFile = sourceMapsDir.map { dir ->
                dir.file("${buildType.flavor}/${buildType.buildType}/index.android.bundle.map")
            }
        } else {
            // Standard build: build/generated/sourcemaps/react/<buildType>/index.android.bundle.map
            sourceMapFile = sourceMapsDir.map { dir ->
                dir.file("${buildType.buildType}/index.android.bundle.map")
            }
        }

        return objectFactory.fileProperty().fileProvider(sourceMapFile.map { it.asFile })
    }

    /**
     * Get the app version name from Android defaultConfig.
     */
    String getAppVersionName() {
        try {
            def versionName = buildHelper.android?.defaultConfig?.versionName
            return versionName ?: "1.0.0"
        } catch (Exception ignored) {
            return "1.0.0"
        }
    }

    /**
     * Check if React Native source map should be uploaded for this variant.
     */
    boolean shouldUploadReactNativeSourceMap(String variantName) {
        return buildHelper.checkReactNative() &&
                buildHelper.extension.shouldUploadReactNativeSourceMap(variantName)
    }

    /**
     * Register or return an existing provider instance for this name/type
     *
     * @param name Name of the variant task
     * @param clazz Class<T> of task
     * @param action Optional closure to pass to tasks container method
     * @return TaskProvider<clazz>
     */
    TaskProvider registerOrNamed(String name, Class clazz, Action action = null) {
        try {
            return buildHelper.project.tasks.register(name, clazz) {
                action?.execute(it)
            }
        } catch (InvalidUserDataException ignored) {
            return buildHelper.project.tasks.named(name, clazz) {
                action?.execute(it)
            }
        }
    }

    /**
     * Create and provide lazy-configuration for our data model
     */
    def assembleDataModel(String variantName) {
        // assemble and configure model
         if (buildHelper.extension.shouldIncludeVariant(variantName)) {
            wiredWithTransformProvider(variantName)
        }

        if (buildHelper.checkApplication()) {
            // inject config class into apks
            wiredWithConfigProvider(variantName)
        }

        if (shouldUploadVariantMap(variantName)) {
            // register map upload task(s)
            wiredWithMapUploadProvider(variantName)
        }

        if (shouldUploadReactNativeSourceMap(variantName)) {
            // register React Native source map upload task(s)
            wiredWithReactNativeSourceMapUploadProvider(variantName)
        }
    }

    static class BuildTypeAdapter {
        final String name
        final Boolean minified
        final String flavor
        final String buildType

        BuildTypeAdapter(String name, Boolean minified = false, String flavor = name, String buildType = name) {
            this.name = name
            this.minified = minified
            this.flavor = flavor
            this.buildType = buildType
        }
    }

}