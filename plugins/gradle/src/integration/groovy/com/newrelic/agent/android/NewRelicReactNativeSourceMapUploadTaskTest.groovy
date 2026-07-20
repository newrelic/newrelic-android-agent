/*
 * Copyright (c) 2023. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android

import org.junit.Assert
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class NewRelicReactNativeSourceMapUploadTaskTest extends PluginTest {
    def provider

    NewRelicReactNativeSourceMapUploadTaskTest() {
        super(true)
    }

    @BeforeEach
    void setup() {
        provider = plugin.buildHelper.variantAdapter.getReactNativeSourceMapUploadProvider("release").get()
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
    void getAppVersionId() {
        def appVersion = provider.getAppVersionId().get()
        Assert.assertNotNull(appVersion)
        // Default version from test build.gradle or fallback
        Assert.assertFalse(appVersion.isEmpty())
    }

    @Test
    void getSourceMapFile() {
        def sourceMapFile = provider.getSourceMapFile()
        // Source map file provider should be set (though file may not exist in test)
        Assert.assertNotNull(sourceMapFile)
    }

    @Test
    void getLogger() {
        Assert.assertEquals(provider.getLogger(), NewRelicGradlePlugin.LOGGER)
    }

    @Test
    void wiredTaskNames() {
        def taskNames = NewRelicReactNativeSourceMapUploadTask.wiredTaskNames("Release")
        Assert.assertTrue(taskNames.contains("bundleReleaseJsAndAssets"))
        Assert.assertTrue(taskNames.contains("createBundleReleaseJsAndAssets"))
    }

    @Test
    void taskName() {
        Assert.assertEquals("newrelicReactNativeSourceMapUpload", NewRelicReactNativeSourceMapUploadTask.NAME)
    }
}