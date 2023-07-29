/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.agp7


import com.android.build.api.artifact.MultipleArtifact
import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.Variant
import com.android.build.api.variant.VariantSelector
import com.newrelic.agent.android.*
import org.gradle.api.Action
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.TaskProvider
import org.gradle.util.GradleVersion

class AGP70Adapter extends VariantAdapter {
    final AndroidComponentsExtension androidComponents
    final VariantSelector variantSelector

    AGP70Adapter(BuildHelper buildHelper) {
        super(buildHelper)

        this.androidComponents = buildHelper.androidComponentsExtension as AndroidComponentsExtension
        this.variantSelector = androidComponents.selector().all()

        androidComponents.beforeVariants(variantSelector, { variantBuilder ->
            metrics.put(variantBuilder.buildType, [:] as HashMap)
            metrics[variantBuilder.buildType].get().with {
                put("minSdk", variantBuilder.minSdk)
                put("targetSdk", variantBuilder.targetSdk)
            }
        })

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
            if (shouldInstrumentVariant(variant.name)) {
                def transformProvider = getTransformProvider(variant.name)
                def configProvider = getConfigProvider(variant.name)
            }

            if (shouldUploadVariantMap(variant.name)) {
                def mapProvider = getMapUploadProvider(variant.name)
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
        def buildType = new BuildTypeAdapter(variant.name, variant.minifiedEnabled, variant.flavorName, variant.buildType)
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
        def variantConfiguration = buildHelper.extension.variantConfigurations.findByName(variantName)

        if (variantConfiguration && variantConfiguration.mappingFile) {
            def variantMappingFilePath = variantConfiguration.mappingFile.getAbsolutePath()
                    .replace("<name>", variant.name)
                    .replace("<dirName>", variant.dirName)

            return objectFactory.fileProperty().fileValue(buildHelper.project.file(variantMappingFilePath))
        }

        // dexguard maps are handled separately
        if (buildHelper.dexguardHelper?.getEnabled()) {
            return buildHelper.dexguardHelper.getMappingFileProvider(variantName)
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

        return mapUploadProvider
    }

}

