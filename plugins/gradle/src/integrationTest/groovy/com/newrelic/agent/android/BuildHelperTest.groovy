/*
 * Copyright (c) 2023. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android

import com.android.build.api.AndroidPluginVersion
import com.newrelic.agent.android.obfuscation.Proguard
import groovy.json.JsonSlurper
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.compile.JavaCompile
import org.junit.Assert
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito

class BuildHelperTest extends PluginTest {

    BuildHelper buildHelper

    BuildHelperTest() {
        super(true)
    }

    @BeforeEach
    void setUp() {
        project.getPlugins().with {
            agp = apply("com.android.application")
            if (applyPlugin) {
                plugin = apply("newrelic")
            }
        }

        def ext = NewRelicExtension.register(project)

        buildHelper = Mockito.spy(BuildHelper.register(project))
        buildHelper.logger = Mockito.spy(buildHelper.logger)
    }

    @Test
    void validatePluginExtension() {
        try {
            Mockito.when(buildHelper.getAndroidExtension()).thenReturn(null)
            buildHelper.validatePluginSettings()
            Assert.fail("AGP Plugin validation failed")
        } catch (RuntimeException e) {
            Assert.assertTrue(e instanceof GradleException)
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
            Assert.assertTrue(e instanceof GradleException)
        }
    }

    @Test
    void validatePluginUnsupportedVersion() {
        Mockito.when(buildHelper.getAgpVersion()).thenReturn("99.88.77")
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
            Assert.assertTrue(e instanceof GradleException)
            Mockito.verify(buildHelper, Mockito.times(1)).warnOrHalt(Mockito.anyString())
        }

        try {
            Mockito.when(buildHelper.getAgpVersion()).thenReturn("99.88.77")
            buildHelper.validatePluginSettings()
            Assert.fail("AGP max version validation failed with 'newrelic.halt-on-error' enabled")
        } catch (RuntimeException e) {
            Assert.assertTrue(e instanceof GradleException)
            Mockito.verify(buildHelper, Mockito.times(2)).warnOrHalt(Mockito.anyString())
        }
    }

    @Test
    void getAGPVersion() {
        def ext = Mockito.spy(buildHelper.androidComponentsExtension)
        def pluginVersion = new AndroidPluginVersion(8, 1)

        Mockito.when(buildHelper.getAndroidComponentsExtension()).thenReturn(ext)
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
        buildHelper.variantAdapter.getVariantValues().each { variant ->
            def provider = project.tasks.named("compile${variant.name.capitalize()}JavaWithJavac")
            Assert.assertNotNull(provider)
            Assert.assertEquals(JavaCompile, provider.type)
        }
    }

    @Test
    void getDefaultMapUploadProvider() {
        buildHelper.variantAdapter.getVariantValues().each { variant ->
            def provider = project.tasks.named("${NewRelicMapUploadTask.NAME}${variant.name.capitalize()}")
            Assert.assertEquals(NewRelicMapUploadTask, provider.type)
        }
    }

    @Test
    void getConfigTask() {
        buildHelper.variantAdapter.getVariantValues().each { variant ->
            def provider = project.tasks.named("${NewRelicConfigTask.NAME}${variant.name.capitalize()}")
            Assert.assertNotNull(provider)
            Assert.assertEquals(NewRelicConfigTask, provider.type)
        }
    }

    @Test
    void getMappingFile() {
        buildHelper.variantAdapter.getVariantValues().each { variant ->
            def provider = buildHelper.variantAdapter.getMappingFileProvider(variant.name)
            Assert.assertTrue(provider instanceof RegularFileProperty)
            // TODO
        }
    }

    @Test
    void withDexGuardHelper() {
        def dexguardHelper = DexGuardHelper.register(buildHelper)
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
    void isUsingLegacyTransform() {
        buildHelper = Mockito.spy(new BuildHelper(project))
        Mockito.when(buildHelper.getGradleVersion()).thenReturn("6.7.1")
        buildHelper.variantAdapter = VariantAdapter.register(buildHelper)
        Assert.assertTrue(buildHelper.isUsingLegacyTransform())
    }

    @Test
    void isUsing7xTransform() {
        buildHelper = Mockito.spy(new BuildHelper(project))
        Mockito.when(buildHelper.getGradleVersion()).thenReturn("7.999")

        def ext = Mockito.spy(buildHelper.androidComponentsExtension)
        def waah = buildHelper.androidComponentsExtension.selector().withName("waah")
        Mockito.when(buildHelper.getAndroidComponentsExtension()).thenReturn(ext)
        Mockito.when(ext.selector()).thenReturn(waah)

        // FIXME buildHelper.variantAdapter = VariantAdapter.register(buildHelper)
        // FIXME Assert.assertFalse(buildHelper.isUsingLegacyTransform())
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
            Assert.assertTrue(e instanceof GradleException)
            Mockito.verify(buildHelper, Mockito.times(2)).warnOrHalt(Mockito.anyString())
        }
    }

    @Test
    void buildMetrics() {
        def metrics = buildHelper.getBuildMetrics()
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
        def jsonStr = buildHelper.getBuildMetricsAsJson()
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

    @Test
    void injectMapUploadFinalizer() {
        // FIXME
    }

    @Test
    void testInjectMapUploadFinalizer() {
        // FIXME
    }

    @Test
    void configureTransformTasks() {
        // FIXME
    }

    @Test
    void configureMapUploadTasks() {
        // FIXME
    }

    @Test
    void configureConfigTasks() {
        // FIXME
    }

    @Test
    void getMapProvider() {
        Assert.assertEquals(buildHelper.getMapCompilerName(), Proguard.Provider.DEFAULT)

        buildHelper.dexguardHelper.enabled = true
        Assert.assertEquals(buildHelper.getMapCompilerName(), Proguard.Provider.DEXGUARD)

        buildHelper.dexguardHelper.enabled = false
        buildHelper.agpVersion = "3.2"
        Assert.assertEquals(buildHelper.getMapCompilerName(), Proguard.Provider.PROGUARD_603)
    }
}