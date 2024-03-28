/*
 * Copyright (c) 2023. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android


import com.newrelic.agent.compile.ClassTransformer
import org.junit.Assert
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import java.util.jar.JarFile

class ClassTransformerWrapperTaskTest extends PluginTest {
    def provider

    ClassTransformerWrapperTaskTest() {
        super(true);
    }

    @BeforeEach
    void setup() {
        provider = plugin.buildHelper.variantAdapter.getTransformProvider("release").get()
    }

    @Test
    void shouldInstrumentArtifact() {
        File input = new File(getClass().getResource("/bcprov-jdk18on-176.jar").toURI());
        Assert.assertTrue(provider.shouldInstrumentArtifact(new ClassTransformer(), new JarFile(input)))
    }

    @Test
    void shouldInstrumentClassFile() {
        Assert.assertTrue(provider.shouldInstrumentClassFile("test.class"))
        Assert.assertFalse(provider.shouldInstrumentClassFile("test.clazz"))
    }

    @Test
    void newrelicTransformClassesTest() {
        Assert.assertTrue(provider.inputs.getHasInputs())
        Assert.assertTrue(provider.outputs.getHasOutput())
    }

    @Test
    void getLogger() {
        Assert.assertEquals(provider.getLogger(), NewRelicGradlePlugin.LOGGER)
    }

    @Test
    void getClassDirectories() {
        if (!plugin.buildHelper.isUsingLegacyTransform()) {
            Assert.assertTrue(provider.inputs.getHasInputs())
        }
    }

    @Test
    void getClassJars() {
        Assert.assertTrue(provider.inputs.getHasInputs())
    }

    @Test
    void getOutputDirectory() {
        Assert.assertNull(provider.getOutputDirectory().getAsFile().getOrElse(null))
    }

    @Test
    void getOutput() {
        Assert.assertTrue(provider.getOutputJar().get().asFile.absolutePath.endsWith("classes.jar"))
        Assert.assertEquals(provider.getOutputJar().get().asFile.absolutePath, provider.outputs.files.asPath)
    }

}