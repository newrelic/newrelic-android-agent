/*
 * Copyright (c) 2023. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android

import com.newrelic.agent.android.obfuscation.Proguard
import com.newrelic.agent.util.BuildId
import org.junit.Assert
import org.junit.Ignore
import org.junit.jupiter.api.Test

@Ignore
class NewRelicGradlePluginTest extends PluginTest {

    @Test
    void getDefaultBuildMap() {
        def buildIdMap = plugin.getDefaultBuildMap() as HashMap<String, String>
        Assert.assertTrue(plugin.pluginExtension.variantMapsEnabled.get())
        Assert.assertTrue(BuildId.variantMapsEnabled)
        Assert.assertEquals(plugin.buildHelper.variantAdapter.getVariantNames().size(), buildIdMap.size())
        buildIdMap.keySet().each() { variantName ->
            Assert.assertNotNull(plugin.buildHelper.variantAdapter.withVariant(variantName))
        }
    }

    @Test
    void getMapProvider() {
        Assert.assertEquals(plugin.buildHelper.getMapCompilerName(), Proguard.Provider.DEFAULT)

        plugin.buildHelper.dexguardHelper.enabled = true
        Assert.assertEquals(plugin.buildHelper.getMapCompilerName(), Proguard.Provider.DEXGUARD)

        plugin.buildHelper.dexguardHelper.enabled = false
        plugin.buildHelper.agpVersion = "3.2"
        Assert.assertEquals(plugin.buildHelper.getMapCompilerName(), Proguard.Provider.PROGUARD_603)
    }

}