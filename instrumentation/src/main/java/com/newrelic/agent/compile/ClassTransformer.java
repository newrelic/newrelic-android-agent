/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.compile;

import com.google.common.collect.ImmutableSet;
import com.newrelic.agent.InstrumentationAgent;
import com.newrelic.agent.util.FileUtils;
import com.newrelic.agent.util.Streams;

import org.slf4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

public final class ClassTransformer {
    public static final String MANIFEST_TRANSFORMED_BY_KEY = "Transformed-By";
    public static final String MANIFEST_SHA_DIGEST_REGEX = "^SHA-.*-Digest$";
    public static final Set<String> EXCLUDE_MANIFEST_EXTENSIONS = ImmutableSet.of("DSA", "RSA", "SF", "EC");

    private final Logger log;
    private File inputFile;
    private File outputFile;
    private ClassData classData;
    private boolean identityTransform;
    private WriteMode writeMode;
    private Map<String, String> agentOptions = new HashMap<String, String>();
    final InvocationDispatcher invocationDispatcher;

    public enum WriteMode {
        modified,
        always     // Transform API requires full data even if not changed
    }

    private InvocationDispatcher initDispatcher() {
        log.debug("Initializing InvocationDispatcher from ClassTransformer");
        log.debug("logInstrumentationEnabled: {}", agentOptions.get("logInstrumentationEnabled"));
        log.debug("logInstrumentationEnabled Parsed Value: {}", Boolean.parseBoolean(agentOptions.get("logInstrumentationEnabled")));
        log.debug("defaultInteractionsEnabled: {}", agentOptions.get("defaultInteractionsEnabled"));
        log.debug("defaultInteractionsEnabled Parsed Value: {}", Boolean.parseBoolean(agentOptions.get("defaultInteractionsEnabled")));
        try {
            return new InvocationDispatcher(log,Boolean.parseBoolean(agentOptions.get("logInstrumentationEnabled")), Boolean.parseBoolean(agentOptions.get("defaultInteractionsEnabled")));
        } catch (Exception e) {
            log.error("ClassTransformer: could not allocate InvocationDispatcher! " + e);
        }
        return null;
    }

    public ClassTransformer() {
        this.log = InstrumentationAgent.LOGGER;
        this.agentOptions = InstrumentationAgent.getAgentOptions();
        this.inputFile = new File(".").getAbsoluteFile();
        this.outputFile = new File(".").getAbsoluteFile();
        this.classData = null;
        this.identityTransform = false;
        this.writeMode = WriteMode.modified;
        this.invocationDispatcher = initDispatcher();
    }

    public ClassTransformer(File classPath, File outputDir) {
        this();

        this.inputFile = classPath;
        this.outputFile = outputDir;
        if (classPath.isDirectory()) {
            this.inputFile = classPath;
        }
    }

    public ClassTransformer(JarFile jarFile, File outputJar) {
        this();
        File jar = new File(jarFile.getName());
        this.inputFile = jar.getParentFile();
        this.outputFile = outputJar;
    }

    /*
     * Transforms an array of bytes using our class rewriter. This is the cut point used
     * by legacy, Dexguard and Transform API class transformations.
     *
     * This method provides the method signature the class rewriter will use when
     * instrumenting legacy class code. Signature must match what's declared
     * in createTransformClassAdapter, currently "(Ljava/lang/String;[B)[B")
     *
     * @param classPathname Used to discern resource type and logging
     * @param bytes Array of bytes containing the class bytecode
     * @return Array of transformed bytes if class was processed; otherwise returns null
     * if transformation is disabled or class could not be transformed.
     */
    public byte[] transformClassBytes(String classPathname, byte[] bytes) {

        if (FileUtils.isClass(classPathname) && !identityTransform) {
            try {
                if (bytes != null) {
                    classData = invocationDispatcher.visitClassBytes(bytes);
                    if (classData != null && classData.getClassBytes() != null && classData.isModified()) {
                        return classData.getClassBytes();
                    }
                }

            } catch (Exception e) {
                log.error("[ClassTransformer] " + e);
            }
        }

        return null;
    }

