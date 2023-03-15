/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.compile;

import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.HashMap;
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
            File output = new File(getClass().getResource("/test.jar").toURI());
            ClassTransformer transformer = new ClassTransformer(input, output);
            transformer.withWriteMode(ClassTransformer.WriteMode.always);
            transformer.transformArchive(input);

            //setup input and output files
            InputStream inputFilestream = new FileInputStream(input);
            JarInputStream jarInputStream = new JarInputStream(inputFilestream);

            InputStream outputFilestream = new FileInputStream(output);
            JarInputStream jarOutputStream = new JarInputStream(outputFilestream);
            JarFile outputJar = new JarFile(output);
            Manifest outputRealManifest = outputJar.getManifest();

            //compare manifest files
            Assert.assertTrue(outputRealManifest.getMainAttributes().containsKey(new Attributes.Name("Transformed-By")));
            Assert.assertTrue(outputRealManifest.getMainAttributes().containsValue("New Relic Android Agent"));

            //setup each class file comparsion maps
            HashMap<String, byte[]> inputJarFileSize = new HashMap<String, byte[]>();
            for (JarEntry inputEntry = jarInputStream.getNextJarEntry(); inputEntry != null; inputEntry = jarInputStream.getNextJarEntry()) {
                String inputPath = inputEntry.getName();
                if (!inputEntry.isDirectory()) {
                    byte[] classbytes = null;
                    if (inputEntry.getSize() != -1) {
                        classbytes = new byte[(int) inputEntry.getSize()];
                    } else {
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        while (true) {
                            int qwe = jarInputStream.read();
                            if (qwe == -1) break;
                            baos.write(qwe);
                        }
                        classbytes = baos.toByteArray();
                    }
                    inputJarFileSize.put(inputPath, classbytes);
                }
            }

            HashMap<String, byte[]> outputJarFileSize = new HashMap<String, byte[]>();
            for (JarEntry outputEntry = jarOutputStream.getNextJarEntry(); outputEntry != null; outputEntry = jarOutputStream.getNextJarEntry()) {
                String outputPath = outputEntry.getName();
                if (!outputEntry.isDirectory()) {
                    byte[] classbytes = null;
                    if (outputEntry.getSize() != -1) {
                        classbytes = new byte[(int) outputEntry.getSize()];
                    } else {
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        while (true) {
                            int qwe = jarOutputStream.read();
                            if (qwe == -1) break;
                            baos.write(qwe);
                        }
                        classbytes = baos.toByteArray();
                    }
                    outputJarFileSize.put(outputPath, classbytes);
                }
            }

            //compare two jar files
            Assert.assertTrue(inputJarFileSize.size() == outputJarFileSize.size());
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    @Test
    public void verifyThreadSafeInvocationDispatcher() throws URISyntaxException {
        File input = new File(getClass().getResource("/jetified-okhttp-3.10.0.jar").toURI());
        File output = new File(getClass().getResource("/test.jar").toURI());

        ClassTransformer transformer1 = new ClassTransformer(input, output);
        ClassTransformer transformer2 = new ClassTransformer(input, output);

        Assert.assertNotEquals(transformer1.invocationDispatcher, transformer2.invocationDispatcher);
    }
}
