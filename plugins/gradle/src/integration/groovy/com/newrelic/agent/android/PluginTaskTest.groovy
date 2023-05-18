/*
 * Copyright (c) 2023. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android


import org.junit.Assert
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

abstract class PluginTaskTest extends PluginTest {

    NewRelicExtension ext
    BuildHelper buildHelper
    VariantAdapter variantAdapter

    PluginTaskTest() {
        super(false);
    }

    @BeforeEach
    void setup() {
        // Create the instances needed to test this class
        ext = NewRelicExtension.register(project)
        buildHelper = BuildHelper.register(project)
        variantAdapter = buildHelper.variantAdapter
        variantAdapter.configure(ext)
    }

    @Test
    void getLogger() {
        Assert.assertEquals(provider.get().logger, NewRelicGradlePlugin.LOGGER)
    }
}