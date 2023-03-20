/*
 * Copyright (c) 2023. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android


import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.junit.Assert
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class VariantAdapterTest extends PluginTest {

    VariantAdapter variantAdapter

    @BeforeEach
    void setup() {
        variantAdapter = plugin.buildHelper.variantAdapter
    }

    @Test
    void getVariantNames() {
        Assert.assertFalse(variantAdapter.getVariantNames().get().empty)
    }

    @Test
    void configure() {
        NewRelicExtension ext  = project.extensions.getByName(NewRelicGradlePlugin.PLUGIN_EXTENSION_NAME)

        ext.excludeVariantInstrumentation("release", "staging")
        Assert.assertEquals(variantAdapter, variantAdapter.configure(ext))
        Assert.assertNull(variantAdapter.withVariant("release"))
        Assert.assertNotNull(variantAdapter.withVariant("debug"))
        Assert.assertNotNull(variantAdapter.withVariant("qa"))
    }

    @Test
    void getBuildTypeAsString() {
        variantAdapter.variants.get().values().each { variant ->
            Assert.assertEquals(variant.name, variantAdapter.getBuildType(variant.name).get().name)
        }
        Assert.assertNull(variantAdapter.getBuildType("debug"))
    }

    @Test
    void getJavaCompileProvider() {
        variantAdapter.variants.get().values().each { variant ->
            def provider = variantAdapter.getJavaCompileProvider(variant.name)
            Assert.assertTrue(provider instanceof TaskProvider)
        }
    }

    @Test
    void getBuildConfigProvider() {
        variantAdapter.variants.get().values().each { variant ->
            def provider = variantAdapter.getBuildConfigProvider(variant.name)
            Assert.assertTrue(provider instanceof TaskProvider)
        }
    }

    @Test
    void getMappingProvider() {
        // FIXME
        // Assert.assertEquals(variantAdapter.getMappingProvider("release").get(), Proguard.Provider.DEFAULT)
    }

    @Test
    void getMappingFile() {
        variantAdapter.variants.get().values().each { variant ->
            def provider = variantAdapter.getMappingFile(variant.name)
            Assert.assertTrue(provider instanceof Provider<File>)
        }
    }

    @Test
    void withVariant() {
        variantAdapter.variants.get().values().each { variant ->
            Assert.assertNotNull(variantAdapter.withVariant(variant.name))
        }
        Assert.assertNull(variantAdapter.withVariant("debug"))
    }

    @Test
    void register() {
        Assert.assertNotNull(VariantAdapter.register(plugin.buildHelper))
    }

    @Test
    void getLogger() {
        Assert.assertEquals(plugin.buildHelper.logger, NewRelicGradlePlugin.LOGGER)
    }
}