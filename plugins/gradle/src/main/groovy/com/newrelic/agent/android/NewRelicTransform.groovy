/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android

import com.android.build.api.transform.*
import com.android.build.api.variant.VariantInfo
import com.google.common.collect.ImmutableSet
import com.newrelic.agent.compile.ClassTransformer
import com.newrelic.agent.util.BuildId
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ProviderFactory

import java.util.jar.JarFile

class NewRelicTransform extends Transform {
    final static String NAME = "newrelicTransform"

    private final Set<QualifiedContent.ContentType> contentTypes
    private final Set<QualifiedContent.Scope> contentScopes
    private final Logger logger
    private final NewRelicExtension pluginExtension
    private final ObjectFactory objectFactory
    private final ProviderFactory providers

    private String variantName = BuildId.DEFAULT_VARIANT
    private boolean identityTransform = false

    NewRelicTransform(final Project project, final NewRelicExtension pluginExtension) {
        this.logger = NewRelicGradlePlugin.LOGGER
        this.pluginExtension = pluginExtension
        this.objectFactory = project.objects
        this.providers = project.providers
        this.contentTypes = ImmutableSet.of(
                QualifiedContent.DefaultContentType.CLASSES
        )
        this.contentScopes = ImmutableSet.of(
                QualifiedContent.Scope.PROJECT,
                QualifiedContent.Scope.EXTERNAL_LIBRARIES,
                QualifiedContent.Scope.SUB_PROJECTS,
        )

        logger.debug("project: " + project.name)

        try {
            def plugins = project.plugins
            if (plugins.hasPlugin('com.android.application') || plugins.hasPlugin('com.android.instantapp')) {
                logger.debug("apk or instantapp, transform external libraries")
            } else {
                logger.debug("feature or library")
            }
        } catch (Exception e) {
            logger.error("NewRelicTransform: " + e)
        }
    }

    @Override
    String getName() {
        return NAME
    }

    @Override
    Set<QualifiedContent.ContentType> getInputTypes() {
        return contentTypes
    }

    @Override
    Set<QualifiedContent.Scope> getScopes() {
        return contentScopes
    }

    @Override
    Set<QualifiedContent.Scope> getReferencedScopes() {
        return contentScopes
    }

    @Override
    boolean isIncremental() {
        return false
    }

    @Override
    void transform(TransformInvocation transformInvocation) throws TransformException, InterruptedException, IOException {
        this.transform(transformInvocation.context,
                transformInvocation.inputs,
                transformInvocation.referencedInputs,
                transformInvocation.outputProvider,
                transformInvocation.incremental)
    }

    void transform(Context context,
                   Collection<TransformInput> inputs,
                   Collection<TransformInput> referencedInputs,
                   TransformOutputProvider outputProvider,
                   boolean isIncremental) throws IOException, TransformException, InterruptedException {

        long tStart = System.currentTimeMillis()

        logger.info("[$NAME] Started")
        if (identityTransform) {
            logger.info("[$NAME] Will not rewrite classes")
        }

        logger.debug("[$NAME] Context.path[$context.path]")
        logger.debug("[$NAME] Context.temporaryDir[$context.temporaryDir]")
        logger.debug("[$NAME] outputProvider[$outputProvider]")
        logger.debug("[$NAME] isIncremental[$isIncremental]")

        inputs.each { input ->
            input.directoryInputs.each { dirInp ->
                logger.debug("[$NAME] directoryInput[${dirInp.name}]")
                def contentLocation = outputProvider.getContentLocation(
                        dirInp.name,
                        dirInp.getContentTypes(),
                        dirInp.getScopes(),
                        Format.DIRECTORY)
                logger.debug("[$NAME] directoryOutput[$contentLocation.absolutePath]")
            }
        }

        try {
            // start with a clean slate
            if (!isIncremental) {
                logger.debug("[$NAME] Delete transform output contents: ")
                outputProvider.deleteAll()
            }

            logger.debug("[$NAME] Calling class rewriter: ")

            ClassTransformer classTransformer

            inputs.each { input ->
                input.directoryInputs.each { dirInp ->
                    final File contentLocation = outputProvider.getContentLocation(
                            dirInp.name,
                            dirInp.getContentTypes(),
                            dirInp.getScopes(),
                            Format.DIRECTORY)

                    logger.debug("[$NAME] Transform directory[${dirInp.file.getAbsolutePath()}] Output[${contentLocation.getAbsolutePath()}]")

                    new ClassTransformer(dirInp.file, contentLocation)
                            .withWriteMode(ClassTransformer.WriteMode.always)
                            .asIdentityTransform(identityTransform)
                            .usingVariant(variantName)
                            .transformDirectory(dirInp.file)
                }

                input.jarInputs.each { jarInp ->
                    final File contentLocation = outputProvider.getContentLocation(
                            jarInp.name,
                            jarInp.getContentTypes(),
                            jarInp.getScopes(),
                            Format.JAR)

                    logger.debug("[$NAME] Transform JAR[${jarInp.file.getAbsolutePath()}] Output[${contentLocation.getAbsolutePath()}]")

                    try (JarFile jar = new JarFile(jarInp.file)) {
                        new ClassTransformer(jar, contentLocation)
                                .withWriteMode(ClassTransformer.WriteMode.always)
                                .asIdentityTransform(identityTransform)
                                .usingVariant(variantName)
                                .transformArchive(jarInp.file)
                    }
                }
            }

        } catch (final IOException exception) {
            logger.error("[$NAME] failed ", exception)
            throw exception
        } catch (final Exception exception) {
            logger.error("[$NAME] failed ", exception)
            throw new TransformException(exception)
        }

        logger.info("[$NAME] Finished in " + Double.valueOf((double) (
                System.currentTimeMillis() - tStart) / 1000f).toString() + " sec.")
    }

