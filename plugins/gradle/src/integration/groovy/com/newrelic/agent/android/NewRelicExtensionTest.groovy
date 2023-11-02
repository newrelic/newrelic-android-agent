/*
 * Copyright (c) 2023. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android

import org.junit.Assert
import org.junit.Ignore
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class NewRelicExtensionTest extends PluginTest {
    NewRelicExtension ext

    // include map uploads for these flavor or build types
    def mapUploadInclusions = List.of(
            "productionRelease",
            "stagingBeta",
            "AuToMaTiOn")

    // exclude instrumentation based on these flavor types
    def instrumentationExclusions = List.of(
            "internalDebug",
            "internalBeta",
            "internalRelease",
            "internalBenchmark",
            "internalAutomation",
            "stagingDebug",
            "stagingRelease",
            "stagingBenchmark",
            "stagingAutomation",
            "productionDebug",
            "productionBeta",
            "productionBenchmark",
            "productionAutomation",
            "mockAutomation")

    // exclude instrumentation based on these build types
    def buildTypeExclusions = List.of(
            "debug",
            "beta",
            "Benchmark",
            "AUTOMATION",
            "reLeaSe"
    )

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
        Assert.assertTrue(NewRelicExtension.register(project) instanceof NewRelicExtension)
    }

    @Test
    void getEnabled() {
        Assert.assertTrue(ext.enabled)
        Assert.assertFalse(ext.setEnabled(false))
        Assert.assertFalse(ext.getEnabled())
    }

    @Test
    void variantConfigurations() {
        Assert.assertNotNull(ext.variantConfigurations)
        Assert.assertFalse(ext.variantConfigurations.isEmpty())
    }

    @Test
    void uploadMapsForVariant() {
        Assert.assertTrue(ext.shouldIncludeMapUpload("curly"))

        ext.uploadMapsForVariant(*mapUploadInclusions)
        Assert.assertFalse(ext.shouldIncludeMapUpload("curly"))
        Assert.assertTrue(ext.shouldIncludeMapUpload("automation"))
        Assert.assertTrue(ext.shouldIncludeMapUpload("betaAutomation"))
        Assert.assertFalse(ext.shouldIncludeMapUpload("automationTest"))
    }

    @Test
    void shouldExcludePackageInstrumentation() {
        ext.excludePackageInstrumentation("com.newrelic.android", "com.android")
        Assert.assertEquals(2, ext.packageExclusions.size())
        Assert.assertTrue(ext.shouldExcludePackageInstrumentation("com.newrelic.android.agent.Agent.class"))
        Assert.assertFalse(ext.shouldExcludePackageInstrumentation("com.newrelic.agent.Agent.class"))

        ext.excludePackageInstrumentation("com.newrelic.*Agent.class", ".*\\.android.*agent\\..*")
        Assert.assertTrue(ext.shouldExcludePackageInstrumentation("com/newrelic/android/agentAgent.class"))
        Assert.assertTrue(ext.shouldExcludePackageInstrumentation("com.newrelic.agent.Agent.class"))

        ext.excludePackageInstrumentation("com/newrelic/.*Agent.class", ".*\\.android.*agent\\..*")
        Assert.assertTrue(ext.shouldExcludePackageInstrumentation("com/newrelic/android/agent/Agent.class"))
        Assert.assertTrue(ext.shouldExcludePackageInstrumentation("com.newrelic.agent.Agent.class"))
        Assert.assertFalse(ext.shouldExcludePackageInstrumentation("com.newrelik.agent.Agent.class"))
    }

    @Test
    void shouldExcludeVariant() {
        ext.uploadMapsForVariant(*mapUploadInclusions)
        ext.excludeVariantInstrumentation(*instrumentationExclusions)

        instrumentationExclusions.each {
            Assert.assertFalse(ext.shouldIncludeVariant(it))
        }

        ext.excludeVariantInstrumentation(*buildTypeExclusions)
        instrumentationExclusions.each {
            Assert.assertTrue(ext.shouldExcludeVariant(it))
        }

        ext.excludeVariantInstrumentation("dweezil", "ahmet", "moon unit")
        Assert.assertTrue(ext.shouldExcludeVariant("dweezil"))
        Assert.assertTrue(ext.shouldExcludeVariant("ahmet"))
        Assert.assertFalse(ext.shouldExcludeVariant("diva"))
    }

    @Test
    void shouldIncludeVariant() {
        // test variant configuration
        Assert.assertTrue(ext.shouldIncludeVariant("larry"))

        ext.excludeVariantInstrumentation(*instrumentationExclusions)
        instrumentationExclusions.each {
            Assert.assertFalse(ext.shouldIncludeVariant(it))
        }

        ext.excludeVariantInstrumentation(*buildTypeExclusions)
        instrumentationExclusions.each {
            Assert.assertFalse(ext.shouldIncludeVariant(it))
        }

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
        // test variant configuration
        Assert.assertTrue(ext.shouldIncludeMapUpload("curly"))
        Assert.assertFalse(ext.shouldIncludeMapUpload("moe"))

        ext.uploadMapsForVariant("mark", "bruce", "scott")
        Assert.assertTrue(ext.shouldIncludeMapUpload("scott"))
        Assert.assertFalse(ext.shouldIncludeMapUpload("dave"))
        Assert.assertFalse(ext.shouldIncludeMapUpload("KeViN"))
    }

}