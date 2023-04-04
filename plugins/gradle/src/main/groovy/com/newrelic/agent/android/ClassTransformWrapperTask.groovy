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
        logger.debug("[TransformTask] Output[${outputFile.getAbsolutePath()}]")

        try (def outputFileStream = new FileOutputStream(outputFile);
             def bufferedOutputStream = new BufferedOutputStream(outputFileStream)) {

            new JarOutputStream(bufferedOutputStream).withCloseable { jarOutputStream ->
                classDirectories.get().forEach { directory ->
                    directory.asFile.traverse(type: FileType.FILES) { classFile ->
                        String relativePath = directory.asFile.toURI().relativize(classFile.toURI()).getPath()
                        try {
                            def jarEntry = new JarEntry(relativePath.replace(File.separatorChar, '/' as char))

                            jarOutputStream.putNextEntry(jarEntry)
                            new FileInputStream(classFile).withCloseable { fileInputStream ->
                                if (FileUtils.isClass(classFile)) {
                                    transformer.processClassBytes(classFile, fileInputStream).withCloseable {
                                        jarOutputStream << it
                                    }
                                } else {
                                    jarOutputStream << fileInputStream
                                }
                            }
                        } catch (IOException ignored) {
                            jar.getInputStream(jarEntry).withCloseable { jarEntryInputStream ->
                                jarOutputStream << jarEntryInputStream
                            }
                        } finally {
                            jarOutputStream.closeEntry()
                        }
                    }
                }

                classJars.get().forEach { classJar ->
                    try (JarFile jar = new JarFile(classJar.asFile)) {
                        try {
                            def instrumentable = transformer.verifyManifest(jar)

                            for (Enumeration<JarEntry> e = jar.entries(); e.hasMoreElements();) {
                                JarEntry jarEntry = e.nextElement()

                                if (FileUtils.isClass(jarEntry.name)) {
                                    try {
                                        jarOutputStream.putNextEntry(new JarEntry(jarEntry.name))
                                        jar.getInputStream(jarEntry).withCloseable { jarEntryInputStream ->
                                            if (instrumentable) {
                                                transformer.processClassBytes(new File(jarEntry.name), jarEntryInputStream).withCloseable {
                                                    jarOutputStream << it
                                                }
                                            } else {
                                                jarOutputStream << jarEntryInputStream
                                            }
                                        }
                                    } catch (IOException ignored) {
                                        jar.getInputStream(jarEntry).withCloseable { jarEntryInputStream ->
                                            jarOutputStream << jarEntryInputStream
                                        }
                                    } finally {
                                        jarOutputStream.closeEntry()
                                    }
                                }
                            }

                        } catch (IOException e) {
                            logger.warn(e)
                        }
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
