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
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.Logger
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.*

import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream

abstract class ClassTransformWrapperTask extends DefaultTask {
    final static String NAME = "newrelicTransformClassesFor"

    @InputFiles
    abstract ListProperty<Directory> getClassDirectories();

    @InputFiles
    abstract ListProperty<RegularFile> getClassJars();

    @OutputDirectory
    @Optional
    abstract DirectoryProperty getOutputDirectory();

    @OutputFile
    abstract RegularFileProperty getOutputJar();

    @TaskAction
    void transformClasses() {
        long tStart = System.currentTimeMillis()
        def transformer = new ClassTransformer()
        File outputJarFile = outputJar.asFile.get()

        logger.debug("[TransformTask] Task[${getName()}] starting: Output JAR[${outputJarFile.getAbsolutePath()}]")
        outputJarFile.parentFile.mkdirs()

        try (def outputFileStream = new FileOutputStream(outputJarFile);
             def bufferedOutputStream = new BufferedOutputStream(outputFileStream)) {

            new JarOutputStream(bufferedOutputStream).withCloseable { jarOutputStream ->

                classDirectories.get().forEach { directory ->
                    directory.asFile.traverse(type: FileType.FILES) { classFile ->
                        String relativePath = directory.asFile.toURI().relativize(classFile.toURI()).getPath()
                        try {
                            new FileInputStream(classFile).withCloseable { fileInputStream ->
                                def jarEntry = new JarEntry(relativePath.replace(File.separatorChar, '/' as char))
                                jarOutputStream.putNextEntry(jarEntry)
                                transformer.processClassBytes(classFile, fileInputStream).withCloseable {
                                    jarOutputStream << it
                                }
                                jarOutputStream.closeEntry()
                            }
                        } catch (IOException ignored) {

                        }
                    }
                }

                classJars.get().forEach { classJar ->
                    try (JarFile jar = new JarFile(classJar.asFile)) {
                        try {
                            def instrumentable = transformer.verifyManifest(jar)

                            if (!instrumentable) {
                                return;
                            }

                            for (Enumeration<JarEntry> e = jar.entries(); e.hasMoreElements();) {
                                if (instrumentable) {
                                    try {
                                        JarEntry jarEntry = e.nextElement()
                                        jarOutputStream.putNextEntry(new JarEntry(jarEntry.name))
                                        jar.getInputStream(jarEntry).withCloseable { jarEntryInputStream ->
                                            transformer.processClassBytes(new File(jarEntry.name), jarEntryInputStream).withCloseable {
                                                jarOutputStream << it
                                            }
                                        }
                                        jarOutputStream.closeEntry()
                                    } catch (IOException ignored) {
                                    }
                                }
                            }

                        } catch (IOException e) {
                            logger.warn(e)
                        }
                    }
                }

                logger.info("[TransformTask] Finished in " + Double.valueOf((double) (
                        System.currentTimeMillis() - tStart) / 1000f).toString() + " sec.")
            }
        }

    }

    @Internal
    @Override
    Logger getLogger() {
        return NewRelicGradlePlugin.LOGGER
    }
}
