/*
 * Copyright (c) 2023. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android

import com.newrelic.agent.util.BuildId
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
        // The plain test app has no React Native, so the plugin's assembleDataModel()
        // never wires this task for it (guarded by shouldUploadReactNativeSourceMap).
        // The full wiredWithReactNativeSourceMapUploadProvider() also can't run here:
        // PluginTest fully evaluates the project, so its afterEvaluate {} bundle-task
        // hook would throw. Configure the task's properties directly via the provider
        // action — mirroring the values the real wiring sets — so the property getters
        // can be asserted.
        def variantAdapter = plugin.buildHelper.variantAdapter
        provider = variantAdapter.getReactNativeSourceMapUploadProvider("release") { task ->
            task.projectRoot.set(project.layout.projectDirectory)
            task.variantName.set("release")
            task.buildId.convention(BuildId.getBuildId("release"))
            task.appVersionId.set(variantAdapter.getAppVersionName())
            task.sourceMapFile.set(variantAdapter.getReactNativeSourceMapPath("release"))
        }.get()
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