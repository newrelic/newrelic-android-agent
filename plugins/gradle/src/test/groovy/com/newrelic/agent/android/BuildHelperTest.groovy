/*
 * Copyright (c) 2023. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android

import com.android.build.api.AndroidPluginVersion
import com.newrelic.agent.compile.HaltBuildException
import groovy.json.JsonSlurper
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.junit.Assert
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito

class BuildHelperTest extends PluginTest {

    BuildHelper buildHelper

    @BeforeEach
    void setUp() {
        buildHelper = Mockito.spy(BuildHelper.register(project))
        buildHelper.logger = Mockito.spy(buildHelper.logger)
        project.providers.gradleProperty("org.gradle.unsafe.configuration-cache")
    }

    @Test
    void validatePluginExtension() {
        try {
            Mockito.when(buildHelper.getAndroid()).thenReturn(null)
            buildHelper.validatePluginSettings()
            Assert.fail("AGP Plugin validation failed")
        } catch (RuntimeException e) {
            Assert.assertTrue(e instanceof HaltBuildException)
        }
    }

    @Test
    void validatePluginLowGradleVersion() {
        Mockito.when(buildHelper.getGradleVersion()).thenReturn("1.2.3")
        buildHelper.validatePluginSettings()
        Mockito.verify(buildHelper, Mockito.times(1)).warnOrHalt(Mockito.anyString())
        Mockito.verify(buildHelper.logger, Mockito.times(1)).warn(Mockito.anyString())
    }

    @Test
    void validatePluginLowAGPVersion() {
        try {
            Mockito.when(buildHelper.getAgpVersion()).thenReturn("3.3.0")
            buildHelper.validatePluginSettings()
            Assert.fail("AGP min version validation failed")
        } catch (RuntimeException e) {
            Assert.assertTrue(e instanceof HaltBuildException)
        }
    }

    @Test
    void validatePluginUnsupportedVersion() {
        Mockito.when(buildHelper.getAgpVersion()).thenReturn("99.8.7")
        buildHelper.validatePluginSettings()
        Mockito.verify(buildHelper, Mockito.times(1)).warnOrHalt(Mockito.anyString())
        Mockito.verify(buildHelper.logger, Mockito.times(1)).warn(Mockito.anyString())
    }

    @Test
    void validatePluginWithHaltOnWarning() {
        Mockito.when(buildHelper.hasOptional(Mockito.anyString(), Mockito.any(Object.class))).thenReturn(true)

        try {
            Mockito.when(buildHelper.getGradleVersion()).thenReturn("1.2.3")
            buildHelper.validatePluginSettings()
            Assert.fail("Gradle version validation failed with 'newrelic.halt-on-error' enabled")
        } catch (RuntimeException e) {
            Assert.assertTrue(e instanceof HaltBuildException)
            Mockito.verify(buildHelper, Mockito.times(1)).warnOrHalt(Mockito.anyString())
        }

        try {
            Mockito.when(buildHelper.getAgpVersion()).thenReturn("99.8.7")
            buildHelper.validatePluginSettings()
            Assert.fail("AGP max version validation failed with 'newrelic.halt-on-error' enabled")
        } catch (RuntimeException e) {
            Assert.assertTrue(e instanceof HaltBuildException)
            Mockito.verify(buildHelper, Mockito.times(2)).warnOrHalt(Mockito.anyString())
        }
    }

    @Test
    void getAGPVersion() {
        def ext = Mockito.spy(buildHelper.androidComponents)
        def pluginVersion = new AndroidPluginVersion(8, 1)

        Mockito.when(buildHelper.getAndroidComponents()).thenReturn(ext)
        Mockito.when(ext.getPluginVersion()).thenReturn(pluginVersion)
        Assert.assertEquals("8.1.0", buildHelper.getAndNormalizeAGPVersion())

        pluginVersion = new AndroidPluginVersion(1, 2, 3).rc(4).dev()
        Mockito.when(ext.getPluginVersion()).thenReturn(pluginVersion)
        Assert.assertEquals("1.2.3", buildHelper.getAndNormalizeAGPVersion())

        pluginVersion = new AndroidPluginVersion(4, 5, 6).alpha(7)
        Mockito.when(ext.getPluginVersion()).thenReturn(pluginVersion)
        Assert.assertEquals("4.5.6", buildHelper.getAndNormalizeAGPVersion())
    }

    @Test
    void getCompileTaskProvider() {
        buildHelper.variantAdapter.variants.get().values().each { variant ->
            def provider = buildHelper.variantAdapter.getJavaCompileProvider(variant.name)
            Assert.assertTrue(provider instanceof TaskProvider)
            // TODO
        }
    }

    @Test
    void getDefaultMapPathProvider() {
        buildHelper.variantAdapter.variants.get().values().each { variant ->
            def provider = buildHelper.variantAdapter.getMapUploadProvider(variant.name)
            Assert.assertTrue(provider instanceof TaskProvider)
            // TODO
        }
    }

    @Test
    void getBuildConfigTask() {
        buildHelper.variantAdapter.variants.get().values().each { variant ->
            def provider = buildHelper.variantAdapter.getBuildConfigProvider(variant.name)
            Assert.assertTrue(provider instanceof TaskProvider)
            // TODO
        }
    }

    @Test
    void getMappingFile() {
        buildHelper.variantAdapter.variants.get().values().each { variant ->
            def provider = buildHelper.variantAdapter.getMappingFile(variant.name)
            Assert.assertTrue(provider instanceof Provider<File>)
            // TODO
        }
    }

    @Test
    void withDexGuardHelper() {
        def dexguardHelper = DexGuardHelper.register(project)
        buildHelper.withDexGuardHelper(dexguardHelper)
        Assert.assertEquals(dexguardHelper, buildHelper.dexguardHelper)
    }

    @Test
    void configurationCacheSupported() {
        Mockito.when(buildHelper.getGradleVersion()).thenReturn(BuildHelper.minSupportedGradleConfigCacheVersion)
        Assert.assertTrue(buildHelper.configurationCacheSupported())

        Mockito.when(buildHelper.getGradleVersion()).thenReturn("1.2.3")
        Assert.assertFalse(buildHelper.configurationCacheSupported())
    }

    @Test
    void configurationCacheEnabled() {
        // TODO
    }

    @Test
    void getSystemPropertyProvider() {
        Assert.assertEquals(BuildHelper.NEWLN, buildHelper.getSystemPropertyProvider("line.separator").get())
    }

    @Test
    void shouldApplyLegacyTransform() {
        // TODO
    }

    @Test
    void shouldApplyArtifactsAPI() {
        // TODO
    }

    @Test
    void warnOrHalt() {
        buildHelper.warnOrHalt("warn")
        Mockito.verify(buildHelper, Mockito.times(1)).warnOrHalt(Mockito.anyString())
        Mockito.verify(buildHelper.logger, Mockito.times(1)).warn(Mockito.anyString())

        try {
            Mockito.when(buildHelper.hasOptional(Mockito.anyString(), Mockito.any(Object.class))).thenReturn(true)
            buildHelper.warnOrHalt("halt")
            Assert.fail("warnOrHalt failed with 'newrelic.halt-on-error' enabled")
        } catch (RuntimeException e) {
            Assert.assertTrue(e instanceof HaltBuildException)
            Mockito.verify(buildHelper, Mockito.times(2)).warnOrHalt(Mockito.anyString())
        }
    }

    @Test
    void buildMetrics() {
        def metrics = buildHelper.buildMetrics()
        Assert.assertEquals(6, metrics.size())
        Assert.assertTrue(metrics.containsKey("agent"))
        Assert.assertTrue(metrics.containsKey("agp"))
        Assert.assertTrue(metrics.containsKey("gradle"))
        Assert.assertTrue(metrics.containsKey("java"))
        Assert.assertTrue(metrics.containsKey("dexguard"))
        Assert.assertTrue(metrics.containsKey("configCache"))
    }

    @Test
    void buildMetricsAsJson() {
        def jsonStr = buildHelper.buildMetricsAsJson()
        Assert.assertNotNull(jsonStr)

        def jsonObj = new JsonSlurper().parseText(jsonStr)
        Assert.assertNotNull(jsonObj)
        Assert.assertEquals(6, jsonObj.size())
        Assert.assertTrue(jsonObj.containsKey("agent"))
        Assert.assertTrue(jsonObj.containsKey("agp"))
        Assert.assertTrue(jsonObj.containsKey("gradle"))
        Assert.assertTrue(jsonObj.containsKey("java"))
        Assert.assertTrue(jsonObj.containsKey("dexguard"))
        Assert.assertTrue(jsonObj.containsKey("configCache"))

        def dexguard = jsonObj.get("dexguard")
        Assert.assertEquals(2, dexguard.size())
        Assert.assertTrue(dexguard.containsKey("enabled"))
        Assert.assertTrue(dexguard.containsKey("version"))

        def configCache = jsonObj.get("configCache")
        Assert.assertEquals(2, configCache.size())
        Assert.assertTrue(configCache.containsKey("supported"))
        Assert.assertTrue(configCache.containsKey("enabled"))
    }
}