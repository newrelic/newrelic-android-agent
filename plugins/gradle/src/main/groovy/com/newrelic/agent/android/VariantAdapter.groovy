/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android

import com.newrelic.agent.util.BuildId

// Gradle 7.2
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.AppExtension
import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.artifact.MultipleArtifact
import com.android.build.api.variant.VariantSelector

// Gradle 7.4
import com.android.build.api.variant.CanMinifyCode
import com.android.build.api.variant.ScopedArtifacts
import com.android.build.api.artifact.ScopedArtifact

import org.gradle.api.InvalidUserDataException
import org.gradle.api.UnknownTaskException
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

    protected VariantAdapter(BuildHelper buildHelper) {
        this.buildHelper = buildHelper
        this.objectFactory = buildHelper.project.objects
        this.variants = objectFactory.mapProperty(String, Object)
    }

    Set<String> getVariantNames() {
        return variants.getOrElse(Set.of()).keySet()
    }

    Collection<Object> getVariantValues() {
        return variants.getOrElse(Set.of()).values()
    }

    abstract VariantAdapter configure(NewRelicExtension extension)

    abstract TaskProvider getTransformProvider(String variantName)

    abstract Provider<BuildTypeAdapter> getBuildTypeProvider(String variantName)

    abstract TaskProvider getJavaCompileProvider(String variantName)

    abstract TaskProvider getConfigProvider(String variantName)

    abstract Provider<String> getMappingProvider(String variantName)

    abstract TaskProvider getMapUploadProvider(String variantName)

    abstract RegularFileProperty getMappingFileProvider(String variantName)

    def withVariant(String variantName) {
        variants.get().get(variantName.toLowerCase())
    }

    boolean shouldInstrumentVariant(def variantName) {
        // we instrument *every* variant unless it's a test variant or excluded in plugin config
        return buildHelper.extension.shouldIncludeVariant(variantName.toLowerCase())
    }

    boolean shouldUploadVariantMap(def variantName) {
        // do all the variants if variantMapsEnabled are disabled, or only those variants
        // provided in the extension. Default is the release variant or build type
        if (!buildHelper.extension.variantMapsEnabled.get() || buildHelper.extension.variantMapUploadList.isEmpty()) {
            return true
        }

        return withVariant(variantName).tap { variant ->
            /**
             * Per our _ongoing spec_, users can specify full variant or build type *names* when
             * specifying what maps to upload.
             */
            def buildType = getBuildTypeProvider(variantName)

            (buildType.get().minified && (buildHelper.extension.shouldIncludeMapUpload(variantName) ||
                    buildHelper.extension.shouldIncludeMapUpload(buildType.get().name)))
        }
    }

    /**
     * Wire up the correct instrumentation tasks, based on Gradle environment
     * @return VariantAdapter
     */
    static VariantAdapter register(BuildHelper buildHelper) {
        def currentGradleVersion = GradleVersion.version(buildHelper.getGradleVersion())

        if (currentGradleVersion >= GradleVersion.version("7.4")) {
            return new AGP74Adapter(buildHelper)
        } else if (currentGradleVersion >= GradleVersion.version("7.0")) {
            return new AGP70Adapter(buildHelper)
        } else {
            return new AGP4Adapter(buildHelper)
        }
    }

    /**
     * When interacting with AGP, use specially made extension points instead of registering
     * the typical Gradle lifecycle callbacks (such as afterEvaluate()) or setting up explicit
     * Task dependencies. Tasks created by AGP are considered implementation details and are not
     * exposed as a public API. You must avoid trying to get instances of the Task objects or
     * guessing the Task names and adding callbacks or dependencies to those Task objects directly.
     *
     * @param variantName
     * @return
     */
    def wiredWithConfigProvider(String variantName) {
        def configProvider = getConfigProvider(variantName)

        configProvider.configure { configTask ->
            def objectFactory = buildHelper.project.objects
            def buildId = objectFactory.property(String).value(BuildId.getBuildId(variantName))
            def buildType = getBuildTypeProvider(variantName)
            def genSrcFolder = buildHelper.project.layout.buildDirectory.dir("generated/source/newrelicConfig/${variant.dirName}")

            logger.debug("${configTask.name} buildId[${buildId.get()}]")

            configTask.buildId = buildId
            configTask.sourceOutputDir.convention(genSrcFolder)
            configTask.mapProvider = objectFactory.property(String).value(buildHelper.getMapCompilerName())
            configTask.minifyEnabled = objectFactory.property(Boolean).value(buildType.getOrElse(true).minified)
            configTask.buildMetrics = objectFactory.property(String).value(buildHelper.buildMetrics().toString())

            withVariant(variantName).sources.assets?.addGeneratedSourceDirectory(configTask, genSrcFolder)
        }

        return configProvider
    }

    def wiredWithMapUploadProvider(String variantName) {
        def mapUploadProvider = getMapUploadProvider(variantName)

        mapUploadProvider.configure { mapUploadTask ->
            mapUploadTask.buildId = buildId
            mapUploadTask.variantName = variantName
            mapUploadTask.mapProvider = getMappingProvider(variantName)
            mapUploadTask.mappingFile = getMappingFileProvider(variantName)
            mapUploadTask.projectRoot = buildHelper.project.layout.projectDirectory

            onlyIf {
                // Execute the task only if the given spec is satisfied. The spec will
                // be evaluated at task execution time, not during configuration.
                true // FIXME mappingFileProperty.get().asFile.exists()
            }

            // FIXME
            mapUploadTask.outputs.upToDateWhen { false }

/*
            withVariant(variantName)?.tap { variant ->
                mapUploadTask.mappingFiles = buildHelper.project.provider {
                    buildHelper.project.files(variant.artifacts.get(SingleArtifact.OBFUSCATION_MAPPING_FILE))
                }

                buildHelper.getMapUploadTaskDependencies(variant.name).get().each { taskProvider ->
                    //  buildHelper.wiredWithTaskFinalizers(taskName, variant.name)
                    taskProvider.configure {
                        mapUploadTask.mappingFileProperty = it.flatMap { it.outputFile }
                    }
                }
            }
*/
        }

        mapUploadProvider.get()

        return mapUploadProvider
    }

    /**
     * Register or return an existing provider instance for this name/type
     *
     * @param name Name of the variant task
     * @param clazz Class<T> of task
     * @return TaskProvider<clazz>
     */
    TaskProvider registerOrNamed(String name, Class clazz) {
        try {
            return buildHelper.project.tasks.register(name, clazz)
        } catch (InvalidUserDataException ignored) {
            return buildHelper.project.tasks.named(name, clazz)
        }
    }

    /**
     * Create and provide lazy-configuration for our data model
     */
    def assembleDataModel(String variantName) {
        wiredWithTransformProvider(variantName, getTransformProvider(variantName))
        wiredWithConfigProvider(variantName)

        if (shouldUploadVariantMap(variantName)) {
            // wire up map upload task(s)
            wiredWithMapUploadProvider(variantName)

            buildHelper.getMapUploadTaskDependencies(variant.name).get().each { taskProvider ->
                buildHelper.wiredWithTaskProvider(taskProvider, variantName)
            }
        }
    }

    static class AGP4Adapter extends VariantAdapter {
        final def android
        final def transformer

        AGP4Adapter(BuildHelper buildHelper) {
            super(buildHelper)
            this.android = buildHelper.androidExtension
            this.transformer = new NewRelicTransform(buildHelper.project, buildHelper.extension)

            // Register the New Relic transformer
            logger.debug("TransformAPI: registering NewRelicTransform")
            android.registerTransform(transformer)
        }

        @Override
        VariantAdapter configure(NewRelicExtension extension) {
            variants.empty()

            // the plugin populates the extension only after the project is evaluated
            if (android instanceof AppExtension) {
                (android as AppExtension).applicationVariants.each { variant ->
                    if (shouldInstrumentVariant(variant.name)) {
                        variants.put(variant.name.toLowerCase(), variant)

                        // assemble and configure model
                        wiredWithTransformProvider(variant.name, getTransformProvider(variant.name))
                        wiredWithConfigProvider(variant.name)

                        if (shouldUploadVariantMap(variant.name)) {
                            // wire up map upload task(s)
                            wiredWithMapUploadProvider(variant.name)

                            buildHelper.getMapUploadTaskDependencies(variant.name).get().each { taskProvider ->
                                buildHelper.wiredWithTaskProvider(taskProvider, variant.name)
                            }
                        }
                    }
                }
            } else if (android instanceof LibraryExtension) {
                (android as LibraryExtension).libraryVariants.each { variant ->
                    if (shouldInstrumentVariant(variant.name)) {
                        variants.put(variant.name.toLowerCase(), variant)
                        assembleDataModel(variant.name)
                    }
                }
            }

            return this
        }

        @Override
        TaskProvider getTransformProvider(String variantName) {
            def variant = withVariant(variantName)

            try {
                final String NAME = "transformClassesWith"
                def transformerName = NewRelicTransform.TRANSFORMER_NAME.capitalize()
                def variantNameCap = variant.name.capitalize()

                return buildHelper.project.tasks.named("${NAME}${transformerName}For${variantNameCap}")
            } catch (UnknownTaskException ignored) {
                // ignored
            }

            null
        }

        @Override
        Provider<Object> getBuildTypeProvider(String variantName) {
            def variant = withVariant(variantName)
            def buildType = new BuildTypeAdapter(variant.buildType.name, variant.buildType.minifiedEnabled)
            return objectFactory.property(Object).value(buildType)
        }

        @Override
        TaskProvider getJavaCompileProvider(String variantName) {
            def variant = withVariant(variantName)

            try {
                return variant.getJavaCompileProvider()
            } catch (Exception e) {
                logger.error("getJavaCompileProvider: $e")
            }

            return variant.javaCompiler
        }

        @Override
        TaskProvider getConfigProvider(String variantName) {
            def variant = withVariant(variantName)
            def buildConfigProvider

            try {
                buildConfigProvider = variant.getGenerateBuildConfigProvider()
            } catch (Exception e) {
                logger.error("getConfigProvider: $e")
                buildConfigProvider = variant.generateBuildConfig
            }

            if (buildConfigProvider) {
                def variantNameCap = variant.name.capitalize()
                def genSrcFolder = buildHelper.project.layout.buildDirectory.dir("generated/source/newrelicConfig/${variant.dirName}")
                def taskName = "${NewRelicConfigTask.NAME}${variantNameCap}"
                def configTaskProvider = registerOrNamed(taskName, NewRelicConfigTask.class)

                try {
                    /**
                     * Update the variant model
                     * @see{https://android.googlesource.com/platform/tools/build/+/master/gradle/src/main/groovy/com/android/build/gradle/api/BaseVariant.java}*
                     */
                    variant.registerJavaGeneratingTask(configTaskProvider, genSrcFolder.get().asFile)
                } catch (Exception e) {
                    logger.error("getConfigProvider: $e")
                }

                try {
                    variant.addJavaSourceFoldersToModel(genSrcFolder.get().asFile)
                } catch (Exception e) {
                    logger.warn("getConfigProvider: $e")
                }

                // must manually update the Kotlin compile tasks source sets (per variant)
                try {
                    buildHelper.project.tasks.named("compile${variantNameCap}Kotlin") {
                        // FIXME dependsOn configTaskProvider
                        source objectFactory.sourceDirectorySet(configTaskProvider.getName(),
                                configTaskProvider.getName()).srcDir(genSrcFolder)
                    }
                } catch (UnknownTaskException ignored) {
                    // Kotlin source not present
                }

                buildConfigProvider.configure {
                    // FIXME finalizedBy configTaskProvider
                }

                return configTaskProvider

            } else {
                logger.error("getConfigProvider: buildConfig NOT finalized: buildConfig task was not found")
            }

            logger.error("getConfigProvider: configClass not provided!")
            null
        }

        @Override
        Provider<String> getMappingProvider(String variantName) {
            // TODO This needn't be a provider
            return objectFactory.property(String).convention(buildHelper.getMapCompilerName())
        }

        @Override
        TaskProvider getMapUploadProvider(String variantName) {
            return registerOrNamed("${NewRelicMapUploadTask.NAME}${variantName.capitalize()}", NewRelicMapUploadTask.class)
        }

        /**
         * Returns a RegularFileProperty representing the variant's mapping file
         * This can be null for Dexguard 8.5.+
         *
         * If the extension's variantConfiguration is present, use that map file
         * if one is provided
         *
         * @param variant
         * @return File(variantMappingFile)
         */
        @Override
        RegularFileProperty getMappingFileProvider(String variantName) {
            withVariant(variantName).with { variant ->

                // dexguard maps are handled separately
                if (buildHelper.checkDexGuard()) {
                    return buildHelper.dexguardHelper.getDefaultMapPathProvider(variant.dirName)
                }

                def variantConfiguration = buildHelper.extension.variantConfigurations.findByName(variant.name)

                if (variantConfiguration && variantConfiguration.mappingFile) {
                    def variantMappingFile = variantConfiguration.mappingFile.getAbsolutePath()
                            .replace("<name>", variant.name)
                            .replace("<dirName>", variant.dirName)

                    return objectFactory.fileProperty().value(variantMappingFile)
                }

                try {
                    def provider = variant.getMappingFileProvider()
                    if (provider.isPresent()) {
                        def fileCollection = provider.get()
                        if (!fileCollection.empty) {
                            return objectFactory.fileProperty().set(fileCollection.singleFile)
                        }
                    }
                } catch (Exception ignore) {
                    logger.error("getMappingFileProvider: Map provider not found in variant [$variant.name]")
                }

                // If all else fails, default to default map locations
                return buildHelper.getDefaultMapPathProvider(variant.dirName)
            }
        }
    }

    static class AGP70Adapter extends VariantAdapter {
        final AndroidComponentsExtension androidComponents
        final VariantSelector variantSelector

        AGP70Adapter(BuildHelper buildHelper) {
            super(buildHelper)

            this.androidComponents = buildHelper.androidComponentsExtension as AndroidComponentsExtension
            this.variantSelector = androidComponents.selector().all()

            /*
             * Plugins can add any number of operations on artifacts into the pipeline from
             * the onVariants() callback, and AGP will ensure they are chained properly so that all
             * tasks run at the right time and artifacts are correctly produced and updated.
             *
             * This means that when an operation changes any outputs by appending, replacing, or
             * transforming them, the next operation will see the updated version of these
             * artifacts as inputs, and so on.
             */
            androidComponents.onVariants(variantSelector, { variant ->

                if (shouldInstrumentVariant(variant.name)) {
                    variants.put(variant.name.toLowerCase(), variant)

                    // assemble and configure model
                    wireWithInstrumentationProvider(variant.name)
                    wiredWithConfigProvider(variant.name)

                    if (shouldUploadVariantMap(variant.name)) {
                        // wire up map upload task(s)
                        // FIXME wiredWithMapUploadProvider(variant.name)
                    }
                }
            })
        }

        @Override
        VariantAdapter configure(NewRelicExtension extension) {
            // Variants are locked and no longer mutable
            getVariantValues().each { variant ->
                if (shouldUploadVariantMap(variant.name)) {
                    // wire up map upload task(s)
                    // FIXME wiredWithMapUploadProvider(variant.name)
                }
            }

            return this
        }

        @Override
        TaskProvider getTransformProvider(String variantName) {
            return registerOrNamed("${ClassTransformWrapperTask.NAME}ClassesAndJars${variantName.capitalize()}", ClassTransformWrapperTask.class)
        }

        @Override
        Provider<BuildTypeAdapter> getBuildTypeProvider(String variantName) {
            def variant = withVariant(variantName)
            def buildType = new BuildTypeAdapter(variant.buildType, variant.flavorName, variant.minifiedEnabled)
            return objectFactory.property(Object).value(buildType)
        }

        @Override
        TaskProvider getJavaCompileProvider(String variantName) {
            def variant = withVariant(variantName)
            return objectFactory.property(Object).value(variant.javaCompile)

            // return buildHelper.project.tasks.register("javaCompile${variantName.capitalize()}", JavaCompile)
        }

        @Override
        TaskProvider getConfigProvider(String variantName) {
            return registerOrNamed("${NewRelicConfigTask.NAME}${variantName.capitalize()}", NewRelicConfigTask.class)
        }

        @Override
        Provider<String> getMappingProvider(String variantName) {
            return objectFactory.property(String).convention(buildHelper.getMapCompilerName())
        }

        @Override
        TaskProvider getMapUploadProvider(String variantName) {
            return registerOrNamed("${NewRelicMapUploadTask.NAME}${variantName.capitalize()}", NewRelicMapUploadTask.class)
        }

        @Override
        RegularFileProperty getMappingFileProvider(String variantName) {
            return withVariant(variantName).artifacts.get(SingleArtifact.OBFUSCATION_MAPPING_FILE.INSTANCE)
        }

        def wireWithInstrumentationProvider(String variantName) {
            def transformProvider = getTransformProvider(variantName)
            def variant = withVariant(variantName)

            transformProvider.configure {
                classJars.set(variant.artifacts.getAll(MultipleArtifact.ALL_CLASSES_JARS.INSTANCE))
            }

            variant.artifacts
                    .use(transformProvider)
                    .wiredWith({ it.getClassDirectories() }, { it.getOutput() })
                    .toTransform(MultipleArtifact.ALL_CLASSES_DIRS.INSTANCE)
        }

    }

    static class AGP74Adapter extends AGP70Adapter {
        AGP74Adapter(BuildHelper buildHelper) {
            super(buildHelper)
        }

        /* TODO
        @Override
        TaskProvider getTransformProvider(String variantName) {
            def provider = super.getTransformProvider(variantName)
            variant.transformClassesWith(TraceClassVisitorFactory.class, InstrumentationScope.PROJECT) { params ->
                params.getTruth().set(true)
            }
            variant.setAsmFramesComputationMode(FramesComputationMode.COMPUTE_FRAMES_FOR_ALL_CLASSES)

            provider
        }
        /* TODO */

        @Override
        Provider<BuildTypeAdapter> getBuildTypeProvider(String variantName) {
            def variant = withVariant(variantName)
            def buildType = new BuildTypeAdapter(variant.buildType, (variant as CanMinifyCode).isMinifyEnabled())

            return buildHelper.project.objects.property(Object).value(buildType)
        }

        @Override
        def wireWithInstrumentationProvider(String variantName) {
            def transformProvider = getTransformProvider(variantName)

            withVariant(variantName).artifacts
                    .forScope(ScopedArtifacts.Scope.PROJECT)
                    .use(transformProvider)
                    .toTransform(ScopedArtifact.CLASSES.INSTANCE, { it.getClassJars() }, { it.getClassDirectories() }, { it.getOutput() })
        }
    }

    static class BuildTypeAdapter {
        final String name
        final String flavor
        final Boolean minified

        BuildTypeAdapter(String name, Boolean isMinified) {
            this.name = name
            this.flavor = ""
            this.minified = isMinified
        }

        BuildTypeAdapter(String name, String flavor, Boolean isMinified) {
            this.name = name
            this.flavor = flavor
            this.minified = isMinified
        }
    }

}