/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android

import spock.lang.Ignore
import spock.lang.Requires
import spock.lang.Stepwise

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS
import static org.gradle.testkit.runner.TaskOutcome.UP_TO_DATE

@Stepwise
@Requires({ jvm.isJava17Compatible() })
class PluginJDK17IntegrationSpec extends PluginSpec {

    static final jdkVersion = 17
    static final agpVersion = "8.0.+"
    static final gradleVersion = "8.1.1"

    def setup() {
        provideRunner()
                .withArguments("-Pnewrelic.agp.version=${agpVersion}", "clean")
                .build()
    }

    def "verify config task with incremental builds"() {
        given: "Build the app again without cleaning"
        def runner = provideRunner().withArguments(
                "-Pnewrelic.agent.version=${agentVersion}",
                "-Pnewrelic.agp.version=${agpVersion}",
                "-Pcompiler=r8",
                "-PagentRepo=${localEnv["M2_REPO"]}",
                "-PwithProductFlavors=false",
                "--stacktrace",
                testTask)

        when:
        testVariants = ["release"]
        buildResult = runner.build()

        and:
        buildResult = runner.build()

        then:
        // rerun the build use local results
        testVariants.each { var ->
            with(buildResult.task(":${NewRelicConfigTask.NAME}${var.capitalize()}")) {
                outcome == SUCCESS || outcome == UP_TO_DATE
            }
        }
    }

    def "test min supported AGP version"() {
        given: "Build the app using the minimum supported AGP version"

        // rerun the build use local results
        def runner = provideRunner()
                .withGradleVersion("7.2")
                .withArguments(
                        "-Pnewrelic.agent.version=${agentVersion}",
                        "-Pnewrelic.agp.version=${BuildHelper.minSupportedAGPVersion}",
                        "-PagentRepo=${localEnv["M2_REPO"]}",
                        "clean",
                        testTask)

        when:
        buildResult = runner.build()

        then:
        buildResult.task("${testTask}").outcome == SUCCESS
    }

    def "verify pre-release AGP version check"() {
        given: "Apply an unsupported AGP version"
        def runner = provideRunner()
                .withGradleVersion("8.0")
                .withArguments(
                        "-Pnewrelic.agent.version=${agentVersion}",
                        "-Pnewrelic.agp.version=8.0.0-alpha01",
                        "-PagentRepo=${localEnv["M2_REPO"]}",
                        "clean")

        when: "run the build"
        buildResult = runner.build()

        then:
        buildResult.output.contains("AGP version [8.0.0-alpha1] is not officially supported")
    }

    def "verify configuration cache compatibility"() {
        given: "Cache the config task"
        def runner = provideRunner()
                .withGradleVersion(gradleVersion)
                .withArguments(
                        "-Pnewrelic.agent.version=${agentVersion}",
                        "-Pnewrelic.agp.version=${agpVersion}",
                        "-PagentRepo=${localEnv["M2_REPO"]}",
                        "--configuration-cache",
                        "clean", "newrelicConfigRelease")
        when:
        def preResult = runner.build()

        and:
        def postResult = runner.build()

        then:
        preResult.task(":newrelicConfigRelease").outcome == SUCCESS
        preResult.output.contains("Calculating task graph")
        preResult.output.contains("Configuration cache entry stored")

        with(postResult.task(":${NewRelicConfigTask.NAME}Release")) {
            outcome == UP_TO_DATE || outcome == SUCCESS     // FIXME Should be UP_TO_DATE
        }

        postResult.output.contains("Reusing configuration cache")
        postResult.output.contains("Configuration cache entry reused")
    }

    @Ignore("FIXME: Fails in JDK w/config cache")
    def "verify cached map uploads"() {
        given: "Rerun the cached the task"
        def runner = provideRunner()
                .withGradleVersion(gradleVersion)
                .withArguments(
                        "-Pnewrelic.agent.version=${agentVersion}",
                        "-Pnewrelic.agp.version=${agpVersion}",
                        "-PagentRepo=${localEnv["M2_REPO"]}",
                        "--configuration-cache",
                        "newrelicMapUploadRelease")
        when:
        buildResult = runner.build()

        and:
        buildResult = runner.build()

        then:
        with(buildResult) {
            (output.contains("Reusing configuration cache") && output.contains("Configuration cache entry reused")) ||
                    (output.contains("configuration cache cannot be reused") && output.contains("Configuration cache entry stored"))
            task(":newrelicMapUploadRelease").outcome == SUCCESS
        }
    }

