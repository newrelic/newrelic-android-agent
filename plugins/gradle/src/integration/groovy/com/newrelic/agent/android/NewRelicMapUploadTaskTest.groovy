/*
 * Copyright (c) 2023. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android

import org.junit.Assert
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class NewRelicMapUploadTaskTest extends PluginTest {
    NewRelicExtension ext
    BuildHelper buildHelper
    VariantAdapter variantAdapter
    def provider

    NewRelicMapUploadTaskTest() {
        super(true);
    }

    @BeforeEach
    void setup() {
        // Create the instances needed to test this class
        ext = NewRelicExtension.register(project)
        buildHelper = BuildHelper.register(project)
        variantAdapter = buildHelper.variantAdapter
        variantAdapter.configure(ext)

        provider = buildHelper.variantAdapter.getMapUploadProvider("release")
    }

    @Test
    void getVariantName() {
        provider.get().tap {
            Assert.assertEquals("release", getVariantName().get())
        }
    }

    @Test
    void getProjectRoot() {
        provider.get().tap {
            Assert.assertEquals(project.layout.projectDirectory, it.getProjectRoot().get())
        }
    }

    @Test
    void getBuildId() {
        def task = provider.get().tap {
            Assert.assertFalse(getBuildId().get().isEmpty())
            Assert.assertFalse(UUID.fromString(getBuildId().get()).toString().isEmpty())
        }
    }

    @Test
    void getMapProvider() {
        def task = provider.get().tap {
            Assert.assertEquals("r8", getMapProvider().get().toLowerCase())
        }
    }

    @Test
    void getMappingFile() {
        def task = provider.get().tap {
            def f = getMappingFile().asFile
            Assert.assertFalse(f.get().absolutePath.isEmpty())
            def m = buildHelper.variantAdapter.getMappingFileProvider("release")
            Assert.assertEquals(f.get(), m.get().asFile)
        }
    }

    @Test
    void newRelicMapUploadTask() {
        provider.configure {
            // @TaskAction
            newRelicMapUploadTask()
        }
    }

    @Test
    void getLogger() {
        Assert.assertEquals(provider.get().logger, NewRelicGradlePlugin.LOGGER)
    }
}