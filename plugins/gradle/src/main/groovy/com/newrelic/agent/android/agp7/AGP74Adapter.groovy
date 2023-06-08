/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.agp7


import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.variant.ScopedArtifacts
import com.newrelic.agent.android.BuildHelper
import org.gradle.api.provider.Provider
import org.gradle.util.GradleVersion

class AGP74Adapter extends AGP70Adapter {
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
        def isMinifyEnabled = variant.properties.getOrDefault('minifyEnabled',
                variant.properties.get("minifiedEnabled"))  // really?
        if (buildHelper.dexguardHelper && buildHelper.dexguardHelper.enabled) {
            isMinifyEnabled |= buildHelper.dexguardHelper.variantConfigurations.get().containsKey(variantName)
        }
        def buildTypeAdapter = new BuildTypeAdapter(variant.name, isMinifyEnabled, variant.flavorName, variant.buildType)

        return buildHelper.project.objects.property(Object).value(buildTypeAdapter)
    }

    @Override
    def wiredWithTransformProvider(String variantName) {
        if (GradleVersion.current() < GradleVersion.version("7.5")) {
            return super.wiredWithTransformProvider(variantName)
        }

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
