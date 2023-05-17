/*
 * Copyright (c) 2023. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.agp7

import com.newrelic.agent.android.BuildHelper
import com.newrelic.agent.android.PluginTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AGP74AdapterTest extends PluginTest {

    @BeforeEach
    void setUp() {
        def buildHelper = BuildHelper.register(project)
    }

    @Test
    void getBuildTypeProvider() {
    }

    @Test
    void wiredWithTransformProvider() {
    }

    @Test
    void wiredWithConfigProvider() {
    }
}