    @Override
    Set<QualifiedContent.ContentType> getOutputTypes() {
        return contentTypes
    }

    /**
     * Set any runtime config vars here
     * @param variantName
     * @param identityTransform True to leave class bytes unaltered
     */
    void withTransformState(String variantName, boolean identityTransform) {
        this.variantName = variantName
        this.identityTransform = identityTransform
    }

    @Override
    boolean applyToVariant(VariantInfo variant) {
        if (variant.isTest() && pluginExtension.shouldInstrumentTests()) {
            logger.info("[$NAME] Excluding instrumentation of test variant [${variant.fullVariantName}]")
            return false
        }

        boolean shouldExclude = pluginExtension.shouldExcludeVariant(variant.fullVariantName) ||
                pluginExtension.shouldExcludeVariant(variant.buildTypeName)

        if (shouldExclude) {
            logger.info("[$NAME] Excluding instrumentation of variant [${variant.fullVariantName}]")
        }

        return !shouldExclude
    }

    /*
    private void dumpTransform(TransformInvocation transformInvocation) {
        final Collection<TransformInput> inputs = transformInvocation.inputs;
        final Collection<TransformInput> referencedInputs = transformInvocation.referencedInputs
        final TransformOutputProvider outputProvider = transformInvocation.outputProvider;

        logger.debug("Inputs")
        inputs.each { input ->
            input.directoryInputs.each { dirInp ->
                logger.debug("    dirInput name: " + dirInp.name)
                logger.debug("    dirInput file: " + dirInp.file.absolutePath)

                final File output = outputProvider.getContentLocation(dirInp.name, dirInp.getContentTypes(), dirInp.getScopes(), Format.DIRECTORY)
                logger.debug("    dirInput output: " + output.absolutePath)
                logger.debug("[newrelic]")
            }

            input.jarInputs.each { jarInp ->
                logger.debug("    jarInput name: " + jarInp.name)
                logger.debug("    jarInput file: " + jarInp.file.absolutePath)
                logger.debug("    jarInput status: " + jarInp.status)

                File output = outputProvider.getContentLocation(jarInp.name, jarInp.getContentTypes(), jarInp.getScopes(), Format.JAR)
                logger.debug("    jarInput output: " + output.absolutePath)
                logger.debug("[newrelic]")
            }
        }

        logger.debug("referencedInputs")
        referencedInputs.each {
            refInput ->
                refInput.directoryInputs.each { dirInp ->
                    logger.debug("    dirInput name: " + dirInp.name)
                    logger.debug("    dirInput file: " + dirInp.file.absolutePath)
                    final File output = outputProvider.getContentLocation(dirInp.name,
                            dirInp.getContentTypes(),
                            dirInp.getScopes(),
                            Format.DIRECTORY)

                    logger.debug("    dirInput output: " + output.absolutePath)
                    logger.debug("[newrelic]")
                }

                refInput.jarInputs.each { jarInp ->
                    logger.debug("    jarInput name: " + jarInp.name)
                    logger.debug("    jarInput file: " + jarInp.file.absolutePath)
                    logger.debug("    jarInput status: " + jarInp.status)

                    final File output = outputProvider.getContentLocation(jarInp.name,
                            jarInp.getContentTypes(),
                            jarInp.getScopes(),
                            Format.JAR)

                    logger.debug("    jarInput output: " + output.absolutePath)
                    logger.debug("[newrelic]")
                }
        }
    }

    void dumpChangedFiles(DirectoryInput dirInp) {
        dirInp.changedFiles.each { File file, Status status ->
            switch (status) {
                case Status.NOTCHANGED:
                    break
                case Status.ADDED:
                case Status.CHANGED:
                    // logger.quiet("dir[" + dirInp.name + "] CHANGED or ADDED")
                    break
                case Status.REMOVED:
                    // logger.quiet("dir[" + dirInp.name + "] REMOVED")
                    break
            }
        }
    }

    void dumpChangedFiles(JarInput jarInp) {
        switch (jarInp.status) {
            case Status.NOTCHANGED:
                break
            case Status.ADDED:
            case Status.CHANGED:
                // logger.quiet("JAR[" + jarInp.name + "] CHANGED or ADDED")
                break
            case Status.REMOVED:
                // logger.quiet("JAR[" + jarInp.name + "] REMOVED")
                break
        }
    }
    /* DEBUG */
}

