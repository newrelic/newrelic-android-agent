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
import java.util.zip.ZipFile

abstract class ClassTransformWrapperTask extends DefaultTask {
    final static String NAME = "newrelicTransformClassesFor"

    @Internal
    NewRelicExtension ext

    ClassTransformWrapperTask() {
        this.ext = NewRelicExtension.register(project)
    }

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
                    directory.asFile.traverse(type: FileType.DIRECTORIES) { classFileDir ->
                        String relativePath = directory.asFile.toURI().relativize(classFileDir.toURI()).getPath()
                        String normalizedPath = relativePath?.replace(File.separatorChar, '/' as char)

                        if (ext.shouldExcludePackageInstrumentation(normalizedPath)) {
                            logger.debug("[ClassTransform] Excluding package [${relativePath}] from instrumentation")
                        }
                    }

                    directory.asFile.traverse(type: FileType.FILES) { classFile ->
                        String relativePath = directory.asFile.toURI().relativize(classFile.toURI()).getPath()
                        String normalizedPath = relativePath?.replace(File.separatorChar, '/' as char)

                        try {
                            new FileInputStream(classFile).withCloseable { fileInputStream ->
                                def jarEntry = new JarEntry(normalizedPath)
                                try {
                                    jarOutputStream.putNextEntry(jarEntry)
                                    try {
                                        transformer.asMutableTransform(shouldInstrumentClassFile(jarEntry.name))
                                        transformer.transformClassByteStream(classFile.path, fileInputStream).withCloseable {
                                            jarOutputStream << it
                                        }
                                    } catch (RuntimeException | IOException re) {
                                        logger.error("[ClassTransform] Instrumentation is disabled for [${classFile.name}] with exception: " + re.gcalizedMessage())
                                        try (StringWriter sw = new StringWriter(); PrintWriter pw = new PrintWriter(sw)) {
                                            re.printStackTrace(pw)
                                            logger.debug("[ClassTransform] Instrumentation is disabled for [${classFile.name}]: " + sw.toString())
                                        }
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
                            logger.error("[ClassTransform] [${classFile.path}] ${fileException.message}")
                        }
                    }
                }

                classJars.get().forEach { classJar ->
                    try (JarFile jar = new JarFile(classJar.asFile, false, ZipFile.OPEN_READ)) {
                        boolean instrumentable = shouldInstrumentArtifact(transformer, jar)

                        try {
                            for (Enumeration<JarEntry> e = jar.entries(); e.hasMoreElements();) {
                                try {
                                    JarEntry jarEntry = e.nextElement()
                                    try {
                                        jarOutputStream.putNextEntry(new JarEntry(jarEntry.name))

                                        if (jarEntry.directory) {
                                            if (ext.shouldExcludePackageInstrumentation(jarEntry.name)) {
                                                logger.info("[ClassTransform] Excluding package [${jarEntry.name}] from instrumentation")
                                            }
                                        }

                                        jar.getInputStream(jarEntry).withCloseable { jarEntryInputStream ->
                                            try {
                                                transformer.asMutableTransform(shouldInstrumentClassFile(jarEntry.name))
                                                transformer.transformClassByteStream(jarEntry.name, jarEntryInputStream).withCloseable {
                                                    jarOutputStream << it
                                                }
                                            } catch (RuntimeException | IOException re) {
                                                logger.warn("[ClassTransform] Instrumentation is disabled for [${jarEntry.name}] with exception: " + re.getLocalizedMessage())
                                                try (StringWriter sw = new StringWriter(); PrintWriter pw = new PrintWriter(sw)) {
                                                    re.printStackTrace(pw)
                                                    logger.debug("[ClassTransform] Instrumentation is disabled for [${classFile.name}]: " + sw.toString())
                                                }
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

    boolean shouldInstrumentClassFile(String classFile) {
        boolean shouldInstrument = FileUtils.isClass(classFile)

        if (shouldInstrument) {
            if (ext.shouldExcludePackageInstrumentation(classFile)) {
                return false
            }
        }

        return shouldInstrument
    }

    boolean shouldInstrumentArtifact(ClassTransformer transformer, JarFile jar) {
        try {
            if (!transformer.verifyManifest(jar)) {
                // signed or other unsupported JAR file, rewrite it unmodified
                logger.warn("[ClassTransform] Signed or possibly incompatible artifact detected: [${jar.name}]")
                return true
            }
        } catch (IOException e) {
            logger.error("[ClassTransform] Manifest error: ${e}")
            return false
        }

        return true
    }

    @Internal
    @Override
    Logger getLogger() {
        return NewRelicGradlePlugin.LOGGER
    }
}
