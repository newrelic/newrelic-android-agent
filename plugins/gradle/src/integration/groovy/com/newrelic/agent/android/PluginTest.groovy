/*
 * Copyright (c) 2023. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android

import com.google.common.io.Files
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.rules.TemporaryFolder

abstract class PluginTest {
    def tmpProjectDir
    def project
    def agp
    def plugin
    def applyPlugin = true

    PluginTest(boolean applyPlugin) {
        this.applyPlugin = applyPlugin
    }

    PluginTest() {
        this(true)
    }

    @BeforeEach
    void beforeEach() {
        tmpProjectDir = TemporaryFolder.builder().assureDeletion().build()
        tmpProjectDir.create()

        def build = tmpProjectDir.newFile("build.gradle")
        Files.write(getClass().getResource("/gradle/build.gradle").bytes, build)

        project = ProjectBuilder.builder()
                .withName("newrelic-plugin-test")
                .withProjectDir(tmpProjectDir.root)
                .build()

        project.getPlugins().with {
            agp = apply("com.android.application")
            if (applyPlugin) {
                plugin = apply("newrelic")
            }
        }

        // force soft evaluation of AGP
        project.getTasksByName("assemble", false)

        // or hard,
        // (project as ProjectInternal).evaluate()

        project.ext.applyPlugin = applyPlugin
    }

    @AfterEach
    void afterEach() {
        tmpProjectDir?.delete()
    }
}