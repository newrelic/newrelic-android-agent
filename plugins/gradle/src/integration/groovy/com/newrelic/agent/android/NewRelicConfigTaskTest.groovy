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
    void getResourceOutputDir() {
        def f = provider.getResourceOutputDir().file(NewRelicConfigTask.CONFIG_RESOURCE_FILE).get().asFile
        Assert.assertTrue(f.absolutePath.endsWith(NewRelicConfigTask.CONFIG_RESOURCE_FILE))

        provider.newRelicConfigTask()
        Assert.assertTrue(f.exists())
    }

    @Test
    void verifyBuildIdResource() {
        provider.newRelicConfigTask()

        def resourceFile = provider.getResourceOutputDir().file(NewRelicConfigTask.CONFIG_RESOURCE_FILE).get().asFile
        def buildId = provider.getBuildId().get()

        Assert.assertTrue(resourceFile.exists())
        Assert.assertTrue(resourceFile.text.contains(buildId))
        Assert.assertTrue(resourceFile.text.contains(NewRelicConfigTask.BUILD_ID_RESOURCE_NAME))
    }

    @Test
    void verifyBuildIdNotInCompiledSource() {
        provider.newRelicConfigTask()

        def sourceFile = provider.getSourceOutputDir().file(provider.CONFIG_CLASS).get().asFile
        def buildId = provider.getBuildId().get()

        Assert.assertFalse(sourceFile.text.contains(buildId))
        Assert.assertTrue(sourceFile.text.contains("BUILD_ID = \"${NewRelicConfigTask.BUILD_ID_PLACEHOLDER}\""))
    }

    @Test
    void verifyMetricsResource() {
        provider.newRelicConfigTask()

        def resourceFile = provider.getResourceOutputDir().file(NewRelicConfigTask.CONFIG_RESOURCE_FILE).get().asFile
        def metrics = provider.getBuildMetrics().get()

        Assert.assertTrue(resourceFile.exists())
        Assert.assertTrue(resourceFile.text.contains(metrics))
        Assert.assertTrue(resourceFile.text.contains(NewRelicConfigTask.METRICS_RESOURCE_NAME))
    }

    @Test
    void verifyMetricsNotInCompiledSource() {
        provider.newRelicConfigTask()

        def sourceFile = provider.getSourceOutputDir().file(provider.CONFIG_CLASS).get().asFile
        def metrics = provider.getBuildMetrics().get()

        Assert.assertFalse(sourceFile.text.contains(metrics))
        Assert.assertTrue(sourceFile.text.contains("METRICS = \"${NewRelicConfigTask.METRICS_PLACEHOLDER}\""))
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