/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android


import com.android.build.api.artifact.MultipleArtifact
import com.android.build.api.artifact.SingleArtifact
import com.android.build.gradle.AppExtension
import com.android.build.gradle.LibraryExtension
import com.newrelic.agent.util.BuildId
import org.gradle.api.UnknownTaskException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.Logger
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.util.GradleVersion

abstract class VariantAdapter {
    final static Logger logger = NewRelicGradlePlugin.LOGGER

    final BuildHelper buildHelper
    final protected MapProperty<String, Object> variants

    protected VariantAdapter(BuildHelper buildHelper) {
        this.buildHelper = buildHelper
        this.variants = buildHelper.project.objects.mapProperty(String, Object)
    }

    Set<String> getVariantNames() {
        return variants.getOrElse(Set.of()).keySet()
    }

    Collection<Object> getVariantValues() {
        return variants.getOrElse(Set.of()).values()
    }

    abstract VariantAdapter configure(NewRelicExtension extension)

    abstract Provider<?> getBuildTypeProvider(String variantName)

    abstract TaskProvider getJavaCompileProvider(String variantName)

    abstract TaskProvider getConfigProvider(String variantName)

    abstract TaskProvider getMapUploadProvider(String variantName)

    abstract TaskProvider getTransformProvider(String variantName)

    abstract Provider<String> getMappingProvider(String variantName)

    abstract RegularFileProperty getMappingFileProvider(String variantName)

    def withVariant(String variantName) {
        variants.getOrNull().get(variantName, null)
    }

    boolean shouldInstrumentVariant(def variantName) {
        return buildHelper.extension.shouldIncludeVariant(variantName)
    }

    boolean shouldUploadVariantMap(def variantName) {
        def list = getVariantNames().collect { it }

        // do all the variants if variantMapsEnabled are disabled, or only those variants
        // provided in the extension. Default is the release variant or build type

        if (!buildHelper.extension.variantMapsEnabled.get()) {
            return true
        }

        return withVariant(variantName).tap { variant ->
            (buildHelper.extension.shouldIncludeMapUpload(variant.name) ||
                    buildHelper.extension.shouldIncludeMapUpload(variant.buildType.name))
        }
    }

    /**
     * Wire up the correct instrumentation tasks, based on Gradle environment
     * @return VariantAdapter
     */
    static VariantAdapter register(BuildHelper buildHelper) {
        if (GradleVersion.current() < GradleVersion.version("7.4")) {
            return new AGP4Adapter(buildHelper)
        } else {
            return new AGP7Adapter.AGP70Adapter(buildHelper)
        }
    }

    static class AGP4Adapter extends VariantAdapter {
        final def android
        final def transformer

        AGP4Adapter(BuildHelper buildHelper) {
            super(buildHelper)
            this.android = buildHelper.android
            this.transformer = new NewRelicTransform(buildHelper.project, buildHelper.extension)

            // Register the New Relic transformer
            logger.debug("TransformAPI: registering NewRelicTransform")
            android.registerTransform(transformer)
        }

