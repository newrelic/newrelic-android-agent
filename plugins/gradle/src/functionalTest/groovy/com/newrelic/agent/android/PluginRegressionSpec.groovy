/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android

import spock.lang.IgnoreIf
import spock.lang.Requires
import spock.lang.Shared
import spock.lang.Unroll

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS
import static org.gradle.testkit.runner.TaskOutcome.UP_TO_DATE

@IgnoreIf({ System.getProperty('regressionTests', 'dexguard') == 'dexguard' })
class PluginRegressionSpec extends PluginSpec {

    @Shared
    def testTask = 'assembleRelease'

    @Shared
    def testVariants = ['release']


    @Unroll("#dataVariablesWithIndex")
    @Requires({ jvm.isJava11Compatible() })
    def "Regress agent[#agent] against AGP[#agp] Gradle[#gradle]"() {
        given: "Run plugin using the AGP/Gradle combination"
        def runner = provideRunner()
                .withGradleVersion(gradle)
                .withArguments(
                        "-Pnewrelic.agent.version=${agent}",
                        "-Pnewrelic.agp.version=${agp}",
                        "-Pcompiler=r8",
                        "-PagentRepo=${localEnv["M2_REPO"]}",
                        "-PwithProductFlavors=false",
                        "--stacktrace",
                        "clean",
                        testTask)

        when: "run the build *once* and cache the results"
        buildResult = runner.build()
        filteredOutput = printFilter

        then:
        with(buildResult) {
            with(task(":clean")) {
                outcome == SUCCESS || outcome == UP_TO_DATE
            }

            testVariants.each { var ->
                task(":assemble${var.capitalize()}").outcome == SUCCESS
                (task(":transformClassesWith${NewRelicTransform.NAME.capitalize()}For${var.capitalize()}")?.outcome == SUCCESS ||
                        task(":${ClassTransformWrapperTask.NAME}${var.capitalize()}")?.outcome == SUCCESS)

                [NewRelicConfigTask.NAME].each { task ->
                    buildResult.task(":${task}${var.capitalize()}").outcome == SUCCESS
                    def configClass = new File(buildDir, "/intermediates/javac/${var}/classes/com/newrelic/agent/android/NewRelicConfig.class")
                    configClass.exists() && configClass.canRead()
                }

                (task(":${NewRelicMapUploadTask.NAME}${var.capitalize()}")?.outcome == SUCCESS ||
                        task("newrelicMapUploadMinify${var.capitalize()}WithR8")?.outcome == SUCCESS)

                with(new File(buildDir, "outputs/mapping/${var}/mapping.txt")) {
                    exists()
                    text.contains("# NR_BUILD_ID -> ")
                }
            }
        }

        where:
        [agent, [agp, gradle]] << [
                ["6.+", [agp: "7.4.+", gradle: "7.5"]],
        //      ["7.+", [agp: "7.0.+", gradle: "7.0.2"]],       // FIXME
                ["7.+", [agp: "7.1.3", gradle: "7.2"]],
                ["7.+", [agp: "7.2.+", gradle: "7.3.3"]],
                ["7.+", [agp: "7.3.+", gradle: "7.4"]],
                ["7.+", [agp: "7.4.+", gradle: "7.5"]],
                ["7.+", [agp: "7.4.+", gradle: "8.0"]],
        ]
    }

    @Unroll("#dataVariablesWithIndex")
    @Requires({ jvm.isJava17Compatible() })
    def "Regress agent[#agent] against AGP8 [#agp] Gradle[#gradle]"() {
        given: "Run #agent plugin using #agp/#gradle"
        def runner = provideRunner()
                .withGradleVersion(gradle)
                .withArguments(
                        "-Pnewrelic.agent.version=${agent}",
                        "-Pnewrelic.agp.version=${agp}",
                        "-Pcompiler=r8",
                        "-PagentRepo=local",
                        "-PagentRepo=${localEnv["M2_REPO"]}",
                        "-PwithProductFlavors=false",
                        "--stacktrace",
                        "clean",
                        testTask)

        when: "run the build *once* and cache the results"
        buildResult = runner.build()
        filteredOutput = printFilter

        then:
        with(buildResult) {
            with(task(":clean")) {
                outcome == SUCCESS || outcome == UP_TO_DATE
            }

            testVariants.each { var ->
                task(":assemble${var.capitalize()}").outcome == SUCCESS
                task(":${ClassTransformWrapperTask.NAME}${var.capitalize()}").outcome == SUCCESS
                task(":${NewRelicConfigTask.NAME}${var.capitalize()}").outcome == SUCCESS
                task(":${NewRelicMapUploadTask.NAME}${var.capitalize()}").outcome == SUCCESS
            }
        }

        where:
        [agent, [agp, gradle]] << [
                ["7.+", [agp: "8.0.+", gradle: "8.0"]],
                ["7.+", [agp: "8.0.+", gradle: "8.1"]],
                //      ["7.+", [agp: "8.1.+", gradle: "8.1"]],
        ]
    }
}
