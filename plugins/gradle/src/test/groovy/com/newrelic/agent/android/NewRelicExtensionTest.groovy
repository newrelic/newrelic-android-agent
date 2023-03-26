/*
 * Copyright (c) 2023. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android

import org.junit.Assert
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class NewRelicExtensionTest extends PluginTest {
    NewRelicExtension ext

    NewRelicExtensionTest() {
        super(false)
    }

    @BeforeEach
    void setUp() {
        ext = NewRelicExtension.register(project)
        ext.variantConfigurations {
            ["larry", "moe", "curly"].each { name ->
                def conf = project.objects.newInstance(VariantConfiguration.class, name, project).tap() {
                    instrument = name.length() > 3
                    uploadMappingFile = name.endsWith("y")
                    mappingFile = project.layout.buildDirectory.file("outputs/mapping/${name}/mapping.txt")
                }
                add(conf)
            }
        }
    }

    @Test
    void register() {
        Assert.assertTrue(ext instanceof NewRelicExtension)
    }

    @Test
    void getEnabled() {
        Assert.assertTrue(ext.enabled)
    }

    @Test
    void variantConfigurations() {
        Assert.assertNotNull(ext.variantConfigurations)
        Assert.assertFalse(ext.variantConfigurations.isEmpty())
    }

    @Test
    void uploadMapsForVariant() {
        ext.uploadMapsForVariant("qa")
        Assert.assertFalse(ext.shouldIncludeMapUpload("curly"))
    }

    @Test
    void excludeVariantInstrumentation() {
        ext.uploadMapsForVariant("qa", "debug")
    }

    @Test
    void excludePackageInstrumentation() {
        ext.excludePackageInstrumentation("com.newrelic", "com.android")
        Assert.assertEquals(2, ext.packageExclusionList.size())
    }

    @Test
    void shouldExcludeVariant() {
        ext.excludeVariantInstrumentation("release")
        Assert.assertTrue(ext.shouldExcludeVariant("release"))
    }

    @Test
    void shouldIncludeVariant() {
        Assert.assertTrue(ext.shouldIncludeVariant("larry"))

        ext.excludeVariantInstrumentation("dave")
        Assert.assertFalse(ext.shouldIncludeVariant("dave"))
    }

    @Test
    void shouldInstrumentTests() {
        ext.instrumentTests.set(false)
        Assert.assertFalse(ext.shouldInstrumentTests())

        ext.instrumentTests.set(true)
        Assert.assertTrue(ext.shouldInstrumentTests())
    }

    @Test
    void shouldIncludeMapUpload() {
        Assert.assertTrue(ext.shouldIncludeMapUpload("curly"))
        Assert.assertFalse(ext.shouldIncludeMapUpload("moe"))

        ext.uploadMapsForVariant("mark", "bruce", "scott")
        Assert.assertTrue(ext.shouldIncludeMapUpload("scott"))
        Assert.assertFalse(ext.shouldIncludeMapUpload("dave"))
    }
}