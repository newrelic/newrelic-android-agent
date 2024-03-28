/*
 * Copyright (c) 2023. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android

import org.junit.Assert
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class NewRelicMapUploadTaskTest extends PluginTest {
    def provider

    NewRelicMapUploadTaskTest() {
        super(true);
    }

    @BeforeEach
    void setup() {
        provider = plugin.buildHelper.variantAdapter.getMapUploadProvider("release").get()
    }

    @Test
    void getVariantName() {
        Assert.assertEquals("release", provider.getVariantName().get())
    }

    @Test
    void getProjectRoot() {
        Assert.assertEquals(project.layout.projectDirectory, provider.getProjectRoot().get())
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
    void getMappingFile() {
        def f = provider.getMappingFile().asFile
        Assert.assertFalse(f.get().absolutePath.isEmpty())
        def m = plugin.buildHelper.variantAdapter.getMappingFileProvider("release")
        Assert.assertEquals(f.get(), m.get().asFile)
    }

    @Test
    void getLogger() {
        Assert.assertEquals(provider.getLogger(), NewRelicGradlePlugin.LOGGER)
    }
}