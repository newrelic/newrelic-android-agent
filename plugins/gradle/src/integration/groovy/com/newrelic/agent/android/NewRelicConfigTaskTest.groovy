/*
 * Copyright (c) 2023. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android


import org.junit.Assert
import org.junit.Ignore
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class NewRelicConfigTaskTest extends PluginTest {

    NewRelicExtension ext
    BuildHelper buildHelper
    VariantAdapter variantAdapter
    def provider

    NewRelicConfigTaskTest() {
        super(true)
    }

    @BeforeEach
    void setup() {
        // Create the instances needed to test this class
        ext = NewRelicExtension.register(project)
        buildHelper = BuildHelper.register(project)
        variantAdapter = buildHelper.variantAdapter
        variantAdapter.configure(ext)

        provider = buildHelper.variantAdapter.getConfigProvider("release")
    }

    @Test
    void getBuildId() {
        def task = provider.get().tap {
            Assert.assertFalse(getBuildId().get().isEmpty())
            Assert.assertFalse(UUID.fromString(getBuildId().get()).toString().isEmpty())
        }
    }

    @Test
    void getMapProvider() {
        def task = provider.get().tap {
            Assert.assertEquals("r8", getMapProvider().get().toLowerCase())
        }
    }

    @Test
    void getMinifyEnabled() {
        def task = provider.get().tap {
            Assert.assertTrue(getMinifyEnabled().get())
        }
    }

    @Test
    void getBuildMetrics() {
        def task = provider.get().tap {
            Assert.assertFalse(getBuildMetrics().get().toString().isEmpty())
        }
    }

    @Test
    void getSourceOutputDir() {
        provider.get().tap {
            def f = getSourceOutputDir().file(CONFIG_CLASS).get().asFile
            Assert.assertTrue(f.absolutePath.endsWith(NewRelicConfigTask.CONFIG_CLASS))
        }
    }

    @Test
    void newRelicConfigTask() {
        provider.configure {
            // @TaskAction
            newrelicConfigTask()
        }
    }

    @Test
    void getLogger() {
        Assert.assertEquals(provider.get().logger, NewRelicGradlePlugin.LOGGER)
    }
}