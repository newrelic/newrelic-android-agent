/*
 * Copyright (c) 2023. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android

import com.newrelic.agent.android.agp4.AGP4Adapter
import com.newrelic.agent.android.agp7.AGP70Adapter
import com.newrelic.agent.android.agp7.AGP74Adapter
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.compile.JavaCompile
import org.junit.Assert
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito

import static org.mockito.ArgumentMatchers.anyString

class VariantAdapterTest extends PluginTest {
    BuildHelper buildHelper
    VariantAdapter variantAdapter
    NewRelicExtension ext

    VariantAdapterTest() {
        super(true)
    }

    @BeforeEach
    void setup() {
        ext = Mockito.spy(plugin.pluginExtension)
        buildHelper = Mockito.spy(plugin.buildHelper)
        variantAdapter = Mockito.spy(buildHelper.variantAdapter)
    }

    @Test
    void getVariants() {
        variantAdapter.configure(ext)
        Assert.assertFalse(variantAdapter.getVariantValues().isEmpty())
    }

    @Test
    void getVariantNames() {
        Assert.assertFalse(variantAdapter.getVariantNames().isEmpty())
        Assert.assertEquals(variantAdapter.getVariantNames(), variantAdapter.variants.get().keySet())
    }

    @Test
    void configure() {
        variantAdapter.buildHelper.extension.excludeVariantInstrumentation("release", "staging")

        Assert.assertEquals(variantAdapter, variantAdapter.configure(buildHelper.extension))
        Assert.assertNotNull(variantAdapter.withVariant("release"))
        Assert.assertNotNull(variantAdapter.withVariant("debug"))
        Assert.assertNotNull(variantAdapter.withVariant("qa"))

        Mockito.verify(variantAdapter, Mockito.times(2)).getTransformProvider(anyString())
    }

    @Test
    void getBuildTypeProvider() {
        variantAdapter.getVariantValues().each { variant ->
            Assert.assertEquals(variant.name, variantAdapter.getBuildTypeProvider(variant.name).get().name)
        }
    }

    @Test
    void getBuildTypeAsString() {
        variantAdapter.getVariantValues().each { variant ->
            Assert.assertEquals(variant.name, variantAdapter.getBuildTypeProvider(variant.name).get().name)
        }
    }

    @Test
    void getJavaCompileProvider() {
        variantAdapter.getVariantValues().each { variant ->
            def provider = variantAdapter.getJavaCompileProvider(variant.name)
            Assert.assertNotNull(provider)
            Assert.assertEquals(JavaCompile, provider.type)
        }
    }

    @Test
    void getConfigProvider() {
        variantAdapter.getVariantValues().each { variant ->
            def provider = variantAdapter.getConfigProvider(variant.name)
            Assert.assertNotNull(provider)
            Assert.assertEquals(NewRelicConfigTask, provider.type)
        }
    }

    @Test
    void getMappingFile() {
        variantAdapter.getVariantValues().each { variant ->
            if (variantAdapter.buildTypes.getting(variant.name).get().minified) {
                def provider = variantAdapter.getMappingFileProvider(variant.name)
                Assert.assertTrue(provider instanceof RegularFileProperty)
                Assert.assertTrue(provider.get().asFile.absolutePath.startsWith(project.layout.buildDirectory.asFile.get().absolutePath))
            }
        }
    }

    @Test
    void withVariant() {
        Assert.assertFalse(variantAdapter.variantValues.isEmpty())
        variantAdapter.getVariantNames().each { variantName ->
            Assert.assertNotNull(variantAdapter.withVariant(variantName))
        }
    }

    @Test
    void register() {
        Assert.assertFalse(variantAdapter.variantValues.isEmpty())
    }

    @Test
    void registerOrNamed() {
        def registered = variantAdapter.getConfigProvider("release")
        Assert.assertNotNull(registered)
        Assert.assertEquals(NewRelicConfigTask, registered.type)

        def named = variantAdapter.getConfigProvider("release")
        Assert.assertNotNull(named)
        Assert.assertEquals(NewRelicConfigTask, named.type)

        Assert.assertEquals(registered, named)
    }

    @Test
    void getLogger() {
        Assert.assertEquals(buildHelper.logger, NewRelicGradlePlugin.LOGGER)
    }

    @Test
    void getVariantAdapterByGradleVersion() {
        Assert.assertTrue(variantAdapter instanceof AGP74Adapter)

        buildHelper = Mockito.spy(plugin.buildHelper)
        Mockito.doReturn("7.2").when(buildHelper).getAgpVersion()
        Mockito.doReturn("7.3.3").when(buildHelper).getGradleVersion()
        def adapter = VariantAdapter.register(buildHelper)
        Assert.assertTrue(adapter instanceof AGP4Adapter)
    }

    @Test
    void getVariantAdapterByAGPVersion() {
        buildHelper = Mockito.spy(plugin.buildHelper)
        Mockito.doReturn("7.3.3").when(buildHelper).getAgpVersion()
        def adapter = VariantAdapter.register(buildHelper)
        Assert.assertTrue(adapter instanceof AGP4Adapter)
    }

}