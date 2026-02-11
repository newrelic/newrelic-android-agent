/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.agp9

import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.Variant
import com.android.build.api.variant.VariantSelector
import com.newrelic.agent.android.*
import org.gradle.api.Action
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.compile.JavaCompile

/**
 * Base adapter for AGP 9.x that extends VariantAdapter directly
 * This acts as a bridge to avoid Groovy MOP issues with super calls
 * All AGP 9.x adapters should extend this class instead of VariantAdapter
 */
abstract class AGP9BaseAdapter extends VariantAdapter {
    final AndroidComponentsExtension androidComponents
    final VariantSelector variantSelector

    AGP9BaseAdapter(BuildHelper buildHelper) {
        super(buildHelper)

        this.androidComponents = buildHelper.androidComponentsExtension as AndroidComponentsExtension
        this.variantSelector = androidComponents.selector().all()

        androidComponents.beforeVariants(variantSelector, { variantBuilder ->
            metrics.put(variantBuilder.buildType, [:] as HashMap)
            metrics.getting(variantBuilder.buildType).get().with {
                put("minSdk", variantBuilder.minSdk)
                put("targetSdk", variantBuilder.targetSdk)
            }
        })

        androidComponents.onVariants(variantSelector, { variant ->
            if (shouldInstrumentVariant(variant.name, variant.buildType)) {
                variants.put(variant.name.toLowerCase(), variant as Variant)
                buildTypes.put(variant.name, getBuildTypeProvider(variant.name))
                assembleDataModel(variant.name)
            }
        })
    }

    @Override
    VariantAdapter configure(NewRelicExtension extension) {
        getVariantValues().each { variant ->
            if (shouldInstrumentVariant(variant.name)) {
                def transformProvider = getTransformProvider(variant.name)
                def configProvider = getConfigProvider(variant.name)
            } else {
                logger.info("Excluding instrumentation of variant[${variant.name}]")
            }

            if (shouldUploadVariantMap(variant.name)) {
                def mapProvider = getMapUploadProvider(variant.name)
            }
        }

        return this
    }

    @Override
    Provider<BuildTypeAdapter> getBuildTypeProvider(String variantName) {
        def variant = withVariant(variantName)

        // AGP 9.0+: Accessing variant.properties triggers warnings for disabled build features
        // Access minifyEnabled through BuildType configuration instead
        def isMinifyEnabled = false
        try {
            def buildTypeConfig = buildHelper.androidExtension.buildTypes.getByName(variant.buildType)
            isMinifyEnabled = buildTypeConfig.minifyEnabled
        } catch (Exception e) {
            logger.warn("Could not determine minify state for variant ${variantName}: ${e.message}")
        }

        if (buildHelper.dexguardHelper && buildHelper.dexguardHelper.enabled) {
            isMinifyEnabled |= buildHelper.dexguardHelper.variantConfigurations.get().containsKey(variantName)
        }

        def buildTypeAdapter = new BuildTypeAdapter(variant.name, isMinifyEnabled, variant.flavorName, variant.buildType)
        return objectFactory.property(BuildTypeAdapter).value(buildTypeAdapter)
    }

    @Override
    TaskProvider getJavaCompileProvider(String variantName) {
        return registerOrNamed("newRelicJavaCompile${variantName.capitalize()}", JavaCompile.class)
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
        def variantConfiguration = buildHelper.extension.variantConfigurations.findByName(variantName)

        if (variantConfiguration && variantConfiguration.mappingFile) {
            def variantMappingFilePath = variantConfiguration.mappingFile.getAbsolutePath()
                    .replace("<name>", variant.name)
                    .replace("<dirName>", variant.name)

            return objectFactory.fileProperty().fileValue(buildHelper.project.file(variantMappingFilePath))
        }

        // dexguard maps are handled separately
        if (buildHelper.dexguardHelper?.getEnabled()) {
            return buildHelper.dexguardHelper.getMappingFileProvider(variantName)
        }

        return variant.artifacts.get(SingleArtifact.OBFUSCATION_MAPPING_FILE.INSTANCE)
    }
}