    /*
     * Transform an array of bytes considered to contain a instrumentable resource,
     * such as a Java class. Other resources are generally ignored.
     *
     * Returned ByteArrayInputStream is Autocloseable, but caller *should* close stream once
     * the processed bytes have been consumed.
     *
     * @param classFilePath Path name of file containing bytes, mostly for resource identification and logging
     * @param classFileInputStream InputStream holding bytes
     * @return ByteArrayInputStream containing transformed bytes if successful, or the original
     * bytes otherwise.
     **/
    public ByteArrayInputStream transformClassByteStream(String classFilePath, InputStream classFileInputStream) throws IOException {
        byte[] classBytes = Streams.slurpBytes(classFileInputStream);
        byte[] transformedClassBytes = transformClassBytes(classFilePath, classBytes);
        final ByteArrayInputStream processedClassBytesStream;

        if (transformedClassBytes == null) {
            processedClassBytesStream = new ByteArrayInputStream(classBytes);
        } else {
            if ((classBytes.length != transformedClassBytes.length) &&
                    (classData != null && classData.isModified())) {
                log.debug("[ClassTransformer] Rewrote class[" + classFilePath + "] bytes[" +
                        classBytes.length + "] rewritten[" + transformedClassBytes.length + "]");
            }
            processedClassBytesStream = new ByteArrayInputStream(transformedClassBytes);
        }

        return processedClassBytesStream;
    }

    /*
     * Transform a single resource contained in classFile. Writes output of transformation
     * to new file with the same name in the ClassTransformer's output location.
     *
     * @param classFile File containing resource
     * @return True if output was written
     */
    public boolean transformClassFile(File classFile) {
        boolean didProcessClass = false;        // true when class bytes are written to output

        try {
            if (FileUtils.isArchive(classFile)) {
                didProcessClass = transformAndExplodeArchive(classFile);

            } else if (classFile.isDirectory()) {
                didProcessClass = transformDirectory(classFile);

            } else {
                String classpath = classFile.getAbsolutePath();

                if (classpath.startsWith(inputFile.getAbsolutePath())) {
                    classpath = classpath.substring(inputFile.getAbsolutePath().length() + 1);
                }

                File transformedClassFile = new File(outputFile, classpath);
                InputStream classBytesInputStream = null;
                InputStream classBytesOutputStream = null;

                try {
                    classBytesInputStream = new FileInputStream(classFile);

                    if (FileUtils.isClass(classFile)) {
                        classBytesOutputStream = transformClassByteStream(classpath, classBytesInputStream);
                        didProcessClass = writeClassFile(classBytesOutputStream, transformedClassFile);
                    } else {
                        log.debug("[ClassTransformer] Class ignored: " + classFile.getName());
                        // pass through non-class files
                        didProcessClass = writeClassFile(classBytesInputStream, transformedClassFile);
                    }

                } catch (Exception e) {
                    log.error("[ClassTransformer] transformClassFile: " + e);
                    didProcessClass = writeClassFile(classBytesInputStream, transformedClassFile);

                } finally {
                    closeQuietly(classBytesInputStream);
                    closeQuietly(classBytesOutputStream);
                }

            }

        } catch (Exception e) {
            log.error("[ClassTransformer] transformClassFile: " + e);
        }

        return didProcessClass;
    }

    public boolean transformDirectory(File directory) {
        boolean didProcessDirectory = false;

        if (directory.isDirectory()) {
            for (File f : Objects.requireNonNull(directory.listFiles())) {
                didProcessDirectory |= transformClassFile(f);
            }
        }

        return didProcessDirectory;
    }

    public boolean transformAndExplodeArchive(File archiveFile) throws IOException {
        return transformArchive(archiveFile, true);
    }

    public boolean transformArchive(File archiveFile) throws IOException {
        return transformArchive(archiveFile, false);
    }

