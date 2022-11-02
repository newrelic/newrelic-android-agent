/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android

import com.newrelic.agent.compile.ClassTransformer
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.Optional

class NewRelicClassRewriterTask extends NewRelicTask {

    @Input
    def inputVariant

    @Input
    @Optional
    File inputPath

    @TaskAction
    def newRelicClassRewriterTask() {
        long tStart = System.currentTimeMillis();

        try {
            def javaCompileTask = BuildHelper.getVariantCompileTask(inputVariant)
            def destinationDir = (inputPath == null) ? javaCompileTask.destinationDir : inputPath
            def inputDir = destinationDir

            logger.info("[newrelic.info] [NewRelicClassRewriterTask] inputDir[" + inputDir + "]")
            logger.info("[newrelic.info] [NewRelicClassRewriterTask] destinationDir[" + destinationDir + "]")

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
            logger.error("[newrelic.error] [NewRelicClassRewriterTask] Error encountered while instrumenting class files: ", e)
            throw new RuntimeException(e)
        }

        logger.info("[newrelic.info] [NewRelicClassRewriterTask] Class instrumentation finished in " + Double.valueOf((double) (
                System.currentTimeMillis() - tStart) / 1000f).toString() + " sec.");

    }
}
