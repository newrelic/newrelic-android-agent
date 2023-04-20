/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android


import com.android.build.api.artifact.MultipleArtifact
import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.ScopedArtifacts
import com.android.build.api.variant.Variant
import com.android.build.api.variant.VariantSelector
import com.android.build.gradle.AppExtension
import com.android.build.gradle.LibraryExtension

import com.newrelic.agent.util.BuildId

import org.gradle.api.Action
import org.gradle.api.InvalidUserDataException
import org.gradle.api.UnknownTaskException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.Logger
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.TaskProvider
import org.gradle.util.GradleVersion

abstract class VariantAdapter {
    final static Logger logger = NewRelicGradlePlugin.LOGGER

    final BuildHelper buildHelper
    final protected ObjectFactory objectFactory
    final protected MapProperty<String, Object> variants
    final protected MapProperty<String, Provider<BuildTypeAdapter>> buildTypes
    final protected MapProperty<String, Object> metrics

    protected VariantAdapter(BuildHelper buildHelper) {
        this.buildHelper = buildHelper
        this.objectFactory = buildHelper.project.objects
        this.variants = objectFactory.mapProperty(String, Object)
        this.buildTypes = objectFactory.mapProperty(String, Object)
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
            return new AGP70Adapter(buildHelper)
        } else {
            return new AGP4Adapter(buildHelper)
        }
    }

    Set<String> getVariantNames() {
        return variants.getOrElse(Set.of()).keySet()
    }

    Collection<Object> getVariantValues() {
        return variants.getOrElse(Set.of()).values()
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
            buildTypes.put(variantName, getBuildTypeProvider(variantName))
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

        return (buildType.minified && (buildHelper.extension.shouldIncludeMapUpload(variant.name) ||
                buildHelper.extension.shouldIncludeMapUpload(buildType.name)))
    }

    def wiredWithTransformProvider(String variantName) {
        return getTransformProvider(variantName)
    }

    def wiredWithConfigProvider(String variantName) {
        def configProvider = getConfigProvider(variantName) { configTask ->
            def buildType = withBuildType(variantName)
            def genSrcFolder = buildHelper.project.layout.buildDirectory.dir("generated/source/newrelicConfig/${variantName}")

            configTask.sourceOutputDir.set(objectFactory.directoryProperty().value(genSrcFolder))
            configTask.buildId.set(objectFactory.property(String).value(BuildId.getBuildId(variantName)))
            configTask.mapProvider.set(objectFactory.property(String).value(buildHelper.getMapCompilerName()))
            configTask.minifyEnabled.set(objectFactory.property(Boolean).value(buildType.minified))
            configTask.buildMetrics.set(objectFactory.property(String).value(buildHelper.getBuildMetrics().toString()))
        }

        return configProvider
    }

    def wiredWithMapUploadProvider(String variantName) {
        def mapUploadProvider = getMapUploadProvider(variantName) { mapUploadTask ->
            mapUploadTask.buildId.set(objectFactory.property(String).value(BuildId.getBuildId(variantName)))
            mapUploadTask.variantName.set(objectFactory.property(String).value(variantName))
            mapUploadTask.mapProvider.set(objectFactory.property(String).value(buildHelper.getMapCompilerName()))
            mapUploadTask.mappingFile.set(getMappingFileProvider(variantName))
            mapUploadTask.projectRoot.set(buildHelper.project.layout.projectDirectory)

            mapUploadTask.onlyIf {
                // Execute the task only if the given spec is satisfied. The spec will
                // be evaluated at task execution time, not during configuration.
                // !mapUploadTask.taggedMappingFile.asFile.get().exists()
            }

            mapUploadTask.outputs.upToDateWhen {
                mapUploadTask.getTaggedMappingFile().asFile.get().with {
                    exists() && text.contains(Proguard.NR_MAP_PREFIX)
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
                action?.execute(it)
            }
        }
    }

    /**
     * Create and provide lazy-configuration for our data model
     */
    def assembleDataModel(String variantName) {
        // assemble and configure model
        wiredWithTransformProvider(variantName)
        wiredWithConfigProvider(variantName)
        if (shouldUploadVariantMap(variantName)) {
            // register map upload task(s)
            wiredWithMapUploadProvider(variantName)
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
                    if (shouldInstrumentVariant(variant.name, variant.buildType.name)) {
                        variants.put(variant.name.toLowerCase(), variant)
                        buildTypes.put(variant.name, getBuildTypeProvider(variant.name))
                        assembleDataModel(variant.name)
                    }
                }
            } else if (android instanceof LibraryExtension) {
                (android as LibraryExtension).libraryVariants.each { variant ->
                    if (shouldInstrumentVariant(variant.name, variant.buildType.name)) {
                        variants.put(variant.name.toLowerCase(), variant)
                        buildTypes.put(variant.name, getBuildTypeProvider(variant.name))
                        assembleDataModel(variant.name)
                    }
                }
            }

            return this
        }

        @Override
        TaskProvider getTransformProvider(String variantName) {
            try {
                return buildHelper.project.tasks.named("transformClassesWith${NewRelicTransform.NAME.capitalize()}For${variantName.capitalize()}")
            } catch (Exception ignored) {
            }

            return null
        }

        @Override
        Provider<Object> getBuildTypeProvider(String variantName) {
            def variant = withVariant(variantName)
            def buildType = new BuildTypeAdapter(variant.buildType.name, variant.buildType.minifyEnabled)
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
        TaskProvider getConfigProvider(String variantName, Action action = null) {
            def variant = withVariant(variantName)
            def configTaskProvider = registerOrNamed("${NewRelicConfigTask.NAME}${variantName.capitalize()}", NewRelicConfigTask.class, action)
            def buildConfigProvider

            try {
                buildConfigProvider = variant.getGenerateBuildConfigProvider()

            } catch (Exception e) {
                logger.error("getConfigProvider: $e")
                buildConfigProvider = variant.generateBuildConfig
            }

            if (buildConfigProvider?.isPresent()) {
                def genSrcFolder = buildHelper.project.layout.buildDirectory.dir("generated/source/newrelicConfig/${variant.dirName}")

                try {
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
                    buildHelper.project.tasks.named("compile${variantName.capitalize()}Kotlin") { kotlinCompileTask ->
                        kotlinCompileTask.dependsOn(configTaskProvider)
                        kotlinCompileTask.source(objectFactory.sourceDirectorySet(configTaskProvider.name, configTaskProvider.name)
                                .srcDir(genSrcFolder))
                    }
                } catch (UnknownTaskException ignored) {
                    // Kotlin source not present
                }

                return configTaskProvider

            } else {
                logger.error("getConfigProvider: buildConfig NOT finalized: buildConfig task was not found")
            }

            return null
        }

        @Override
        TaskProvider getMapUploadProvider(String variantName, Action action = null) {
            return registerOrNamed("${NewRelicMapUploadTask.NAME}${variantName.capitalize()}", NewRelicMapUploadTask.class, action)
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
        RegularFileProperty getMappingFileProvider(String variantName, Action action = null) {
            def variant = withVariant(variantName)

            // FIXME DRY up
            // dexguard maps are handled separately
            if (buildHelper.checkDexGuard()) {
                return buildHelper.dexguardHelper.getDefaultMapPathProvider(variant.dirName)
            }

            def variantConfiguration = buildHelper.extension.variantConfigurations.findByName(variant.name)
            if (variantConfiguration && variantConfiguration?.mappingFile) {
                def variantMappingFilePath = variantConfiguration.mappingFile.getAbsolutePath()
                        .replace("<name>", variant.name)
                        .replace("<dirName>", variant.dirName)

                return objectFactory.fileProperty().value(variantMappingFilePath)
            }

            try {
                def provider = variant.getMappingFileProvider()
                if (provider.isPresent()) {
                    return objectFactory.fileProperty().fileValue(provider.get().singleFile)
                }
            } catch (Exception ignore) {
                logger.error("getMappingFileProvider: Map provider not found in variant [$variant.name]")
            }

            // If all else fails, default to default map locations
            def f = buildHelper.project.layout.buildDirectory.file("outputs/mapping/${variant.dirName}/mapping.txt")
            return objectFactory.fileProperty().fileValue(f)
        }

        @Override
        def wiredWithConfigProvider(String variantName) {
            def configProvider = super.wiredWithConfigProvider(variantName)

            withVariant(variantName).with { variant ->
                try {
                    variant.getGenerateBuildConfigProvider().configure {
                        it.finalizedBy(configProvider)
                    }
                } catch (Exception ignored) {
                    configTaskProvider = variant.generateBuildConfig.configure {
                        it.finalizedBy(configProvider)
                    }
                }
            }

            def dependencyTasks = ["generate${variantName.capitalize()}BuildConfig"] as Set
            buildHelper.wireTaskProviderToDependencyNames(dependencyTasks) { dependencyTaskProvider ->
                dependencyTaskProvider.configure { dependencyTask ->
                    dependencyTask.finalizedBy(configProvider)
                }
            }
        }

        @Override
        def wiredWithMapUploadProvider(String variantName) {
            def mapUploadProvider = super.wiredWithMapUploadProvider(variantName)

            withVariant(variantName).with { variant ->
                def mapFileProvider = variant.getMappingFileProvider()
                if (mapFileProvider?.isPresent()) {
                    mapUploadProvider.configure { mapTask ->
                        mapTask.mappingFile.fileValue(mapFileProvider.map { it.singleFile }.get())
                    }
                }
            }

            def dependencyTaskNames = NewRelicMapUploadTask.getDependentTaskNames(variantName.capitalize())
            buildHelper.wireTaskProviderToDependencyNames(dependencyTaskNames) { dependencyTaskProvider ->
                dependencyTaskProvider.configure { dependencyTask ->
                    // dependencyTask.finalizedBy(mapUploadProvider)
                }
            }

            def dependencyTasks = ["minify${variantName.capitalize()}WithR8"] as Set
            buildHelper.wireTaskProviderToDependencyNames(dependencyTasks) { dependencyTaskProvider ->
                dependencyTaskProvider.configure { dependencyTask ->
                    dependencyTask.finalizedBy(mapUploadProvider)
                }
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

            androidComponents.onVariants(variantSelector, { variant ->
                if (shouldInstrumentVariant(variant.name, variant.buildType)) {
                    variants.put(variant.name.toLowerCase(), variant as Variant)
                    buildTypes.put(variant.name, getBuildTypeProvider(variant.name).get())
                    assembleDataModel(variant.name)
                }
            })
        }

        @Override
        VariantAdapter configure(NewRelicExtension extension) {
            getVariantValues().each { variant ->
                def transformProvider = getTransformProvider(variant.name)
                def configProvider = getConfigProvider(variant.name)

                if (shouldUploadVariantMap(variant.name)) {
                    def mapProvider = getMapUploadProvider(variant.name)
                    def mapUploadTasks = NewRelicMapUploadTask.getDependentTaskNames(variant.name.capitalize())
                    buildHelper.wireTaskProviderToDependencyNames(mapUploadTasks) { dependencyTaskProvider ->
                        mapProvider.configure {
                            it.dependsOn(dependencyTaskProvider)
                        }
                    }
                }
            }

            return this
        }

        @Override
        TaskProvider getTransformProvider(String variantName) {
            try {
                return buildHelper.project.tasks.named("transformClassesWith${NewRelicTransform.NAME.capitalize()}For${variantName.capitalize()}", NewRelicTransform.class)
            } catch (Exception ignored) {
            }

            return registerOrNamed("${ClassTransformWrapperTask.NAME}${variantName.capitalize()}", ClassTransformWrapperTask.class)
        }

        @Override
        Provider<BuildTypeAdapter> getBuildTypeProvider(String variantName) {
            def variant = withVariant(variantName)
            def buildType = new BuildTypeAdapter(variant.buildType, variant.minifiedEnabled, variant.flavorName)
            return objectFactory.property(Object).value(buildType)
        }

        @Override
        TaskProvider getJavaCompileProvider(String variantName) {
            def variant = withVariant(variantName)
            return objectFactory.property(Object) // FIXME
        }

        @Override
        TaskProvider getConfigProvider(String variantName, Action action = null) {
            return registerOrNamed("${NewRelicConfigTask.NAME}${variantName.capitalize()}", NewRelicConfigTask.class, action)
        }

        @Override
        TaskProvider getMapUploadProvider(String variantName, Action action = null) {
            return registerOrNamed("${NewRelicMapUploadTask.NAME}${variantName.capitalize()}", NewRelicMapUploadTask.class, action)
        }

        @Override
        RegularFileProperty getMappingFileProvider(String variantName, Action action = null) {
            def variant = withVariant(variantName)

            // FIXME DRY up
            // dexguard maps are handled separately
            if (buildHelper.checkDexGuard() && buildHelper.dexguardHelper.getEnabled()) {
                return buildHelper.dexguardHelper.getDefaultMapPathProvider(variant.dirName)
            }

            def variantConfiguration = buildHelper.extension.variantConfigurations.findByName(variantName)
            if (variantConfiguration && variantConfiguration.mappingFile) {
                def variantMappingFilePath = variantConfiguration.mappingFile.getAbsolutePath()
                        .replace("<name>", variant.name)
                        .replace("<dirName>", variant.dirName)

                return objectFactory.fileProperty().fileValue(buildHelper.project.file(variantMappingFilePath))
            }

            return variant.artifacts.get(SingleArtifact.OBFUSCATION_MAPPING_FILE.INSTANCE)
        }

        @Override
        def wiredWithTransformProvider(String variantName) {
            def transformProvider = getTransformProvider(variantName)
            def variant = withVariant(variantName)
            def mappedJarFile = transformProvider.flatMap({ it.getOutputDirectory().file("classes.jar") })

            def unpack = registerOrNamed("newRelicTransformUnpack${variantName.capitalize()}", Copy.class) { unpackTask ->
                unpackTask.from(buildHelper.project.zipTree(mappedJarFile))
                unpackTask.into(transformProvider.map({ it.getOutputDirectory().asFile.get() }))
                unpackTask.doLast {
                    mappedJarFile.get().get().asFile.delete()
                }
                unpackTask.dependsOn(transformProvider)
            }

            transformProvider.configure { transformTask ->
                // Transformation from Gradle 7.2 - 7.3 use a different artifact
                // model than 7.4. Provide the JAR elements directly and set the output JAR name
                transformTask.classJars.set(variant.artifacts.getAll(MultipleArtifact.ALL_CLASSES_JARS.INSTANCE))
                transformTask.outputJar.set(mappedJarFile)
            }

            variant.artifacts.use(transformProvider)
                    .wiredWith({ it.getClassDirectories() }, { it.getOutputDirectory() })
                    .toTransform(MultipleArtifact.ALL_CLASSES_DIRS.INSTANCE)

            return transformProvider
        }

        @Override
        def wiredWithConfigProvider(String variantName) {
            def configProvider = super.wiredWithConfigProvider(variantName)

            withVariant(variantName).with { variant ->
                try {
                    variant.sources.java.addGeneratedSourceDirectory(configProvider, { it.getSourceOutputDir() })
                } catch (Exception ignored) {
                    //  FIXME
                    logger.debug("${GradleVersion.current()} does not privide addGeneratedSourceDirectory() on the Java sources instance.")
                }
            }

            buildHelper.project.afterEvaluate {
                def configTasks = NewRelicConfigTask.getDependentTaskNames(variantName.capitalize())
                buildHelper.wireTaskProviderToDependencyNames(configTasks) { taskProvider ->
                    taskProvider.configure { dependencyTask ->
                        dependencyTask.finalizedBy(configProvider)
                    }
                }
            }

            return configProvider
        }

        @Override
        def wiredWithMapUploadProvider(String variantName) {
            def mapUploadProvider = super.wiredWithMapUploadProvider(variantName)

            mapUploadProvider.configure { mapTask ->
                // mapTask.mappingFile.set(getMappingFileProvider(variantName).flatMap { it })
            }

            withVariant(variantName).artifacts.use(mapUploadProvider)
                    .wiredWithFiles({ it.getMappingFile() }, { it.getTaggedMappingFile() })
                    .toTransform(SingleArtifact.OBFUSCATION_MAPPING_FILE.INSTANCE)

            return mapUploadProvider
        }

    }

    static class AGP74Adapter extends AGP70Adapter {
        AGP74Adapter(BuildHelper buildHelper) {
            super(buildHelper)
            androidComponents.beforeVariants(variantSelector, { variantBuilder ->
                metrics.put(variantBuilder.buildType, [:] as HashMap)
                metrics[variantBuilder.buildType].get().with {
                    put("minSdk", variantBuilder.minSdk)
                    put("maxSdk", variantBuilder.maxSdk)
                    put("targetSdk", variantBuilder.targetSdk)
                }
            })
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
            def isMinifyEnabled = variant.properties.getOrDefault('minifyEnabled', variant.properties.get("minifiedEnabled")) // really?
            def buildTypeAdapter = new BuildTypeAdapter(variant.buildType, isMinifyEnabled, variant.flavorName)

            return buildHelper.project.objects.property(Object).value(buildTypeAdapter)
        }

        @Override
        def wiredWithTransformProvider(String variantName) {
            def transformProvider = getTransformProvider(variantName)
            withVariant(variantName).artifacts
                    .forScope(ScopedArtifacts.Scope.ALL)
                    .use(transformProvider)
                    .toTransform(ScopedArtifact.CLASSES.INSTANCE, { it.getClassJars() }, { it.getClassDirectories() }, { it.getOutputJar() })

            return transformProvider
        }

        @Override
        def wiredWithConfigProvider(String variantName) {
            def configProvider = super.wiredWithConfigProvider(variantName)
            withVariant(variantName).sources.java.addGeneratedSourceDirectory(configProvider, { it.getSourceOutputDir() })

            return configProvider
        }
    }

    static class BuildTypeAdapter {
        final String name
        final Boolean minified
        final String flavor

        BuildTypeAdapter(String name, Boolean minified = false, String flavor = "") {
            this.name = name
            this.minified = minified
            this.flavor = flavor
        }
    }

}