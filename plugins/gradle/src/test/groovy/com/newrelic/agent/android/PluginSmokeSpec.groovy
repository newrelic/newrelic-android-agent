/*
 * Copyright (c) 2023 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS
import static org.gradle.testkit.runner.TaskOutcome.UP_TO_DATE

class PluginSmokeSpec extends PluginSpec {

    def setupSpec() {
        given: "create the build runner"
        def runner = provideRunner()
                .withArguments(
                        "-Pnewrelic.agent.version=${agentVersion}",
                        "-Pnewrelic.agp.version=${agpVersion}",
                        "-Pcompiler=r8",
                        "-PagentRepo=${localEnv["M2_REPO"]}",
                        "-PwithProductFlavors=true",
                        "--debug",
                        "--stacktrace",
                        "clean",
                        testTask)

        when: "run the build *once* and cache the results"
        buildResult = runner.build()
        filteredOutput = printFilter
    }

    def "build the test app"() {
        expect: "the test app was built"
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

    def "verify NewRelicConfig was injected"() {
        expect:
        testVariants.each { var ->
            ['newrelicConfig'].each { task ->
                buildResult.task(":${task}${var.capitalize()}").outcome == SUCCESS
                def configTmpl = new File(buildDir,
                        "/generated/java/newrelicConfig${var.capitalize()}/com/newrelic/agent/android/NewRelicConfig.java")
                configTmpl.exists() && configTmpl.canRead()
                configTmpl.text.find(~/BUILD_ID = \"(.*)\".*/)

                def configClass = new File(buildDir, "/intermediates/javac/${var}/classes/com/newrelic/agent/android/NewRelicConfig.class")
                configClass.exists() && configClass.canRead()
            }
        }
    }

    void "verify class transforms"() {
        expect:
        testVariants.each { var ->
            (buildResult.task(":transformClassesWith${NewRelicTransform.NAME.capitalize()}For${var.capitalize()}")?.outcome == SUCCESS ||
                    buildResult.task(":${ClassTransformWrapperTask.NAME}${var.capitalize()}")?.outcome == SUCCESS)
        }
    }

    def "verify map uploads"() {
        expect:
        filteredOutput.contains("Maps will be tagged and uploaded for variants [")

        testVariants.each { var ->
            buildResult.task(":newrelicMapUpload${var.capitalize()}").outcome == SUCCESS

            with(new File(buildDir, "outputs/mapping/${var}/mapping.txt")) {
                exists()
                text.contains("# NR_BUILD_ID -> ")
                filteredOutput.contains("Map file for variant [${var}] detected: [${getCanonicalPath()}]")
                filteredOutput.contains("Tagging map [${getCanonicalPath()}] with buildID [")
            }
        }
    }
}