    def "verify buildID is cached with config task"() {
        given: "Cache the config task with new build ID"
        def runner = provideRunner()
                .withArguments(
                        "-Pnewrelic.agent.version=${agentVersion}",
                        "-Pnewrelic.agp.version=${agpVersion}",
                        "-PagentRepo=${localEnv["M2_REPO"]}",
                        "--configuration-cache",
                        "clean",
                        "newRelicConfigRelease")
        when:
        def preResult = runner.build()

        and:
        def preBuildId = new File(buildDir, "/generated/java/newrelicConfigRelease/com/newrelic/agent/android/NewRelicConfig.java").text

        and:
        def postResult = runner.build()

        and:
        def postBuildId = new File(buildDir, "/generated/java/newrelicConfigRelease/com/newrelic/agent/android/NewRelicConfig.java").text

        then:
        preBuildId.find(~/BUILD_ID = \"(.*)\".*/) == postBuildId.find(~/BUILD_ID = \"(.*)\".*/)

        preResult.output.contains("Calculating task graph as no configuration cache is available for tasks:")
        preResult.output.contains("Configuration cache entry stored")

        postResult.output.contains("Reusing configuration cache")
        postResult.output.contains("Configuration cache entry reused")
    }

    def "verify product flavors"() {
        given: "Build with flavor dimensions enabled"
        def runner = provideRunner()
                .withArguments("--debug",
                        "-Pnewrelic.agent.version=${agentVersion}",
                        "-Pnewrelic.agp.version=${agpVersion}",
                        "-PagentRepo=${localEnv["M2_REPO"]}",
                        "-PwithProductFlavors=true",
                        testTask)

        when: "run the build"
        buildResult = runner.build()

        then:
        with(buildResult) {
            task(":assembleRelease").outcome == SUCCESS
            ['Amazon', 'Google'].each {
                task(":transformClassesWithNewrelicTransformFor${it}Release").outcome == SUCCESS
                task(":assemble${it}Release").outcome == SUCCESS
                filteredOutput.contains("Map upload ignored for variant[${it.toLowerCase()}Debug]")
                filteredOutput.contains("newrelicConfig${it}Release buildId[")
                filteredOutput.contains("newrelicConfig${it}Debug buildId[")
            }
        }
    }

    def "verify plugin extension API"() {
        given: "Configure the plugin using the extension API methods"
        extensionsFile << """
            newrelic {
                uploadMapsForVariant 'release', 'qa'
                excludeVariantInstrumentation 'debug'
                }
        """

        def runner = provideRunner()
                .withArguments("--debug",
                        "-Pnewrelic.agent.version=${agentVersion}",
                        "-Pnewrelic.agp.version=${agpVersion}",
                        "-PagentRepo=${localEnv["M2_REPO"]}",
                        "clean", "assemble")

        when: "run the build"
        buildResult = runner.build()

        then:
        with(buildResult) {
            task(":transformClassesWithNewrelicTransformForDebug") == null
            with(task(":transformClassesWithNewrelicTransformForRelease")) {
                outcome == SUCCESS || outcome == UP_TO_DATE
            }
            with(task(":transformClassesWithNewrelicTransformForQa")) {
                outcome == SUCCESS || outcome == UP_TO_DATE
            }

            ["debug", "release", "qa"].each { var ->
                var = var.capitalize()
                filteredOutput.contains("newrelicConfig${var} buildId[")
                ["assemble${var}"].each { taskName ->
                    with(task(":${taskName}")) {
                        outcome == SUCCESS || outcome == UP_TO_DATE
                    }
                }
            }

            filteredOutput.contains("Excluding instrumentation of variant [debug]")
            !filteredOutput.contains("Excluding instrumentation of variant [release]")
            filteredOutput.contains("Map upload ignored for variant[debug]")
        }
    }

    def "verify plugin extension DSL"() {
        given: "Configure the plugin using the extension DSL"
        extensionsFile << """
            newrelic {
                variantConfigurations {
                    debug {
                        instrument = false
                        uploadMappingFile = true
                    }
                    qa {
                        uploadMappingFile = false
                    }
                    release {
                        uploadMappingFile = true
                    }
                }
            }
        """

        def runner = provideRunner()
                .withArguments("--debug",
                        "-Pnewrelic.agent.version=${agentVersion}",
                        "-Pnewrelic.agp.version=${agpVersion}",
                        "-PagentRepo=${localEnv["M2_REPO"]}",
                        "clean", "assemble")

        when: "run the build"
        buildResult = runner.build()

        then:
        with(buildResult) {
            task(":transformClassesWithNewrelicTransformForDebug") == null
            with(task(":transformClassesWithNewrelicTransformForRelease")) {
                outcome == SUCCESS || outcome == UP_TO_DATE
            }
            with(task(":transformClassesWithNewrelicTransformForQa")) {
                outcome == SUCCESS || outcome == UP_TO_DATE
            }

            ["debug", "release", "qa"].each { var ->
                var = var.capitalize()
                filteredOutput.contains("newrelicConfig${var} buildId[")
                ["assemble${var}"].each { taskName ->
                    with(task(":${taskName}")) {
                        outcome == SUCCESS || outcome == UP_TO_DATE
                    }
                }
            }

            filteredOutput.contains("Excluding instrumentation of variant [debug]")
            !filteredOutput.contains("Excluding instrumentation of variant [release]")
            filteredOutput.contains("Map upload ignored for variant[debug]")
        }
    }

    @Requires({ jvm.isJava17Compatible() })
    def "verify AGP8"() {
        final agp8Version = s
        final gradle8Version = "8.0.2"

        given: "Apply AGP8"
        def runner = provideRunner()
                .withGradleVersion(gradle8Version)
                .withArguments(
                        "-Pnewrelic.agent.version=${agentVersion}",
                        "-Pnewrelic.agp.version=${agp8Version}",
                        "-PagentRepo=${localEnv["M2_REPO"]}",
                        "clean", testTask)

        when: "run the build"
        buildResult = runner.build()

        then:
        with(buildResult) {
            with(task(":clean")) {
                outcome == SUCCESS || outcome == UP_TO_DATE
            }
            with(task(":$testTask")) {
                outcome == SUCCESS
            }

            filteredOutput.contains("Android Gradle plugin version:")
            filteredOutput.contains("Gradle version:")
            filteredOutput.contains("Java version:")
            filteredOutput.contains("Kotlin version:")
        }
    }

    def cleanup() {
        extensionsFile?.delete()
        with(new File(projectRootDir, ".gradle/configuration-cache")) {
            it.deleteDir()
        }
    }
}