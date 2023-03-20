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
    def dexGuardHelper

    @BeforeEach
    void setUp() {
        def plugins = Mockito.spy(project.getPlugins())

        project = Mockito.spy(project)
        Mockito.when(plugins.hasPlugin(DexGuardHelper.PLUGIN_EXTENSION_NAME)).thenReturn(true)
        Mockito.when(project.getPlugins()).thenReturn(plugins)

        dexGuardHelper = Mockito.spy(DexGuardHelper.register(project))
        Mockito.doReturn(true).when(dexGuardHelper).getEnabled()
        Mockito.doReturn(DexGuardHelper.minSupportedVersion).when(dexGuardHelper).getCurrentVersion()
    }

    @Test
    void isLegacyDexGuard() {
        Mockito.doReturn(DexGuardHelper.minSupportedVersion).when(dexGuardHelper).getCurrentVersion()
        Assert.assertTrue(dexGuardHelper.isLegacyDexGuard())
    }

    @Test
    void isDexGuard9() {
        Mockito.doReturn("10.9.8").when(dexGuardHelper).getCurrentVersion()
        Assert.assertTrue(dexGuardHelper.isDexGuard9())
    }

    @Test
    void getDefaultMapPath() {
        def buildHelper = BuildHelper.register(project)
        buildHelper.variantAdapter.configure(buildHelper.variantAdapter.plugin)
        buildHelper.variantAdapter.variants.get().values().each { variant ->
            File mapPath = dexGuardHelper.getDefaultMapPath(variant)
            Assert.assertNotNull(mapPath)
            Assert.assertTrue(mapPath.absolutePath.contains("/${variant.dirName}/"))
            if( dexGuardHelper.isDexGuard9()) {
                Assert.assertTrue(mapPath.absolutePath.contains("/dexguard/"))
            }
        }
    }
}