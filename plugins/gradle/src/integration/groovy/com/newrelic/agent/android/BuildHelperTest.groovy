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
        Mockito.verify(buildHelper.logger, Mockito.times(2)).warn(Mockito.anyString())
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
        Mockito.verify(buildHelper.logger, Mockito.times(2)).warn(Mockito.anyString())
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
    void getReportedAGPVersion() {
        def ext = Mockito.spy(buildHelper.androidComponentsExtension)
        def pluginVersion = new AndroidPluginVersion(8, 1)

        Mockito.when(buildHelper.getAndroidComponentsExtension()).thenReturn(ext)
        Mockito.when(ext.getPluginVersion()).thenReturn(pluginVersion)
        Assert.assertEquals("8.1.0", buildHelper.getReportedAGPVersion())

        pluginVersion = new AndroidPluginVersion(1, 2, 3).rc(4).dev()
        Mockito.when(ext.getPluginVersion()).thenReturn(pluginVersion)
        Assert.assertEquals("1.2.3-dev0", buildHelper.getReportedAGPVersion())

        pluginVersion = new AndroidPluginVersion(4, 5, 6).alpha(7)
        Mockito.when(ext.getPluginVersion()).thenReturn(pluginVersion)
        Assert.assertEquals("4.5.6-alpha7", buildHelper.getReportedAGPVersion())
    }

    @Test
    void getAGPVersionAsSemver() {
        Assert.assertEquals("8.1.0", buildHelper.getAGPVersionAsSemVer("8.1.0-rc01"))

        def pluginVersion = new AndroidPluginVersion(1, 2, 3).toString()
                .replace("Android Gradle Plugin version ", "")
        Assert.assertEquals("1.2.3", buildHelper.getAGPVersionAsSemVer(pluginVersion))

        pluginVersion = new AndroidPluginVersion(1, 2, 3).alpha(4).toString()
                .replace("Android Gradle Plugin version ", "")
        Assert.assertEquals("1.2.3", buildHelper.getAGPVersionAsSemVer(pluginVersion))

        pluginVersion = new AndroidPluginVersion(1, 2, 3).beta(5).toString()
                .replace("Android Gradle Plugin version ", "")
        Assert.assertEquals("1.2.3", buildHelper.getAGPVersionAsSemVer(pluginVersion))

        pluginVersion = new AndroidPluginVersion(1, 2, 3).rc(6).toString()
                .replace("Android Gradle Plugin version ", "")
        Assert.assertEquals("1.2.3", buildHelper.getAGPVersionAsSemVer(pluginVersion))

        pluginVersion = new AndroidPluginVersion(1, 2, 3).dev().toString()
                .replace("Android Gradle Plugin version ", "")
        Assert.assertEquals("1.2.3", buildHelper.getAGPVersionAsSemVer(pluginVersion))
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
        Assert.assertFalse(buildHelper.configurationCacheEnabled())

        Mockito.when(project.providers.gradleProperty("org.gradle.configuration-cache")).thenReturn(true)
        Assert.assertTrue(buildHelper.configurationCacheEnabled())
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
    }

    @Test
    void warnOrHalt() {
        buildHelper.warnOrHalt("warn")
        Mockito.verify(buildHelper, Mockito.times(1)).warnOrHalt(Mockito.anyString())
        Mockito.verify(buildHelper.logger, Mockito.times(2)).warn(Mockito.anyString())

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
        Assert.assertEquals(7, metrics.size())
        Assert.assertTrue(metrics.containsKey("agent"))
        Assert.assertTrue(metrics.containsKey("agp"))
        Assert.assertTrue(metrics.containsKey("gradle"))
        Assert.assertTrue(metrics.containsKey("java"))
        Assert.assertTrue(metrics.containsKey("kotlin"))
        Assert.assertTrue(metrics.containsKey("variants"))
        Assert.assertTrue(metrics.containsKey("configCacheEnabled"))
    }

    @Test
    void buildMetricsAsJson() {
        def jsonStr = buildHelper.getBuildMetricsAsJson()
        Assert.assertNotNull(jsonStr)

        def jsonObj = new JsonSlurper().parseText(jsonStr)
        Assert.assertNotNull(jsonObj)
        Assert.assertEquals(7, jsonObj.size())
        Assert.assertTrue(jsonObj.containsKey("agent"))
        Assert.assertTrue(jsonObj.containsKey("agp"))
        Assert.assertTrue(jsonObj.containsKey("gradle"))
        Assert.assertTrue(jsonObj.containsKey("java"))
        Assert.assertTrue(jsonObj.containsKey("kotlin"))
        Assert.assertTrue(jsonObj.containsKey("configCacheEnabled"))
        Assert.assertTrue(jsonObj.containsKey("variants"))
        Assert.assertFalse(jsonObj.get("configCacheEnabled"))
    }

    @Test
    void checkDexGuard() {
        Assert.assertFalse(buildHelper.checkDexGuard())

        Mockito.when(project.plugins.hasPlugin("dexguard")).thenReturn(true)
        Assert.assertTrue(buildHelper.checkDexGuard())
    }

    @Test
    void checkApplication() {
        Assert.assertTrue(buildHelper.checkApplication())

        Mockito.when(project.plugins.hasPlugin("com.android.application")).thenReturn(false)
        Assert.assertFalse(buildHelper.checkDexGuard())
    }

    @Test
    void checkDynamicFeature() {
        Assert.assertFalse(buildHelper.checkDynamicFeature())

        Mockito.when(project.plugins.hasPlugin("com.android.dynamic-feature")).thenReturn(true)
        Assert.assertTrue(buildHelper.checkDynamicFeature())
    }

    @Test
    void checkLibrary() {
        Assert.assertFalse(buildHelper.checkLibrary())

        Mockito.when(project.plugins.hasPlugin("com.android.library")).thenReturn(true)
        Assert.assertTrue(buildHelper.checkLibrary())
    }

    @Test
    void getMapCompilerName() {
        Assert.assertEquals(buildHelper.getMapCompilerName(), Proguard.Provider.DEFAULT)

        buildHelper.dexguardHelper.enabled = true
        Assert.assertEquals(buildHelper.getMapCompilerName(), Proguard.Provider.DEXGUARD)

        buildHelper.dexguardHelper.enabled = false
        buildHelper.agpVersion = "3.2"
        Assert.assertEquals(buildHelper.getMapCompilerName(), Proguard.Provider.PROGUARD_603)
    }

    @Test
    void getTaskProvidersFromNames() {
        def providers = buildHelper.getTaskProvidersFromNames(Set.of("assemble", "check", "build"))

        Assert.assertEquals(3, providers.size())
        Assert.assertNotNull(providers.find { it.name == "assemble" })
        Assert.assertNotNull(providers.find { it.name == "check" })
        Assert.assertNotNull(providers.find { it.name == "build" })
        Assert.assertNull(providers.find { it.name == "clean" })
    }

    @Test
    void wireTaskProviderToDependencyNames() {
        buildHelper.wireTaskProviderToDependencyNames(Set.of("assemble", "check", "build")) {
            Assert.assertTrue(it.name == "assemble" || it.name == "check" || it.name == "build")
        }
    }

    @Test
    void wireTaskProviderToDependencies() {
        def providers = buildHelper.getTaskProvidersFromNames(Set.of("assemble", "check", "build"))
        buildHelper.wireTaskProviderToDependencies(providers) {
            Assert.assertTrue(it.name == "assemble" || it.name == "check" || it.name == "build")
            Assert.assertFalse(it.name == "clean")
        }
    }

    @Test
    void hasOptional() {
        Assert.assertEquals("value", buildHelper.hasOptional("key", "value"))
    }
}