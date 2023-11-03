/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android

import com.newrelic.agent.compile.ClassTransformer
import com.newrelic.agent.util.FileUtils
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

        logger.debug("[ClassTransform] Task[${getName()}] starting: Output JAR[${outputJarFile.getAbsolutePath()}]")
        outputJarFile.parentFile.mkdirs()

        try (def outputFileStream = new FileOutputStream(outputJarFile)
             def bufferedOutputStream = new BufferedOutputStream(outputFileStream)) {

            new JarOutputStream(bufferedOutputStream).withCloseable { jarOutputStream ->

                classDirectories.get().forEach { directory ->
                    directory.asFile.traverse(type: FileType.FILES) { classFile ->
                        String relativePath = directory.asFile.toURI().relativize(classFile.toURI()).getPath()

                        try {
                            new FileInputStream(classFile).withCloseable { fileInputStream ->
                                def jarEntry = new JarEntry(relativePath.replace(File.separatorChar, '/' as char))
                                try {
                                    jarOutputStream.putNextEntry(jarEntry)
                                    transformer.asIdentityTransform(!shouldInstrumentClassFile(classFile))
                                    try {
                                        transformer.processClassBytes(classFile.path, fileInputStream).withCloseable {
                                            jarOutputStream << it
                                        }
                                    } catch (RuntimeException re) {
                                        logger.warn("[ClassTransform] Instrumentation is disabled for [${classFile.name}] with exception: " + re.getLocalizedMessage())
                                        jarOutputStream << fileInputStream
                                    } catch (IOException ioE) {
                                        logger.warn("[ClassTransform] Instrumentation is disabled for [${classFile.name}] with exception: " + ioE.getLocalizedMessage())
                                        jarOutputStream << fileInputStream
                                    }
                                    jarOutputStream.closeEntry()
                                } catch (IOException ioE) {
                                    // ignore the duplicate file structure entry
                                    if (!(jarEntry.directory || jarEntry.name.startsWith("META-INF/"))) {
                                        throw ioE
                                    }
                                }
                            }
                        } catch (IOException fileException) {
                            logger.error("[ClassTransform] [${classJar.asFile.path}] ${fileException.message}")
                        }
                    }
                }

                classJars.get().forEach { classJar ->
                    try (JarFile jar = new JarFile(classJar.asFile)) {
                        boolean shouldInstrument = shouldInstrumentArtifact(transformer, jar)

                        try {
                            transformer.asIdentityTransform(!shouldInstrument);

                            for (Enumeration<JarEntry> e = jar.entries(); e.hasMoreElements();) {
                                try {
                                    JarEntry jarEntry = e.nextElement()
                                    try {
                                        jarOutputStream.putNextEntry(new JarEntry(jarEntry.name))
                                        jar.getInputStream(jarEntry).withCloseable { jarEntryInputStream ->
                                            try {
                                                transformer.processClassBytes(jarEntry.name, jarEntryInputStream).withCloseable {
                                                    jarOutputStream << it
                                                }
                                            } catch (RuntimeException re) {
                                                logger.warn("[ClassTransform] Instrumentation is disabled for [${jarEntry.name}] with exception: " + re.getLocalizedMessage())
                                                jarOutputStream << jarEntryInputStream
                                            } catch (IOException ioE) {
                                                logger.warn("[ClassTransform] Instrumentation is disabled for [${jarEntry.name}] with exception: " + ioE.getLocalizedMessage())
                                                jarOutputStream << jarEntryInputStream
                                            }
                                        }
                                        jarOutputStream.closeEntry()
                                    } catch (IOException ioE) {
                                        // ignore the duplicate file structure entry
                                        if (!(jarEntry.directory || jarEntry.name.startsWith("META-INF/"))) {
                                            throw ioE
                                        }
                                    }
                                } catch (IOException jarEntryException) {
                                    logger.error("[ClassTransform] [${classJar.asFile.path}] ${jarEntryException.message}")
                                }
                            }

                        } catch (IOException jarException) {
                            logger.error(("[ClassTransform] [${classJar.asFile.path}] ${jarException.message}"))
                        }
                    }
                }

                logger.info("[ClassTransform] Finished in " + Double.valueOf((double) (
                        System.currentTimeMillis() - tStart) / 1000f).toString() + " sec.")
            }
        }

    }

    boolean shouldInstrumentClassFile(File classFile) {
        boolean shouldInstrument = FileUtils.isClass(classFile)
        // TODO: exclude package names
        shouldInstrument
    }

    boolean shouldInstrumentArtifact(ClassTransformer transformer, JarFile jar) {
        boolean shouldInstrument = true;

        try {
            shouldInstrument = transformer.verifyManifest(jar)
            if (!shouldInstrument) {
                // signed or other unsupported JAR file, rewrite it unmodified
                logger.warn("[ClassTransform] Signed dependency artifacts [${jar.name}] are not instrumented.")
            }
        } catch (IOException e) {
            logger.error("[ClassTransform] Manifest error: ${e}");
            shouldInstrument = false
        }

        shouldInstrument
    }

    @Internal
    @Override
    Logger getLogger() {
        return NewRelicGradlePlugin.LOGGER
    }
}