        @Override
        VariantAdapter configure(NewRelicExtension extension) {
            // the plugin populates the extension only after the project is evaluated
            variants.empty()
            if (android instanceof AppExtension) {
                (android as AppExtension).applicationVariants.each { variant ->
                    def nameTolower = variant.name.toLowerCase()

                    if (shouldInstrumentVariant(variant.name)) {
                        variants.put(variant.name.toLowerCase(), variant)

                        if (buildHelper.isUsingLegacyTransform()) {
                            getTransformProvider(variant.name)?.configure { targetTask ->
                                if (targetTask.transform && targetTask.transform instanceof NewRelicTransform) {
                                    try {
                                        def excludeVariant = !shouldInstrumentVariant(variant.name)

                                        if (excludeVariant) {
                                            logger.info("Excluding instrumentation of variant [${variant.name}]")
                                        }
                                        targetTask.transform.withTransformState(variant.name, excludeVariant)

                                    } catch (Exception e) {
                                        e
                                    }
                                } else {
                                    logger.error("getTransformProvider: Could not set state on transform task [${targetTask.name}]")
                                }
                            }
                        }

                        // library projects do not inject configurations nor produce maps
                        if (buildHelper.checkLibrary()) {
                            return
                        }

                        // assemble and configure model
                        def objects = buildHelper.project.objects
                        def buildId = objects.property(String).value(BuildId.getBuildId(variant.name))
                        def minified = getBuildTypeProvider(variant.name)
                        def configProvider = getConfigProvider(variant.name)

                        configProvider.configure { configTask ->
                            logger.debug("${configTask.name} buildId[${buildId.get()}]")

                            def genSrcFolder = project.layout.buildDirectory.dir("generated/source/newrelicConfig/${variant.dirName}")

                            configTask.buildId = buildId
                            configTask.sourceOutputDir.convention(genSrcFolder)
                            configTask.mapProvider = objects.property(String).value(buildHelper.getMapCompilerName())
                            configTask.minifyEnabled = objects.property(Boolean).value(minified.getOrElse(true).minifyEnabled)
                            configTask.buildMetrics = objects.property(String).value(buildHelper.buildMetrics().toString())
                        }

                        if (minified.getOrElse(true).minifyEnabled && shouldUploadVariantMap(variant.name)) {
                            def mapUploadProvider = getMapUploadProvider(variant.name)

                            mapUploadProvider.configure { mapUploadTask ->
                                mapUploadTask.dependsOn configProvider

                                def mappingFileProperty = getMappingFileProvider(variant.name)

                                mapUploadTask.buildId = buildId
                                mapUploadTask.variantName = variant.name
                                mapUploadTask.mapProvider = getMappingProvider(variant.name)
                                mapUploadTask.mappingFile = mappingFileProperty
                                mapUploadTask.projectRoot = buildHelper.project.layout.projectDirectory

                                onlyIf {
                                    // Execute the task only if the given spec is satisfied. The spec will
                                    // be evaluated at task execution time, not during configuration.
                                    true    // mappingFileProperty.getAsFile().get().exists()
                                }

                                mapUploadTask.configure {
                                    dependsOn targetTask
                                }
                            }
                        }
                    }
                }
            } else if (android instanceof LibraryExtension) {
                (android as LibraryExtension).libraryVariants.each { variant ->
                    def nameTolower = variant.name.toLowerCase()
                    if (shouldInstrumentVariant(variant.name)) {
                        variants.put(nameTolower, variant)
                        if (extension.shouldIncludeMapUpload(nameTolower)) {
                            // TODO refactor from application variant
                        }
                    }
                }
            }

            return this
        }

        @Override
        Provider<Object> getBuildTypeProvider(String variantName) {
            return buildHelper.project.objects.property(Object).value(withVariant(variantName)?.buildType)
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

                try {
                    def configTaskProvider = buildHelper.project.tasks.register(taskName, NewRelicConfigTask)

                    try {
                        /**
                         * Update the variant model
                         * @see{https://android.googlesource.com/platform/tools/build/+/master/gradle/src/main/groovy/com/android/build/gradle/api/BaseVariant.java}*
                         */
                        def provider = variant.registerJavaGeneratingTask(configTaskProvider, genSrcFolder.get().asFile)
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
                            dependsOn configTaskProvider
                            source buildHelper.project.objects.sourceDirectorySet(configTaskProvider.getName(),
                                    configTaskProvider.getName()).srcDir(genSrcFolder)
                        }
                    } catch (UnknownTaskException ignored) {
                        // Kotlin source not present
                    }

                    buildConfigProvider.configure {
                        finalizedBy configTaskProvider
                    }

                    return configTaskProvider

                } catch (Exception e) {
                    // task for this variant not available
                    logger.warn("getConfigProvider: " + e.message)
                    return buildHelper.project.tasks.named(taskName)
                }
            } else {
                logger.error("buildConfig NOT finalized: buildConfig task was not found")
            }

