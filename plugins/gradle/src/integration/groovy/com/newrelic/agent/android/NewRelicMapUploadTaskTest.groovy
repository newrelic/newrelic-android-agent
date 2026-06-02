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

    @Test
    void getTaggedMappingFile() {
        def taggedFile = provider.getTaggedMappingFile()
        def expectedPath = project.layout.buildDirectory.file("outputs/newrelic/release/mapping.txt").get().asFile

        Assert.assertEquals(expectedPath.absolutePath, taggedFile.absolutePath)
        Assert.assertTrue("Tagged file path should contain variant name",
                          taggedFile.absolutePath.contains("release"))
        Assert.assertTrue("Tagged file should be separate from input",
                          taggedFile != provider.getMappingFile().asFile.get())
    }

    @Test
    void getTaggedMappingFileWithCompoundVariantName() {
        // Test compound variant names (flavor + build type) like "googleQa", "amazonRelease", etc.
        def compoundProvider = plugin.buildHelper.variantAdapter.registerOrNamed(
                "testMapUploadGoogleQa", NewRelicMapUploadTask) { task ->
            task.variantName.set("googleQa")
            task.buildId.set("test-build-id")
            task.mapProvider.set("r8")
            task.projectRoot.set(project.layout.projectDirectory)
        }.get()

        def taggedFile = compoundProvider.getTaggedMappingFile()
        def expectedPath = project.layout.buildDirectory.file("outputs/newrelic/googleQa/mapping.txt").get().asFile

        Assert.assertEquals("Compound variant names should work correctly",
                          expectedPath.absolutePath, taggedFile.absolutePath)
        Assert.assertTrue("Tagged file path should contain compound variant name",
                          taggedFile.absolutePath.contains("googleQa"))
        Assert.assertTrue("Output path should be isolated per variant",
                          !taggedFile.absolutePath.contains("release"))
    }
}