    /*
     * Transforms resources in an archive (JAR,AAR), rewriting the archive to the
     * class transformer's output location. If expodeJar is true, each class is written to
     * output instead of the archive.
     *
     * @param archiveFile Name of file containing archive
     * @param explodeJar  If true, write each class encountered to output.
     *                    Otherwise, rewrite the archive itself.
     * @return True if archive or any of its members was written to output.
     */
    boolean transformArchive(File archiveFile, boolean explodeJar) throws IOException {
        boolean didProcessArchive = false;      // true is any member of the archive was written

        if (FileUtils.isSupportJar(archiveFile)) {
            log.debug("[ClassTransformer] Skipping support jar [" + archiveFile.getPath() + "]");
            return false;
        }

        log.debug("[ClassTransformer] Transforming archive[" + archiveFile.getCanonicalPath() + "]");

        InputStream archiveFileInputStream = null;
        ByteArrayOutputStream byteArrayOutputStream = null;
        JarInputStream jarInputStream = null;
        JarOutputStream jarOutputStream = null;
        JarFile jarFile = null;

        try {
            jarFile = new JarFile(archiveFile);
            byteArrayOutputStream = new ByteArrayOutputStream();
            archiveFileInputStream = new FileInputStream(archiveFile);
            jarInputStream = new JarInputStream(archiveFileInputStream);
            jarOutputStream = new JarOutputStream(byteArrayOutputStream);

            boolean doTransform = verifyAndWriteManifest(jarFile, jarOutputStream);

            if (!doTransform) {
                log.info("[ClassTransformer] Skipping instrumentation of signed jar [" + archiveFile.getPath() + "]");
                return Streams.copy(new FileInputStream(archiveFile), new FileOutputStream(outputFile)) > 0;
            }

            for (JarEntry entry = jarInputStream.getNextJarEntry(); entry != null; entry = jarInputStream.getNextJarEntry()) {
                String jarEntryPath = entry.getName();

                if (!entry.isDirectory()) {
                    JarEntry jarEntry = new JarEntry(jarEntryPath);
                    File archiveClassFile = new File(outputFile, jarEntryPath);
                    InputStream classBytesInputStream = null;
                    InputStream classBytesOutputStream = null;

                    try {
                        jarEntry.setTime(entry.getTime());
                        jarOutputStream.putNextEntry(jarEntry);
                        classBytesInputStream = jarFile.getInputStream(entry);
                        classBytesOutputStream = transformClassByteStream(archiveClassFile.getPath(), classBytesInputStream);

                        if (explodeJar) {
                            didProcessArchive |= writeClassFile(classBytesOutputStream, archiveClassFile);
                        } else {
                            writeClassStream(classBytesOutputStream, jarOutputStream);
                            didProcessArchive = true;
                        }

                        jarOutputStream.flush();
                        jarOutputStream.closeEntry();

                    } catch (Exception e) {
                        log.warn("[ClassTransformer] transformArchive: " + e);
                        if (explodeJar) {
                            didProcessArchive |= writeClassFile(classBytesInputStream, archiveClassFile);
                        } else {
                            writeClassStream(classBytesInputStream, jarOutputStream);
                            didProcessArchive = true;
                        }

                    } finally {
                        closeQuietly(classBytesInputStream);
                        closeQuietly(classBytesOutputStream);
                    }
                }
            }

            if (didProcessArchive) {
                //
                // write JAR back to transforms location
                //
                File rewrittenJar = new File(outputFile.getAbsolutePath());
                if (archiveFile.getAbsolutePath() != rewrittenJar.getAbsolutePath()) {
                    log.debug("[ClassTransformer] Rewriting archive to [" + rewrittenJar.getAbsolutePath() + "]");
                    closeQuietly(jarOutputStream);
                    try (ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(byteArrayOutputStream.toByteArray())) {
                        writeClassFile(byteArrayInputStream, rewrittenJar);
                    } catch (Exception e) {
                        log.error("[ClassTransformer] transformArchive: " + e);
                    }
                } else {
                    log.error("[ClassTransformer] Refusing to overwrite archive [" + rewrittenJar.getAbsolutePath() + "]");
                }
            }

        } catch (Exception e) {
            log.warn("[ClassTransformer] transformArchive: Original library file is unmodified due to exception: " + e.getLocalizedMessage());
            try (StringWriter sw = new StringWriter(); PrintWriter pw = new PrintWriter(sw)) {
                e.printStackTrace(pw);
                log.debug("[ClassTransformer] transformArchive: " + sw);
            }
            return Streams.copy(new FileInputStream(archiveFile), new FileOutputStream(outputFile)) > 0;
        } finally {
            // close all the streams that may or may npt have already been closed
            closeQuietly(jarFile);
            closeQuietly(archiveFileInputStream);
            closeQuietly(byteArrayOutputStream);
            closeQuietly(jarInputStream);
            closeQuietly(jarOutputStream);
        }

        return didProcessArchive;
    }

