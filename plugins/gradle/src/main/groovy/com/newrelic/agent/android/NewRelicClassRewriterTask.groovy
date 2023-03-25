/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android

import com.newrelic.agent.compile.ClassTransformer
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.Logger
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction

abstract class NewRelicClassRewriterTask extends DefaultTask {
    final static String NAME = "newrelicClassRewriter"

    @Input
    abstract Property<String> getVariantName()

    @Input
    @Optional
    RegularFileProperty inputPath

    @TaskAction
    def newRelicClassRewriterTask() {
        long tStart = System.currentTimeMillis();

        try {
            def javaCompileProvider = BuildHelper.INSTANCE.get().variantAdapter.getJavaCompileProvider(variantName)
            def destinationDir = (inputPath == null) ? javaCompileProvider.get().destinationDir : inputPath
            def inputDir = destinationDir

            logger.info("[NewRelicClassRewriterTask] inputDir[" + inputDir + "]")
            logger.info("[NewRelicClassRewriterTask] destinationDir[" + destinationDir + "]")

            ClassTransformer classTransformer = new ClassTransformer(inputDir, destinationDir).tap {
                javaCompileProvider.configure { javaCompileTask ->
                    javaCompileTask.classpath.each {
                        addClasspath(it)
                    }
                }
                withWriteMode(ClassTransformer.WriteMode.modified)
                usingVariant(variantName)
                doTransform();
            }

        } catch (Exception e) {
            logger.error("[NewRelicClassRewriterTask] Error encountered while instrumenting class files: ", e)
            throw new RuntimeException(e)
        }

        logger.info("[NewRelicClassRewriterTask] Class instrumentation finished in " + Double.valueOf((double) (
                System.currentTimeMillis() - tStart) / 1000f).toString() + " sec.");
    }

    @Internal
    @Override
    Logger getLogger() {
        return NewRelicGradlePlugin.LOGGER
    }

}