            null
        }

        @Override
        TaskProvider getMapUploadProvider(String variantName) {
            return buildHelper.project.tasks.register("${NewRelicMapUploadTask.NAME}${variantName.capitalize()}", NewRelicMapUploadTask)
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
                logger.warn("getTransformProvider: $ignored")
            }
        }

        @Override
        Provider<String> getMappingProvider(String variantName) {
            return buildHelper.project.objects.property(String).convention(buildHelper.getMapCompilerName())
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

                    return buildHelper.project.objects.fileProperty().set(variantMappingFile)
                }

                try {
                    def provider = variant.getMappingFileProvider
                    if (provider.isPresent()) {
                        def fileCollection = provider.get()
                        if (!fileCollection.empty) {
                            return buildHelper.project.objects.fileProperty().set(fileCollection.singleFile)
                        }
                    }
                } catch (Exception e) {
                    logger.error("getMappingFileProvider: Map provider not found in variant [$variant.name]")
                }

                // If all else fails, default to default map locations
                return buildHelper.getDefaultMapPathProvider(variant.dirName)
            }
        }

    }

    static class AGP7Adapter extends VariantAdapter {
        final def androidComponents
        final def variantSelector

        AGP7Adapter(BuildHelper buildHelper) {
            super(buildHelper)
            this.androidComponents = buildHelper.androidComponents
            this.variantSelector = androidComponents.selector().all()

            androidComponents.onVariants(variantSelector, { variant ->
                def nameTolower = variant.name.toLowerCase()
                if (buildHelper.extension.shouldIncludeVariant(nameTolower)) {
                    variants.put(nameTolower, variant)
                    if (buildHelper.extension.shouldIncludeMapUpload(nameTolower)) {
                        // TODO
                    }
                }

                TaskProvider<ClassTransformWrapperTask> classesTaskProvider = buildHelper.project.tasks.register("newrelicTransformClasses${variant.name.capitalize()}", ClassTransformWrapperTask.class)
                variant.artifacts.use(classesTaskProvider)
                        .wiredWith({ it.getAllClasses() }, { it.getOutput() })
                        .toTransform(MultipleArtifact.ALL_CLASSES_DIRS.INSTANCE)

                TaskProvider<ClassTransformWrapperTask> jarsTaskProvider = buildHelper.project.tasks.register("newrelicTransformJars${variant.name.capitalize()}", ClassTransformWrapperTask.class)
                variant.artifacts.use(jarsTaskProvider)
                        .wiredWith({ it.getAllJars() }, { it.getOutput() })
                        .toTransform(MultipleArtifact.ALL_CLASSES_JARS.INSTANCE)
            })

            // Variants are locked and no longer mutable

            //  called with variant instances of type VariantT once the list of com.android.build.api.artifact.Artifact has been determined.
            configure(buildHelper.extension)

        }

        @Override
        VariantAdapter configure(NewRelicExtension extension) {
            return this
        }

        @Override
        Provider<Object> getBuildTypeProvider(String variantName) {
            withVariant(variantName).with {
                return buildHelper.project.objects.property(Object).convention(variant.buildType).orNull
            }
        }

        @Override
        TaskProvider getJavaCompileProvider(String variantName) {
            return buildHelper.project.tasks.register("javaCompile${variantName.capitalize()}", JavaCompile)
        }

        @Override
        TaskProvider getConfigProvider(String variantName) {
            return buildHelper.project.tasks.register("${NewRelicConfigTask.NAME}}${variantName.capitalize()}")
        }

        @Override
        TaskProvider getMapUploadProvider(String variantName) {
            return buildHelper.project.tasks.register("${NewRelicMapUploadTask.NAME}}${variantName.capitalize()}", NewRelicMapUploadTask)
        }

        @Override
        TaskProvider getTransformProvider(String variantName) {
            return null
        }

        @Override
        Provider<String> getMappingProvider(String variantName) {
            // TODO
            return null
        }

        @Override
        RegularFileProperty getMappingFileProvider(String variantName) {
            withVariant(variantName).with { variant ->
                return variant.artifacts.get(SingleArtifact.OBFUSCATION_MAPPING_FILE)
            }
        }

        static class AGP70Adapter extends AGP7Adapter {
            AGP70Adapter(BuildHelper buildHelper) {
                super(buildHelper)
            }
        }

        /*
        private setInstrumentation_7_0(def project, def variant) {
            try {
                // deprecated in 7.2
                variant.transformClassesWith(TraceClassVisitorFactory.class, InstrumentationScope.PROJECT) { params ->
                    params.getTruth().set(true)
                }
                variant.setAsmFramesComputationMode(FramesComputationMode.COMPUTE_FRAMES_FOR_ALL_CLASSES)
            } catch (Exception e) {
                e
            }

        }

        private configureVariantInstrumentation(def project, def variant) {
            try {
                // 7.1.0
                TaskProvider<ClassTransformWrapperTask> classesTaskProvider = project.tasks.register("newrelicTransformClasses${variant.name.capitalize()}", ClassTransformWrapperTask.class)
                variant.artifacts.use(classesTaskProvider)
                        .wiredWith({ it.getAllClasses() }, { it.getOutput() })
                        .toTransform(MultipleArtifact.ALL_CLASSES_DIRS.INSTANCE)

                TaskProvider<ClassTransformWrapperTask> jarsTaskProvider = project.tasks.register("newrelicTransformJars${variant.name.capitalize()}", ClassTransformWrapperTask.class)
                variant.artifacts.use(jarsTaskProvider)
                        .wiredWith({ it.getAllJars() }, { it.getOutput() })
                        .toTransform(MultipleArtifact.ALL_CLASSES_JARS.INSTANCE)

            } catch (Exception e) {
                e
            }
        }

        private setInstrumentation_7_4(def project, def variant) {
            TaskProvider<ClassTransformWrapperTask> taskProvider = tasks.register("${variant.name}Transform", ClassTransformWrapperTask.class)
            variant.artifacts.use(taskProvider)
                    .forScope(ScopedArtifacts.Scope.PROJECT)
                    .toTransform(ScopedArtifact.CLASSES.INSTANCE,
                            { it.getAllJars() },
                            { it.getAllDirectories() },
                            { it.getOutput() })
        }
        /**/

        static class AGP74Adapter extends AGP7Adapter {
            AGP74Adapter(BuildHelper buildHelper) {
                super(buildHelper)
            }
        }
    }

}
