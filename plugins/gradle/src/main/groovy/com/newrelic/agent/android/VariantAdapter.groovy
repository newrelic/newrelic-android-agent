/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android


import com.newrelic.agent.android.agp4.AGP4Adapter
import com.newrelic.agent.android.agp7.AGP70Adapter
import com.newrelic.agent.android.agp7.AGP74Adapter
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

    /**
     * Wire up the correct instrumentation tasks, based on Gradle environment
     * @return VariantAdapter
     */
    static VariantAdapter register(BuildHelper buildHelper) {
        def currentGradleVersion = GradleVersion.version(buildHelper.getGradleVersion())

        if (currentGradleVersion >= GradleVersion.version("7.4")) {
            return new AGP74Adapter(buildHelper)
        } else if (currentGradleVersion >= GradleVersion.version("7.2")) {
            if (GradleVersion.version(buildHelper.agpVersion) < GradleVersion.version("7.2")) {
                return new AGP4Adapter(buildHelper)
            }
            return new AGP70Adapter(buildHelper)
        } else {
            return new AGP4Adapter(buildHelper)
        }
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
        return variants.get().get(variantName.toLowerCase())
    }

    /**
     * Returns a cached BuildTypeAdapter
     * @param variantName
     * @return Instance for this variant, creating one if needed
     */
    BuildTypeAdapter withBuildType(String variantName) {
        if (!buildTypes.getting(variantName).isPresent()) {
            buildTypes.put(variantName, getBuildTypeProvider(variantName).get())
        }
        return buildTypes.getting(variantName).get()
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
                || buildHelper.extension.variantMapUploadList.isEmpty()) {
            return true
        }

        /**
         * Per our _on-going spec_, users can specify full variant or build type *names* in
         * plugin extension to determine what maps to upload.
         */
        def variant = withVariant(variantName)
        def buildType = withBuildType(variantName)

        return (buildType.minified && (buildHelper.extension.shouldIncludeMapUpload(buildType.name) ||
                buildHelper.extension.shouldIncludeMapUpload(buildType.buildType)))
    }

    def wiredWithTransformProvider(String variantName) {
        return getTransformProvider(variantName)
    }

    def wiredWithConfigProvider(String variantName) {
        def configProvider = getConfigProvider(variantName) { configTask ->
            def buildType = withBuildType(variantName)
            def genSrcFolder = buildHelper.project.layout.buildDirectory.dir("generated/java/newrelicConfig/${buildType.name}")

            configTask.sourceOutputDir.set(objectFactory.directoryProperty().value(genSrcFolder))
            configTask.buildId.set(objectFactory.property(String).value(BuildId.getBuildId(variantName)))
            configTask.mapProvider.set(objectFactory.property(String).value(buildHelper.getMapCompilerName()))
            configTask.minifyEnabled.set(objectFactory.property(Boolean).value(buildType.minified))
            configTask.buildMetrics.set(objectFactory.property(String).value(buildHelper.getBuildMetrics().toString()))

            configTask.outputs.upToDateWhen {
                configTask.sourceOutputDir.file(NewRelicConfigTask.CONFIG_CLASS).get().asFile.with() {
                    exists() && text.contains(configTask.buildId.get())
                }
            }
        }

        return configProvider
    }

    def wiredWithMapUploadProvider(String variantName) {
        def mapUploadProvider = getMapUploadProvider(variantName) { mapUploadTask ->
            def variantMap = getMappingFileProvider(variantName)

            mapUploadTask.mappingFile.set(variantMap)
            mapUploadTask.taggedMappingFiles.set(buildHelper.project.files())
            mapUploadTask.projectRoot.set(buildHelper.project.layout.projectDirectory)
            mapUploadTask.buildId.set(objectFactory.property(String).value(BuildId.getBuildId(variantName)))
            mapUploadTask.variantName.set(objectFactory.property(String).value(variantName))
            mapUploadTask.mapProvider.set(objectFactory.property(String).value(buildHelper.getMapCompilerName()))

            mapUploadTask.onlyIf {
                // Execute the task only if the given spec is satisfied. The spec will
                // be evaluated at task execution time, not during configuration.
                mapUploadTask.mappingFile.asFile.get().exists()
            }

            mapUploadTask.outputs.upToDateWhen {
                mapUploadTask.mappingFile.asFile.get().with {
                    exists() && text.contains(Proguard.NR_MAP_PREFIX + mapUploadTask.buildId.get())
                }
            }
        }

        return mapUploadProvider
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
                // action?.execute(it)
            }
        }
    }

    /**
     * Create and provide lazy-configuration for our data model
     */
    def assembleDataModel(String variantName) {
        // assemble and configure model
        wiredWithTransformProvider(variantName)
        if (buildHelper.project.plugins.hasPlugin("com.android.application")) {
            // inject config lass into apps
            wiredWithConfigProvider(variantName)
        }
        if (shouldUploadVariantMap(variantName)) {
            // register map upload task(s)
            wiredWithMapUploadProvider(variantName)
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