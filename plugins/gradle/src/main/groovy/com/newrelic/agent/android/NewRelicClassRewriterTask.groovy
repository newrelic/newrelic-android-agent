/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android

import com.newrelic.agent.compile.ClassTransformer
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction

abstract class NewRelicClassRewriterTask extends NewRelicTask {

    @Input
    def inputVariant

    @Input
    @Optional
    RegularFileProperty inputPath

    @TaskAction
    def newRelicClassRewriterTask() {
        long tStart = System.currentTimeMillis();

        try {
            def javaCompileTask = BuildHelper.getInstance().getVariantCompileTask(inputVariant)
            def destinationDir = (inputPath == null) ? javaCompileTask.destinationDir : inputPath
            def inputDir = destinationDir

            logger.info("[NewRelicClassRewriterTask] inputDir[" + inputDir + "]")
            logger.info("[NewRelicClassRewriterTask] destinationDir[" + destinationDir + "]")

            ClassTransformer classTransformer = new ClassTransformer(inputDir, destinationDir)
            classTransformer.withWriteMode(ClassTransformer.WriteMode.modified);
            classTransformer.usingVariant(inputVariant.name)
            if (javaCompileTask) {
                javaCompileTask.classpath.each {
                    classTransformer.addClasspath(it)
                }
            }
            classTransformer.doTransform();

        } catch (Exception e) {
            logger.error("[NewRelicClassRewriterTask] Error encountered while instrumenting class files: ", e)
            throw new RuntimeException(e)
        }

        logger.info("[NewRelicClassRewriterTask] Class instrumentation finished in " + Double.valueOf((double) (
                System.currentTimeMillis() - tStart) / 1000f).toString() + " sec.");

    }
}
