/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android

import org.gradle.testkit.runner.UnexpectedBuildFailure
import spock.lang.Ignore
import spock.lang.Requires
import spock.lang.Stepwise

import static org.gradle.testkit.runner.TaskOutcome.SKIPPED
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

@Stepwise
@Requires({ !jvm.isJava17Compatible() })
class PluginIntegrationSpec extends PluginSpec {

    def setup() {
        with(new File(projectRootDir, ".gradle/configuration-cache")) {
            it.deleteDir()
        }

        provideRunner()
                .withGradleVersion("7.3.3")
                .withArguments("-Pnewrelic.agp.version=7.2.+", "clean")
                .build()
    }

    def "test min supported Gradle version"() {
        given: "Build the app using the minimum supported Gradle version"

        // rerun the build use local results
        def runner = provideRunner()
                .withGradleVersion(BuildHelper.minSupportedGradleVersion)
                .withArguments(
                        "-Pnewrelic.agent.version=${agentVersion}",
                        "-Pnewrelic.agp.version=${BuildHelper.minSupportedAGPVersion}",
                        "-PagentRepo=${localEnv["M2_REPO"]}",
                        "clean",
                        testTask)

        when:
        buildResult = runner.build()

        then:
        buildResult.task(":${testTask}").outcome == SUCCESS
    }

    def "verify unsupported Gradle version"() {
        given: "Apply an unsupported Gradle version"
        def runner = provideRunner()
                .withGradleVersion("6.7.1")
                .withArguments(
                        "-Pnewrelic.agent.version=${agentVersion}",
                        "-Pnewrelic.agp.version=4.+",
                        "-PagentRepo=${localEnv["M2_REPO"]}")

        when:
        try {
            buildResult = runner.build()
        } catch (Exception ignored) {
        }

        then:
        errorOutput.toString().contains("FAILURE: Build failed with an exception") ||
                buildResult.output.contains("The New Relic plugin may not be compatible with Gradle version")
    }

    def "verify unsupported AGP version"() {
        given: "Apply an unsupported AGP version: AGP 3.+ Gradle 5.6.4"
        def runner = provideRunner()
                .withGradleVersion("6.7.1")
                .withArguments(
                        "-Pnewrelic.agent.version=${agentVersion}",
                        "-Pnewrelic.agp.version=4.1.+",
                        "-PagentRepo=${localEnv["M2_REPO"]}")

        when:
        try {
            buildResult = runner.build()
        } catch (Exception ignored) {
        }

        then:
        (errorOutput.toString().contains("FAILURE: Build failed with an exception") ||
                errorOutput.toString().contains("The New Relic plugin is not compatible"))
    }

    def "verify min supported Gradle version without config cache"() {
        given: "Build with the lowest supported Gradle/AGP version"
        def runner = provideRunner()
                .withGradleVersion(BuildHelper.minSupportedGradleVersion)
                .withArguments(
                        "-Pnewrelic.agent.version=${agentVersion}",
                        "-Pnewrelic.agp.version=${BuildHelper.minSupportedAGPVersion}",
                        "-PagentRepo=${localEnv["M2_REPO"]}",
                        testTask)
        when:
        buildResult = runner.build()

        then:
        with(buildResult) {
            with(task(":${testTask}")) {
                outcome == SUCCESS
            }
        }
    }

    def "verify unsupported configuration cache Gradle version"() {
        given: "Try to cache the task with an unsupported Gradle version"
        def runner = provideRunner()
                .withGradleVersion(BuildHelper.minSupportedGradleConfigCacheVersion)
                .withArguments(
                        "-Pnewrelic.agent.version=${agentVersion}",
                        "-Pnewrelic.agp.version=4.0.0",
                        "-PagentRepo=${localEnv["M2_REPO"]}",
                        "--configuration-cache",
                        "newRelicConfigRelease")
        when:
        buildResult = runner.build()

        then:
        UnexpectedBuildFailure e = thrown()
        e.message.contains("BUILD FAILED")
    }

    @Ignore("FIXME: map upload")
    def "verify invalidated cached map uploads"() {
        given: "Rerun the cached the task"
        def runner = provideRunner()
                .withGradleVersion(BuildHelper.minSupportedGradleVersion)
                .withArguments(
                        "-Pnewrelic.agent.version=${agentVersion}",
                        "-Pnewrelic.agp.version=${BuildHelper.minSupportedAGPConfigCacheVersion}",
                        "-PagentRepo=${localEnv["M2_REPO"]}",
                        "--configuration-cache",
                        testTask)
        when:
        def preResult = runner.build()

        and:
        def postResult = runner.build()

        then:
        preResult.output.contains("Configuration cache entry stored")
        postResult.task(":newrelicMapUploadRelease").outcome == SKIPPED
    }

    def "verify min config cache supported agp/gradle"() {
        given: "Cache the task"
        def runner = provideRunner()
                .withGradleVersion(BuildHelper.minSupportedGradleVersion)
                .withArguments(
                        "-Pnewrelic.agent.version=${agentVersion}",
                        "-Pnewrelic.agp.version=${BuildHelper.minSupportedAGPConfigCacheVersion}",
                        "-PagentRepo=${localEnv["M2_REPO"]}",
                        "--configuration-cache",
                        testTask)
        when:
        def preResult = runner.build()

        and:
        def postResult = runner.build()

        then:
        preResult.output.contains("Calculating task graph as no configuration cache is available for tasks:")
        preResult.output.contains("Configuration cache entry stored")

        postResult.output.contains("Reusing configuration cache")
        postResult.output.contains("Configuration cache entry reused")
    }

    def cleanup() {
        extensionsFile?.delete()
    }
}