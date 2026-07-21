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
    def provider

    NewRelicConfigTaskTest() {
        super(true)
    }

    @BeforeEach
    void setup() {
        provider = plugin.buildHelper.variantAdapter.getConfigProvider("release").get()
    }

    @Test
    void getBuildId() {
        def buildId = provider.getBuildId().get()
        Assert.assertFalse(buildId.isEmpty())
        Assert.assertFalse(UUID.fromString(buildId).toString().isEmpty())
    }

    @Test
    void getMapProvider() {
        Assert.assertEquals("r8", provider.getMapProvider().get().toLowerCase())
    }

    @Test
    void getMinifyEnabled() {
        Assert.assertTrue(provider.getMinifyEnabled().get())
    }

    @Test
    void getBuildMetrics() {
        Assert.assertFalse(provider.getBuildMetrics().get().toString().isEmpty())
    }

    @Test
    void getSourceOutputDir() {
        def f = provider.getSourceOutputDir().file(provider.CONFIG_CLASS).get().asFile
        Assert.assertTrue(f.absolutePath.endsWith(NewRelicConfigTask.CONFIG_CLASS))

        provider.newRelicConfigTask()
        Assert.assertTrue(f.exists())
    }

    @Test
    void newRelicConfigTask() {
        // @TaskAction
        provider.newRelicConfigTask()
    }

    @Test
    void getLogger() {
        Assert.assertEquals(provider.logger, NewRelicGradlePlugin.LOGGER)
    }

    @Test
    void verifyMetadata() {
        def f = provider.getSourceOutputDir().file(provider.METADATA).get().asFile
        Assert.assertTrue(f.absolutePath.endsWith(NewRelicConfigTask.METADATA))

        provider.newRelicConfigTask()
        Assert.assertTrue(f.exists())

        def buildId = provider.getBuildId().get()
        Assert.assertTrue(f.text.contains(buildId))
    }
}