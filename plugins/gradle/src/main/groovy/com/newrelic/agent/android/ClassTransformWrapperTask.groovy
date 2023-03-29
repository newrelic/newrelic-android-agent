/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android

import com.newrelic.agent.compile.ClassTransformer
import groovy.io.FileType
import org.apache.commons.io.FileUtils
import org.gradle.api.DefaultTask
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFile
import org.gradle.api.logging.Logger
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.*

import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream

abstract class ClassTransformWrapperTask extends DefaultTask {
    final static String NAME = "newrelicTransform"

    @InputFiles
    abstract ListProperty<Directory> getClassDirectories();

    @InputFiles
    @Optional
    abstract ListProperty<RegularFile> getClassJars();

    @OutputFiles
    abstract DirectoryProperty getOutput();

    @TaskAction
    void transformClasses() {
        long tStart = System.currentTimeMillis()
        def transformer = new ClassTransformer()
        def outputFile = output.get().getAsFile()

        logger.debug("[TransformTask] ${NAME} Starting")
        logger.debug("[TransformTask] Output[${getOutput().get().getAsFile().getAbsolutePath()}]")

        outputFile.mkdirs()

        new JarOutputStream(new BufferedOutputStream(new FileOutputStream(new File(outputFile, "classes.jar"))))
                .withCloseable { jarOutputStream ->
                    classDirectories.get().forEach { directory ->
                        directory.asFile.traverse(type: FileType.FILES) { classFile ->
                            String relativePath = directory.asFile.toURI().relativize(classFile.toURI()).getPath()

                            try {
                                def jarEntry = new JarEntry(relativePath.replace(File.separatorChar, '/' as char))
                                jarOutputStream.putNextEntry(jarEntry)
                                new FileInputStream(classFile).withCloseable { fileInputStream ->
                                    jarOutputStream << transformer.processClassBytes(classFile, fileInputStream)
                                }
                            } finally {
                                jarOutputStream.closeEntry()
                            }
                        }
                    }

                    classJars.get().forEach { classJar ->
                        try (JarFile jar = new JarFile(classJar.asFile)) {
                            try {
                                JarEntry manifest = new JarEntry(JarFile.MANIFEST_NAME)
                                if (transformer.verifyAndWriteManifest(jar, jarOutputStream)) {
                                    for (Enumeration<JarEntry> e = jar.entries(); e.hasMoreElements();) {
                                        JarEntry jarEntry = e.nextElement()
                                        try {
                                            jarOutputStream.putNextEntry(new JarEntry(jarEntry.name))
                                            jar.getInputStream(jarEntry).withCloseable { jarEntryInputStream ->
                                                jarOutputStream << transformer.processClassBytes(new File(jarEntry.name), jarEntryInputStream)
                                            }
                                        } finally {
                                            jarOutputStream.closeEntry()
                                        }
                                    }
                                }
                            } catch (IOException e) {
                                // return Streams.copy(new FileInputStream(file.asFile), new FileOutputStream(outputFile)) > 0;
                                e
                            }
                        }
                    }
                }

        logger.info("[$NAME] Finished in " + Double.valueOf((double) (
                System.currentTimeMillis() - tStart) / 1000f).toString() + " sec.")
    }

    @Internal
    @Override
    Logger getLogger() {
        return NewRelicGradlePlugin.LOGGER
    }
}
