/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.compile;

import com.newrelic.agent.util.FileUtils;
import com.newrelic.agent.util.Streams;

import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

public class ClassTransformerTest {
    @Test
    public void isTransformerJarSame() {
        try {
            //transform the file
            File input = new File(getClass().getResource("/jetified-okhttp-3.10.0.jar").toURI());
            File output = File.createTempFile("okhttp.transformed.", ".jar");

            ClassTransformer transformer = new ClassTransformer(input, output);
            transformer.withWriteMode(ClassTransformer.WriteMode.always);
            transformer.transformArchive(input);

            //setup input and output files
            InputStream inputFilestream = new FileInputStream(input);
            JarInputStream jarInputStream = new JarInputStream(inputFilestream);

            InputStream outputFilestream = new FileInputStream(output);
            JarInputStream jarOutputStream = new JarInputStream(outputFilestream);

            // instrumentation should have changed (+) the file size
            Assert.assertNotEquals(Files.size(Path.of(input.getPath())), Files.size(Path.of(output.getPath())));

            JarFile inputJar = new JarFile(input);
            JarFile outputJar = new JarFile(output);
            Manifest outputRealManifest = outputJar.getManifest();

            //compare manifest files
            Assert.assertNotEquals(outputRealManifest.getMainAttributes(), inputJar.getManifest().getMainAttributes());
            Assert.assertTrue(outputRealManifest.getMainAttributes().containsKey(new Attributes.Name("Transformed-By")));
            Assert.assertTrue(outputRealManifest.getMainAttributes().containsValue("New Relic Android Agent"));

            //setup each class file comparison maps
            HashMap<String, byte[]> inputJarFileSize = new HashMap<>();
            for (JarEntry inputEntry = jarInputStream.getNextJarEntry(); inputEntry != null; inputEntry = jarInputStream.getNextJarEntry()) {
                String inputPath = inputEntry.getName();
                if (!inputEntry.isDirectory()) {
                    ByteArrayOutputStream classbytes = new ByteArrayOutputStream();
                    Streams.copy(jarInputStream, classbytes);
                    inputJarFileSize.put(inputPath, classbytes.toByteArray());
                }
            }

            HashMap<String, byte[]> outputJarFileSize = new HashMap<>();
            for (JarEntry outputEntry = jarOutputStream.getNextJarEntry(); outputEntry != null; outputEntry = jarOutputStream.getNextJarEntry()) {
                String outputPath = outputEntry.getName();
                if (!outputEntry.isDirectory()) {
                    ByteArrayOutputStream classbytes = new ByteArrayOutputStream();
                    Streams.copy(jarOutputStream, classbytes);
                    outputJarFileSize.put(outputPath, classbytes.toByteArray());
                }
            }

            //compare two jar files
            Assert.assertTrue(inputJarFileSize.size() == outputJarFileSize.size());
            Assert.assertNotEquals(inputJarFileSize, outputJarFileSize);
            Assert.assertFalse(compareJarFile(inputJar, outputJar));

            Files.delete(output.getAbsoluteFile().toPath());

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    @Test
    public void verifyThreadSafeInvocationDispatcher() throws URISyntaxException {
        ClassTransformer transformer1 = new ClassTransformer();
        ClassTransformer transformer2 = new ClassTransformer();

        Assert.assertNotEquals(transformer1.invocationDispatcher, transformer2.invocationDispatcher);
    }

    @Test
    public void ignoreSignedArtifacts() throws IOException, URISyntaxException {
        File input = new File(getClass().getResource("/bcprov-jdk18on-176.jar").toURI());
        File output = File.createTempFile("signed.", ".jar");

        try (JarFile sourceJar = new JarFile(input)) {
            ClassTransformer transformer = new ClassTransformer(sourceJar, output);
            Assert.assertFalse(transformer.verifyManifest(sourceJar));
            Assert.assertTrue(transformer.transformAndExplodeArchive(input));

            try (JarFile targetJar = new JarFile(output)) {
                Manifest sourceManifest = sourceJar.getManifest();
                Manifest targetManifest = targetJar.getManifest();

                Assert.assertEquals(sourceManifest, targetManifest);
                Assert.assertTrue(compareJarFile(sourceJar, targetJar));
            }
            Files.delete(output.getAbsoluteFile().toPath());
        }
    }

    @Test
    public void shouldSupportInstrumentationTransform() throws IOException, URISyntaxException {
        File classFile = new File(getClass().getResource("/MainActivity.class").toURI());
        ClassTransformer transformer = new ClassTransformer();

        Assert.assertTrue(transformer.transformClassFile(classFile));

        try (InputStream inputFilestream = new ByteArrayInputStream(new FileInputStream(classFile).readAllBytes());
             ByteArrayInputStream outputFilestream = transformer.processClassBytes(classFile.getName(), inputFilestream)) {

            inputFilestream.reset();
            ByteBuffer inBytes = ByteBuffer.wrap(inputFilestream.readAllBytes());
            ByteBuffer outBytes = ByteBuffer.wrap(outputFilestream.readAllBytes());
            Assert.assertTrue(inBytes.array().length < outBytes.array().length);
            Assert.assertFalse(inBytes.equals(outBytes));
        }
    }

    @Test
    public void shouldSupportIdentityTransform() throws IOException, URISyntaxException {
        File classFile = new File(getClass().getResource("/MainActivity.class").toURI());
        ClassTransformer transformer = new ClassTransformer();

        transformer.asIdentityTransform(true);
        Assert.assertFalse(transformer.transformClassFile(classFile));

        try (InputStream inputFilestream = new ByteArrayInputStream(new FileInputStream(classFile).readAllBytes());
             ByteArrayInputStream outputFilestream = transformer.processClassBytes(classFile.getName(), inputFilestream)) {
            inputFilestream.reset();
            ByteBuffer inBytes = ByteBuffer.wrap(inputFilestream.readAllBytes());
            ByteBuffer outBytes = ByteBuffer.wrap(outputFilestream.readAllBytes());
            Assert.assertEquals(inBytes.array().length, outBytes.array().length);
            Assert.assertTrue(inBytes.equals(outBytes));
        }
    }

    /**
     * Compare two JARs for equivalency. Identical jars should:
     * . have same number of entries
     * . have entries with matching CRC values
     * . contains matching entry names
     *
     * @param source JAR
     * @param target JAR
     * @return true if JARS are equal
     * @throws IOException
     */
    private boolean compareJarFile(JarFile source, JarFile target) throws IOException {
        HashSet<String> sourceEntries = new HashSet<>();
        HashMap<String, Long> sourceCRC = new HashMap<>();
        HashSet<String> targetEntries = new HashSet<>();
        HashMap<String, Long> targetCRC = new HashMap<>();

        source.stream().forEach(file -> {
            sourceEntries.add(file.getName());
            sourceCRC.put(file.getName(), file.getCrc());
        });

        source.stream().forEach(file -> {
            targetEntries.add(file.getName());
            targetCRC.put(file.getName(), file.getCrc());
        });

        if (!sourceCRC.keySet().equals(targetCRC.keySet())) {
            return false;
        }

        if (!sourceCRC.equals(targetCRC)) {
            return false;
        }


        if (!sourceEntries.equals(targetEntries)) {
            return false;
        }

        return true;
    }
}
