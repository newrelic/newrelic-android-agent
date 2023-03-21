/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android

import com.android.build.gradle.AppExtension
import com.android.build.gradle.LibraryExtension
import com.newrelic.agent.android.obfuscation.Proguard
import org.gradle.api.logging.Logger
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.util.GradleVersion

abstract class VariantAdapter {
    final BuildHelper buildHelper
    final static Logger logger = NewRelicGradlePlugin.LOGGER
    final protected MapProperty<String, Object> variants;
    final protected NewRelicExtension plugin

    protected VariantAdapter(BuildHelper buildHelper) {
        this.buildHelper = buildHelper
        this.plugin = buildHelper.extensions.getByName(NewRelicGradlePlugin.PLUGIN_EXTENSION_NAME)
        this.variants = buildHelper.objects.mapProperty(String.class, Object.class)
    }

    ListProperty<String> getVariantNames() {
        ListProperty<String> variantNames = buildHelper.objects.listProperty(String.class)
        variants.get().keySet().each { name -> variantNames.add(name) }
        return variantNames
    }

    abstract VariantAdapter configure(NewRelicExtension extension)

    abstract Provider<?> getBuildType(String variantName)

    abstract TaskProvider getJavaCompileProvider(String variantName)

    abstract TaskProvider getBuildConfigProvider(String variantName)

    abstract TaskProvider getMapUploadProvider(String variantName)

    abstract Provider<String> getMappingProvider(String variantName)

    abstract Provider<File> getMappingFile(String variantName)

    def withVariant(String variantName) {
        variants.get().get(variantName)
    }

    /**
     * Wire up the correct instrumentation tasks, based on Gradle environment
     * @return VariantAdapter
     */
    static VariantAdapter register(BuildHelper buildHelper) {
        if (GradleVersion.current() < GradleVersion.version("7.4")) {
            return new AGP4xAdapter(buildHelper)
        } else {
            return new AGP7Adapter.AGP70Adapter(buildHelper)
        }
    }

    static class AGP4xAdapter extends VariantAdapter {
        final def android
        final def transformer

        AGP4xAdapter(BuildHelper buildHelper) {
            super(buildHelper)
            this.android = buildHelper.android
            this.transformer = new NewRelicTransform(buildHelper.project, plugin)

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
                    if (extension.shouldIncludeVariant(nameTolower)) {
                        variants.put(nameTolower, variant)
                        if (plugin.shouldIncludeMapUpload(nameTolower)) {
                            // TODO
                        }
                    }
                }
            } else if (android instanceof LibraryExtension) {
                (android as LibraryExtension).libraryVariants.each { variant ->
                    def nameTolower = variant.name.toLowerCase()
                    if (extension.shouldIncludeVariant(nameTolower)) {
                        variants.put(nameTolower, variant)
                        if (plugin.shouldIncludeMapUpload(nameTolower)) {
                            // TODO
                        }
                    }
                }
            }

            return this
        }

        @Override
        Provider<Object> getBuildType(String variantName) {
            if (variants.get().containsKey(variantName)) {
                def variant = variants.get().get(variantName)
                return buildHelper.objects.property(Object.class).convention(variant.buildType)
            }
            null
        }

        @Override
        TaskProvider getJavaCompileProvider(String variantName) {
            def variant = withVariant(variantName)
            try {
                def provider = variant.getJavaCompileProvider()
                if (provider) {
                    return provider
                }
            } catch (Exception e) {
                logger.error("getVariantCompileTask: $e")
            }

            return variant.javaCompiler
        }

        @Override
        TaskProvider getBuildConfigProvider(String variantName) {
            def variant = withVariant(variantName)
            try {
                def provider = variant.getGenerateBuildConfigProvider()
                if (provider) {
                    return provider
                }
            } catch (Exception e) {
                logger.error("getVariantBuildConfigTask: $e")
            }

            return variant.generateBuildConfig
        }

        @Override
        TaskProvider getMapUploadProvider(String variantName) {
            return buildHelper.project.tasks.register("mapUpload${variantName.capitalize()}") {
            }
        }

        @Override
        Provider<String> getMappingProvider(String variantName) {
            // TODO
            return buildHelper.objects.property(String).convention(Proguard.Provider.DEFAULT)
        }

        @Override
        Provider<File> getMappingFile(String variantName) {
            withVariant(variantName).with {
                // FIXME
                return buildHelper.objects.fileProperty()   // .set(new File("mapping.txt"))
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

            // Customize DSL Objects programmatically before the beforeVariants is called
            // Called before DSL objects are locked for component (variant) creation
            androidComponents.finalizeDsl({ dslExtension ->
                // TODO
                dslExtension.buildTypes.create("newrelic")
            })

            // DSL is now locked and no longer mutable

            androidComponents.beforeVariants(variantSelector, { builder ->
                // beforeVariants() allows modifications to the build flow and the artifacts that are produced
                // TODO
                builder.name
            })

            //  called with variant instances of type VariantT once the list of com.android.build.api.artifact.Artifact has been determined.
            configure(plugin)

            androidComponents.onVariants(variantSelector, { variant ->

                // onVariants() provides access to the newly-created Variant objects.
                // Set values or providers for the lazy-configured Property values each variant contains.
                // All artifacts to be created by AGP are already decided so can no longer be disabled

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
        }

        @Override
        VariantAdapter configure(NewRelicExtension extension) {
            def variantSelector = androidComponents.selector().all()

            // Prune lists based on extension settings
            variants.empty()

            //  called with variant instances of type VariantT once the list
            //  of com.android.build.api.artifact.Artifact has been determined.
            androidComponents.onVariants(variantSelector, { variant ->

            })

            return this
        }

        @Override
        Provider<Object> getBuildType(String variantName) {
            if (variants.get().containsKey(variantName)) {
                def variant = variants.get().get(variantName)
                return buildHelper.objects.property(Object).convention(variant.builtType)
            }
            null
        }

        @Override
        TaskProvider getJavaCompileProvider(String variantName) {
            return buildHelper.project.tasks.register("javaCompile${variantName.capitalize()}", JavaCompile) {
                // TODO
            }
        }

        @Override
        TaskProvider getBuildConfigProvider(String variantName) {
            return buildHelper.project.tasks.register("buildConfig${variantName.capitalize()}") {
                // TODO
            }
        }

        @Override
        TaskProvider getMapUploadProvider(String variantName) {
            return buildHelper.project.tasks.register("mapUpload${variantName.capitalize()}") {
                // TODO
            }
        }

        @Override
        Provider<String> getMappingProvider(String variantName) {
            // TODO
            return null
        }

        @Override
        Provider<File> getMappingFile(String variantName) {
            // TODO
            return null
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
