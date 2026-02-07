/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.agp9

import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.variant.ScopedArtifacts
import com.newrelic.agent.android.BuildHelper
import com.newrelic.agent.android.ClassTransformWrapperTask
import org.gradle.api.tasks.TaskProvider
import org.gradle.util.GradleVersion

/**
 * AGP 9.1+ adapter that extends AGP9BaseAdapter to use super calls properly
 * This adapter is specifically for AGP 9.1+ and uses only the new ScopedArtifacts API
 */
class AGP90Adapter extends AGP9BaseAdapter {

    AGP90Adapter(BuildHelper buildHelper) {
        super(buildHelper)
    }

    @Override
    TaskProvider getTransformProvider(String variantName) {
        // AGP 9.0+: ONLY use ClassTransformWrapperTask, never reference Transform API
        return registerOrNamed("${ClassTransformWrapperTask.NAME}${variantName.capitalize()}", ClassTransformWrapperTask.class)
    }

    @Override
    def wiredWithTransformProvider(String variantName) {
        def transformProvider = getTransformProvider(variantName)

        // AGP 9.0+: Use ScopedArtifacts API for class transformation
        withVariant(variantName).artifacts
                .forScope(ScopedArtifacts.Scope.ALL)
                .use(transformProvider)
                .toTransform(
                        ScopedArtifact.CLASSES.INSTANCE,
                        { it.getClassJars() },
                        { it.getClassDirectories() },
                        { it.getOutputJar() }
                )

        return transformProvider
    }

    @Override
    def wiredWithConfigProvider(String variantName) {
        // Call parent implementation from VariantAdapter via AGP9BaseAdapter
        def configProvider = super.wiredWithConfigProvider(variantName)

        // AGP 9.0+ specific: Add generated source directory
        withVariant(variantName).with { variant ->
            try {
                variant.sources.java.addGeneratedSourceDirectory(configProvider, { it.getSourceOutputDir() })
            } catch (Exception ignored) {
                logger.debug("${GradleVersion.current()} does not provide addGeneratedSourceDirectory() on the Java sources instance.")
            }
        }

        buildHelper.project.afterEvaluate {
            def wiredTaskNames = Set.of(
                    "generate${variantName.capitalize()}BuildConfig",
                    "javaPreCompile${variantName.capitalize()}",
            )

            buildHelper.wireTaskProviderToDependencyNames(wiredTaskNames) { taskProvider ->
                taskProvider.configure { dependencyTask ->
                    dependencyTask.finalizedBy(configProvider)
                }
            }
        }

        return configProvider
    }

    @Override
    def wiredWithMapUploadProvider(String variantName) {
        // Call parent implementation from VariantAdapter via AGP9BaseAdapter
        def mapUploadProvider = super.wiredWithMapUploadProvider(variantName)

        // AGP 9.0+ specific: Wire map upload provider to minify tasks
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

        return mapUploadProvider
    }
}