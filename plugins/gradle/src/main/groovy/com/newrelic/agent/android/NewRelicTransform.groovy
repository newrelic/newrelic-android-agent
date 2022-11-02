/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android

import com.android.build.api.transform.*
import com.android.build.api.variant.VariantInfo
import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import com.google.common.collect.Maps
import com.newrelic.agent.compile.ClassTransformer
import com.newrelic.agent.compile.RewriterAgent
import com.newrelic.agent.util.BuildId
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.logging.Logger

import java.util.jar.JarFile

class NewRelicTransform extends Transform {

    final static String TRANSFORMER_NAME = "newrelicTransform"

    private final static Logger logger = NewRelicGradlePlugin.getLogger()

    private final Set<QualifiedContent.ContentType> contentTypes = ImmutableSet.of(
            QualifiedContent.DefaultContentType.CLASSES
    )

    private final Set<QualifiedContent.Scope> contentScopes
    private final Project project
    private final NewRelicExtension pluginExtension

    private String variantName = BuildId.DEFAULT_VARIANT
    private boolean identityTransform = false

    NewRelicTransform(
            final Project project,
            final NewRelicExtension pluginExtension,
            final String agentArgs) {

        this.project = project;
        this.pluginExtension = pluginExtension;

        logger.debug("[newrelic.debug] project: " + project.name)
        logger.debug("[newrelic.debug] agentArgs: " + agentArgs.toString())

        contentScopes = new HashSet<QualifiedContent.Scope>()
        contentScopes.add(QualifiedContent.Scope.PROJECT)

        try {
            if (project.plugins.hasPlugin('com.android.application') ||
                    project.plugins.hasPlugin('com.android.instantapp')) {
                contentScopes.add(QualifiedContent.Scope.EXTERNAL_LIBRARIES)
                contentScopes.add(QualifiedContent.Scope.SUB_PROJECTS)
                logger.debug("[newrelic.debug] apk or instantapp, transform external libraries")
            } else {
                logger.debug("[newrelic.debug] feature or library")
            }
        } catch (Exception e) {
            logger.error("[newrelic.error] NewRelicTransform: " + e)
        }
    }

    @Override
    String getName() {
        return TRANSFORMER_NAME
    }

    @Override
    Set<QualifiedContent.ContentType> getInputTypes() {
        return Collections.singleton(QualifiedContent.DefaultContentType.CLASSES)
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
        // dumpTransform(transformInvocation);
        this.transform(transformInvocation.context,
                transformInvocation.inputs,
                transformInvocation.referencedInputs,
                transformInvocation.outputProvider,
                transformInvocation.incremental);
    }

    void transform(Context context,
                   Collection<TransformInput> inputs,
                   Collection<TransformInput> referencedInputs,
                   TransformOutputProvider outputProvider,
                   boolean isIncremental) throws IOException, TransformException, InterruptedException {

        long tStart = System.currentTimeMillis();

        logger.info("[newrelic.info] [" + TRANSFORMER_NAME + "] Started")
        if (identityTransform) {
            logger.info("[newrelic.info] [" + TRANSFORMER_NAME + "] Will not rewrite classes")
        }

        logger.debug("[newrelic.debug] Context.path: " + context.path)
        logger.debug("[newrelic.debug] Context.temporaryDir: " + context.temporaryDir)

        File directoryOutput = context.temporaryDir
        inputs.each { input ->
            if (!input.directoryInputs.empty) {
                def dirInp = input.directoryInputs.first()
                directoryOutput = outputProvider.getContentLocation(
                        dirInp.name,
                        dirInp.getContentTypes(),
                        dirInp.getScopes(),
                        Format.DIRECTORY)
                logger.debug("[newrelic.debug] transform.directoryOutput: " + directoryOutput.absolutePath)
            }
        }

        logger.debug("[newrelic.debug] transform.outputProvider: " + outputProvider)
        logger.debug("[newrelic.debug] transform.isIncremental: " + isIncremental)

        try {
            // start with a clean slate
            if (!isIncremental) {
                logger.debug("[newrelic.debug] Delete transform output contents: ")
                outputProvider.deleteAll()
            }

            logger.debug("[newrelic.debug] Calling class rewriter: ")

            ClassTransformer classTransformer

            inputs.each { input ->
                input.directoryInputs.each { dirInp ->
                    final File output = outputProvider.getContentLocation(
                            dirInp.name,
                            dirInp.getContentTypes(),
                            dirInp.getScopes(),
                            Format.DIRECTORY)

                    // dumpChangedFiles(dirInp)

                    logger.debug("[newrelic.debug] Transform directory[" + dirInp.file.getAbsolutePath() + "] Output[" + output.getAbsolutePath() + "]")

                    classTransformer = new ClassTransformer(dirInp.file, output)
                    classTransformer.withWriteMode(ClassTransformer.WriteMode.always)
                            .asIdentityTransform(identityTransform)
                            .usingVariant(variantName)
                            .transformDirectory(dirInp.file)
                }

                input.jarInputs.each { jarInp ->
                    final File output = outputProvider.getContentLocation(
                            jarInp.name,
                            jarInp.getContentTypes(),
                            jarInp.getScopes(),
                            Format.JAR)

                    logger.debug("[newrelic.debug] Transform JAR[" + jarInp.file.getAbsolutePath() + "] Output[" + output.getAbsolutePath() + "]")

                    JarFile jar = null
                    try {
                        jar = new JarFile(jarInp.file)
                        classTransformer = new ClassTransformer(jar, output)
                        classTransformer.withWriteMode(ClassTransformer.WriteMode.always)
                                .asIdentityTransform(identityTransform)
                                .usingVariant(variantName)
                                .transformArchive(jarInp.file)
                    } finally {
                        if (jar != null) {
                            jar.close()
                        }
                    }
                }
            }

        } catch (final IOException exception) {
            logger.error("[newrelic.error] [" + TRANSFORMER_NAME + "] failed ", exception)
            throw exception
        } catch (final Exception exception) {
            logger.error("[newrelic.error] [" + TRANSFORMER_NAME + "] failed ", exception)
            throw new TransformException(exception)
        }

        logger.info("[newrelic.info] [" + TRANSFORMER_NAME + "] Finished in " + Double.valueOf((double) (
                System.currentTimeMillis() - tStart) / 1000f).toString() + " sec.")
    }

