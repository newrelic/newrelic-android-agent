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

class AGP90Adapter extends AGP70Adapter {
    AGP90Adapter(BuildHelper buildHelper) {
        super(buildHelper)
    }


    @Override
    Provider<BuildTypeAdapter> getBuildTypeProvider(String variantName) {
        def variant = withVariant(variantName)
        def isMinifyEnabled = variant.properties.getOrDefault('minifyEnabled',
                variant.properties.get("minifiedEnabled"))  // really?
        if (buildHelper.dexguardHelper && buildHelper.dexguardHelper.enabled) {
            isMinifyEnabled |= buildHelper.dexguardHelper.variantConfigurations.get().containsKey(variantName)
        }
        def buildTypeAdapter = new BuildTypeAdapter(variant.name, isMinifyEnabled, variant.flavorName, variant.buildType)

        return objectFactory.property(BuildTypeAdapter).value(buildTypeAdapter)
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
    @Override
    def wiredWithMapUploadProvider(String variantName) {
        def mapUploadProvider = super.wiredWithMapUploadProvider(variantName)
        def vnc = variantName.capitalize()

        buildHelper.project.afterEvaluate {
            def wiredTaskNames

            if (buildHelper.dexguardHelper?.enabled) {
                wiredTaskNames = buildHelper.dexguardHelper.wiredTaskNames(vnc)
            } else {
                wiredTaskNames = Set.of(
                        "minify${vnc}WithR8",
                        "minify${vnc}WithProguard",
                        "transformClassesAndResourcesWithProguardTransformFor${vnc}",
                        "transformClassesAndResourcesWithProguardFor${vnc}",
                        "transformClassesAndResourcesWithR8For${vnc}",
                )
            }

            buildHelper.wireTaskProviderToDependencyNames(wiredTaskNames) { dependencyTask ->
                dependencyTask.configure {
                    finalizedBy(mapUploadProvider)
                }
                mapUploadProvider.configure {
                    shouldRunAfter(dependencyTask)
                }
            }
        }
        return mapUploadProvider;
    }
}