    public boolean verifyManifest(JarFile jarFile) throws IOException {
        Manifest realManifest = jarFile.getManifest();

        if (realManifest != null) {

            Map<String, Attributes> entries = realManifest.getEntries();
            for (String entryKey : entries.keySet()) {
                Attributes attrs = realManifest.getAttributes(entryKey);
                for (Object attr : attrs.keySet()) {
                    String attrKeyName = attr.toString();
                    if (attrKeyName.matches(MANIFEST_SHA_DIGEST_REGEX))
                        return false;
                }
            }

            realManifest.getMainAttributes().put(new Attributes.Name(MANIFEST_TRANSFORMED_BY_KEY), "New Relic Android Agent");
        }

        return true;
    }

    public boolean verifyAndWriteManifest(JarFile jarFile, JarOutputStream jarOutputStream) throws IOException {
        if (!verifyManifest(jarFile)) {
            return false;
        }

        Manifest manifest = jarFile.getManifest();
        if (manifest == null) {
            manifest = new Manifest();
        }

        JarEntry manifestJarEntry = new JarEntry(JarFile.MANIFEST_NAME);
        manifest.getMainAttributes().put(new Attributes.Name(MANIFEST_TRANSFORMED_BY_KEY), "New Relic Android Agent");
        jarOutputStream.putNextEntry(manifestJarEntry);
        manifest.write(jarOutputStream);
        jarOutputStream.flush();
        jarOutputStream.closeEntry();

        return true;
    }

    public ClassTransformer asIdentityTransform(boolean identityTransform) {
        this.identityTransform = identityTransform;
        return this;
    }

    public ClassTransformer asMutableTransform(boolean mutableTransform) {
        return asIdentityTransform(!mutableTransform);
    }

    public ClassTransformer withWriteMode(WriteMode writeMode) {
        this.writeMode = writeMode;
        return this;
    }

    public ClassTransformer usingVariant(String variantName) {
        if (!(variantName == null || variantName.isEmpty())) {
            invocationDispatcher.getInstrumentationContext().setVariantName(variantName);
        }
        return this;
    }

    protected boolean writeClassStream(InputStream inStream, OutputStream outStrm) throws IOException {
        if ((writeMode == WriteMode.always ||
                (writeMode == WriteMode.modified && (classData != null && classData.isModified())))) {
            if (inStream != null) {
                return (0 < Streams.copy(inStream, outStrm));
            }
        }

        return false;
    }

    protected boolean writeClassFile(InputStream inStream, File className) throws IOException {
        boolean writeResult = false;
        if ((writeMode == WriteMode.always ||
                (writeMode == WriteMode.modified && (classData != null && classData.isModified())))) {
            if (inStream != null && className != null) {
                className.getParentFile().mkdirs();  // ensure directory is available
                try (FileOutputStream modifiedClassBytesStream = new FileOutputStream(className)) {
                    writeResult = writeClassStream(inStream, modifiedClassBytesStream);
                    closeQuietly(modifiedClassBytesStream);
                } catch (FileNotFoundException e) {
                    // should not happen
                    log.debug("writeClassFile: " + e);
                } catch (IOException e) {
                    log.error("writeClassFile: " + e);
                }
            } else {
                log.error("writeClassFile: input stream or class name is missing!");
            }
        }

        return writeResult;
    }

    private void closeQuietly(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException e) {
                log.warn("[ClassTransformer] closeQuietly: " + e);
            }
        }
    }
}