    @Override
    Map<String, Object> getParameterInputs() {
        try {
            String jarFilePath = RewriterAgent.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath()
            return ImmutableMap.<String, Object> builder()
                    .put('agent.agentArgs', System.getProperty(NewRelicGradlePlugin.NR_AGENT_ARGS_KEY))
                    .put('agent.class_rewriter', jarFilePath)
                    .put('enabled', pluginExtension.isEnabled())
                    .put('instrumentTests', pluginExtension.shouldInstrumentTests())
                    .put('identityTransform', identityTransform)
                    .put('variant', variantName)
                    .build()

        } catch (Exception e) {
            logger.error("[newrelic.error] Exception: ", e)
        }

        return Maps.newHashMap()
    }

    @Override
    Set<QualifiedContent.ContentType> getOutputTypes() {
        return contentTypes
    }

    @Override
    Collection<File> getSecondaryFileInputs() {
        return ImmutableSet.of()
    }

    @Override
    Collection<File> getSecondaryFileOutputs() {
        return ImmutableSet.of()
    }

    @Override
    Collection<File> getSecondaryDirectoryOutputs() {
        return ImmutableSet.of()
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
            logger.info("[newrelic.info] Excluding instrumentation of test variant [" + variant.fullVariantName + "]")
            return false
        }

        boolean shouldExclude = pluginExtension.shouldExcludeVariant(variant.fullVariantName) ||
                pluginExtension.shouldExcludeVariant(variant.buildTypeName)

        if (shouldExclude) {
            logger.info("[newrelic.info] Excluding instrumentation of variant [" + variant.fullVariantName + "]")
        }
        return !shouldExclude
    }

    private void dumpTransform(TransformInvocation transformInvocation) {
        // final Context context = (transformInvocation.context;
        final Collection<TransformInput> inputs = transformInvocation.inputs;
        final Collection<TransformInput> referencedInputs = transformInvocation.referencedInputs
        final TransformOutputProvider outputProvider = transformInvocation.outputProvider;

        logger.debug("[newrelic.debug] Inputs")
        inputs.each { input ->
            input.directoryInputs.each { dirInp ->
                logger.debug("[newrelic.debug]     dirInput name: " + dirInp.name)
                logger.debug("[newrelic.debug]     dirInput file: " + dirInp.file.absolutePath)

                final File output = outputProvider.getContentLocation(dirInp.name, dirInp.getContentTypes(), dirInp.getScopes(), Format.DIRECTORY)
                logger.debug("[newrelic.debug]     dirInput output: " + output.absolutePath)
                logger.debug("[newrelic.debug]")
            }

            input.jarInputs.each { jarInp ->
                logger.debug("[newrelic.debug]     jarInput name: " + jarInp.name)
                logger.debug("[newrelic.debug]     jarInput file: " + jarInp.file.absolutePath)
                logger.debug("[newrelic.debug]     jarInput status: " + jarInp.status)

                File output = outputProvider.getContentLocation(jarInp.name, jarInp.getContentTypes(), jarInp.getScopes(), Format.JAR)
                logger.debug("[newrelic.debug]     jarInput output: " + output.absolutePath)
                logger.debug("[newrelic.debug]")
            }
        }

        logger.debug("[newrelic.debug] referencedInputs")
        referencedInputs.each {
            refInput ->
                refInput.directoryInputs.each { dirInp ->
                    logger.debug("[newrelic.debug]     dirInput name: " + dirInp.name)
                    logger.debug("[newrelic.debug]     dirInput file: " + dirInp.file.absolutePath)
                    final File output = outputProvider.getContentLocation(dirInp.name,
                            dirInp.getContentTypes(),
                            dirInp.getScopes(),
                            Format.DIRECTORY)

                    logger.debug("[newrelic.debug]     dirInput output: " + output.absolutePath)
                    logger.debug("[newrelic.debug]")
                }

                refInput.jarInputs.each { jarInp ->
                    logger.debug("[newrelic.debug]     jarInput name: " + jarInp.name)
                    logger.debug("[newrelic.debug]     jarInput file: " + jarInp.file.absolutePath)
                    logger.debug("[newrelic.debug]     jarInput status: " + jarInp.status)

                    final File output = outputProvider.getContentLocation(jarInp.name,
                            jarInp.getContentTypes(),
                            jarInp.getScopes(),
                            Format.JAR)

                    logger.debug("[newrelic.debug]     jarInput output: " + output.absolutePath)
                    logger.debug("[newrelic.debug]")
                }
        }
    }

    void dumpChangedFiles(DirectoryInput dirInp) {
        FileCollection changedFiles = project.files()

        dirInp.changedFiles.each { File file, Status status ->
            switch (status) {
                case NOTCHANGED:
                    break
                case ADDED:
                case CHANGED:
                    changedFiles += project.files(file);
                    logger.quiet("[newrelic.debug] dir[" + dirInp.name + "] CHANGED or ADDED")
                    break
                case REMOVED:
                    logger.quiet("[newrelic.debug] dir[" + dirInp.name + "] REMOVED")
                    break
            }
        }
    }
}

