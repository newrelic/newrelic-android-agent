/*
 * Copyright (c) 2023. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android


import org.junit.Assert
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito

class DexGuardHelperTest extends PluginTest {
    def buildHelper
    def dexGuardHelper

    @BeforeEach
    void setUp() {
        def plugins = Mockito.spy(project.plugins)

        project = Mockito.spy(project)
        Mockito.when(project.getPlugins()).thenReturn(plugins)
        Mockito.when(plugins.hasPlugin(DexGuardHelper.PLUGIN_EXTENSION_NAME)).thenReturn(true)

        buildHelper = Mockito.spy(plugin.buildHelper)
        Mockito.when(buildHelper.getGradleVersion()).thenReturn("7.6")
        Mockito.when(buildHelper.getProject()).thenReturn(project);

        dexGuardHelper = Mockito.spy(DexGuardHelper.register(buildHelper))
        Mockito.when(dexGuardHelper.getEnabled()).thenReturn(true)
        Mockito.when(dexGuardHelper.getCurrentVersion()). thenReturn(DexGuardHelper.minSupportedVersion)
    }

    @Test
    void isLegacyDexGuard() {
        Mockito.doReturn("8.3").when(dexGuardHelper).getCurrentVersion()
        Assert.assertFalse(dexGuardHelper.isDexGuard9())
    }

    @Test
    void isDexGuard9() {
        // defaults to 9.0
        Assert.assertTrue(dexGuardHelper.isDexGuard9())

        Mockito.doReturn("10.9.8").when(dexGuardHelper).getCurrentVersion()
        Assert.assertTrue(dexGuardHelper.isDexGuard9())

        Mockito.doReturn("8.9.10").when(dexGuardHelper).getCurrentVersion()
        Assert.assertFalse(dexGuardHelper.isDexGuard9())
    }

    @Test
    void getDefaultMapPath() {
        Assert.assertEquals(3, buildHelper.variantAdapter.getVariantValues().size())
        buildHelper.variantAdapter.getVariantValues().each { variant ->
            def mapPath = dexGuardHelper.getMappingFileProvider(variant.name)
            Assert.assertNotNull(mapPath)
            Assert.assertTrue(mapPath.getAsFile().get().getAbsolutePath().contains("/${variant.name}/"))
            if (dexGuardHelper.isDexGuard9()) {
                Assert.assertTrue(mapPath.getAsFile().get().getAbsolutePath().contains("/dexguard/"))
            }
        }
    }

    @Test
    void configureDexGuard() {
        dexGuardHelper.configureDexGuard()
        Assert.assertEquals(3, buildHelper.variantAdapter.getVariantValues().size())
        buildHelper.variantAdapter.getVariantValues().each { variant ->
            if (buildHelper.extension.shouldIncludeMapUpload(variant.name)) {
                Mockito.verify(dexGuardHelper, Mockito.times(1)).wireDexGuardMapProviders(variant.name)
            }
        }
    }

    @Test
    void wiredTaskNames() {
        Assert.assertEquals(3, buildHelper.variantAdapter.getVariantValues().size())
        buildHelper.variantAdapter.getVariantValues().each { variant ->
            def taskNames = dexGuardHelper.wiredTaskNames(variant.name)
            Assert.assertEquals(2, taskNames.size())
            Assert.assertTrue(taskNames.contains("bundle"))
            Assert.assertTrue(taskNames.contains("assemble"))
        }
    }

    @Test
    void getTaskProvidersFromNames() {
        Assert.assertEquals(3, buildHelper.variantAdapter.getVariantValues().size())
        buildHelper.variantAdapter.getVariantValues().each { variant ->
            def taskNames = dexGuardHelper.wiredTaskNames(variant.name)
            def tasks = buildHelper.wireTaskProviderToDependencyNames(taskNames)
            Assert.assertEquals(2, tasks.size())
            Assert.assertNotNull(tasks.find { it.name == "bundle" })
            Assert.assertNotNull(tasks.find { it.name == "assemble" })
        }
    }

    @Test
    void wireDexGuardMapProviders() {
        Assert.assertEquals(3, buildHelper.variantAdapter.getVariantValues().size())
        buildHelper.variantAdapter.getVariantValues().each { variant ->
            if (buildHelper.extension.shouldIncludeMapUpload(variant.name)) {
                Assert.assertNotNull(buildHelper.project.tasks.named("${NewRelicMapUploadTask.NAME}${variant.name.capitalize()}", NewRelicMapUploadTask.class))
            }
        }
    }

}