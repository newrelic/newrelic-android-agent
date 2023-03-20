/*
 * Copyright (c) 2023. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android

import com.android.build.gradle.AppPlugin
import com.google.common.io.Files
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.rules.TemporaryFolder

abstract class PluginTest {
    final def rootDir = new File("../..").absoluteFile
    final def projectDir = new File(rootDir, "samples/agent-test-app/")

    def tmpProjectDir
    def project
    def agp
    def plugin

    @BeforeEach
    void beforeEach() {
        tmpProjectDir = TemporaryFolder.builder().assureDeletion().build()
        tmpProjectDir.create()

        def settings = tmpProjectDir.newFile("settings.gradle")
        Files.write(getClass().getResource("/gradle/settings.gradle").bytes, settings)

        def build = tmpProjectDir.newFile("build.gradle")
        Files.write(getClass().getResource("/gradle/build.gradle").bytes, build)

        project = ProjectBuilder.builder()
                .withName("newrelic-plugin-test")
                .withProjectDir(projectDir) // tmpProjectDir.root)
                .build()

        project.getPlugins().with {
            agp = apply("com.android.application")
            plugin = apply("newrelic")
        }

        // force soft evaluation
        project.getTasksByName("assemble", false)

        // or hard,
        // (project as ProjectInternal).evaluate()
    }

    @AfterEach
    void
    afterEach() {
        tmpProjectDir.delete()
    }
}