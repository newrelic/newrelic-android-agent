/*
 * Copyright (c) 2023. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android


import org.junit.Assert
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito

class ClassTransformerWrapperTaskTest extends PluginTest {
    NewRelicExtension ext
    BuildHelper buildHelper
    VariantAdapter variantAdapter
    def provider

    ClassTransformerWrapperTaskTest() {
        super(true);
    }

    @BeforeEach
    void setup() {
        // Create the instances needed to test this class
        ext = NewRelicExtension.register(project)
        buildHelper = BuildHelper.register(project)
        variantAdapter = buildHelper.variantAdapter
        variantAdapter.configure(ext)

        provider = Mockito.spy(buildHelper.variantAdapter.getTransformProvider("release"))

        provider.configure {

        }
    }

    @Test
    void getVariantName() {
        provider.get().tap {
            Assert.assertEquals("release", getVariantName())
        }
    }

    @Test
    void shouldInstrumentArtifact() {
        def task = provider.get().tap {
            // Assert.assertFalse(shouldInstrumentArtifact())
        }
    }

    @Test
    void shouldInstrumentClassFile() {
        def task = provider.get().tap {
            // Assert.assertTrue(shouldInstrumentClassFile(""))
        }
    }

    @Test
    void newrelicTransformClassesTest() {
        provider.configure {
            // @TaskAction
            // transformClasses()
        }
    }

    @Test
    void getLogger() {
        // Assert.assertEquals(provider.get().logger, NewRelicGradlePlugin.LOGGER)
    }


    @Test
    void getClassDirectories() {
        provider.get().tap {

        }
    }

    @Test
    void getClassJars() {
        provider.get().tap {

        }
    }

    @Test
    void getOutputDirectory() {
        provider.get().tap {

        }
    }

    @Test
    void getOutputJar() {
        provider.get().tap {

        }
    }

}