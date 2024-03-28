/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.agp4

import com.android.build.gradle.AppExtension
import com.android.build.gradle.LibraryExtension
import com.newrelic.agent.android.*
import org.gradle.api.Action
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider

class AGP4Adapter extends VariantAdapter {
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
                    buildTypes.put(variant.name, getBuildTypeProvider(variant.name).get())
                    assembleDataModel(variant.name)
                } else {
                    logger.info("Excluding instrumentation of variant[${variant.name}]")
                }
            }
        } else if (android instanceof LibraryExtension) {
            (android as LibraryExtension).libraryVariants.each { variant ->
                if (shouldInstrumentVariant(variant.name, variant.buildType.name)) {
                    variants.put(variant.name.toLowerCase(), variant)
                    buildTypes.put(variant.name, getBuildTypeProvider(variant.name).get())
                    assembleDataModel(variant.name)
                } else {
                    logger.info("Excluding library instrumentation of variant[${variant.name}]")
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
        def buildType = new BuildTypeAdapter(variant.name, variant.buildType.minifyEnabled, variant.buildType.name)
        return objectFactory.property(BuildTypeAdapter).value(buildType)
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

        try {
            def buildConfigProvider = variant.getGenerateBuildConfigProvider()
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
                } catch (Exception ignored) {
                    // Kotlin source not present or task has started
                }

                return configTaskProvider

            } else {
                logger.error("getConfigProvider: buildConfig NOT finalized: buildConfig task was not found")
            }

        } catch (Exception e) {
            logger.error("getConfigProvider: $e")
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
            return buildHelper.dexguardHelper.getMappingFileProvider(variantName)
        }

        def variantConfiguration = buildHelper.extension.variantConfigurations.findByName(variant.name)
        if (variantConfiguration && variantConfiguration?.mappingFile) {
            def variantMappingFilePath = variantConfiguration.mappingFile.getAbsolutePath()
                    .replace("<name>", variant.name)
                    .replace("<dirName>", variant.dirName)

            return objectFactory.fileProperty().fileValue(buildHelper.project.file(variantMappingFilePath))
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
        return objectFactory.fileProperty().value(f)
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
                ignored
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
                    if (!mapTask.mappingFile.present) {
                        mapTask.mappingFile.fileValue(mapFileProvider.map { it.singleFile }.get())
                    }
                }
            }
        }

        def dependencyTaskNames = NewRelicMapUploadTask.wiredTaskNames(variantName.capitalize())
        buildHelper.wireTaskProviderToDependencyNames(dependencyTaskNames) { dependencyTaskProvider ->
            dependencyTaskProvider.configure { dependencyTask ->
                dependencyTask.finalizedBy(mapUploadProvider)
            }
        }
    }
}
