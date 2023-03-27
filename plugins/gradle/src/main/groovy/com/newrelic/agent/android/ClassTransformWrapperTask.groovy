/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android

import com.newrelic.agent.compile.ClassTransformer
import groovy.io.FileType
import org.gradle.api.DefaultTask
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFile
import org.gradle.api.logging.Logger
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.*

import java.util.jar.JarFile
import java.util.jar.JarOutputStream

abstract class ClassTransformWrapperTask extends DefaultTask {
    final static String NAME = "newrelicTransform"

    @InputFiles
    @Optional
    abstract ListProperty<Directory> getAllClasses();

    @InputFiles
    @Optional
    abstract ListProperty<RegularFile> getAllJars();

    @OutputFiles
    abstract DirectoryProperty getOutput();

    @TaskAction
    void transformClasses() {
        long tStart = System.currentTimeMillis()

        logger.info("[${NAME}] Starting")
        logger.info("[TransformTask] Output[${getOutput().get().getAsFile().getAbsolutePath()}]")

        OutputStream jarOutput = new JarOutputStream(
                new BufferedOutputStream(
                        new FileOutputStream(output.get().getAsFile())
                )
        )

        allClasses.get().forEach { directory ->
            logger.debug("[TransformTask] Directory[${directory.asFile.getAbsolutePath()}]")
            directory.asFile.traverse(type: FileType.FILES) { file ->
                String relativePath = directory.asFile.toURI().relativize(file.toURI()).getPath()
                logger.info("[TransformTask]    File[${relativePath.replace(File.separatorChar, '/' as char)}]")
            }
            /* TODO */
            def classTransformer = new ClassTransformer(directory.asFile, output.get().asFile)
            classTransformer.withWriteMode(ClassTransformer.WriteMode.always).transformDirectory(directory.asFile)
            /**/
        }

        allJars.get().forEach { file ->
            logger.info("[TransformTask] JarFile[${file.asFile.getAbsolutePath()}]")
            try (JarFile jar = new JarFile(file.asFile)) {
                /* TODO */
                def classTransformer = new ClassTransformer(jar, output.get().asFile)
                classTransformer.withWriteMode(ClassTransformer.WriteMode.always).transformArchive(file.asFile)
                /**/
            }
        }

    }

    @Internal
    @Override
    Logger getLogger() {
        return NewRelicGradlePlugin.LOGGER
    }
}
