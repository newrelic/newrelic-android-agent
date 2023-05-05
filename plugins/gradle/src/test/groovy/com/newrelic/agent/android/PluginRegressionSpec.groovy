/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import spock.lang.*

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS
import static org.gradle.testkit.runner.TaskOutcome.UP_TO_DATE

@IgnoreIf({ System.getProperty('regressionTests', 'dexguard') == 'dexguard' })
class PluginRegressionSpec extends Specification {

    static final rootDir = new File("../..")
    static final projectRootDir = new File(rootDir, "samples/agent-test-app/")
    static final buildDir = new File(projectRootDir, "build")

    // update as needed
    static final gradleVersion = "7.2"

    @Shared
    Map<String, String> localEnv = [:]

    @Shared
    BuildResult buildResult

    @Shared
    boolean debuggable = true

    @Shared
    def testTask = 'assembleRelease'

    @Shared
    def testVariants = ['release']

    @Shared
    def printFilter

    @Shared
    String filteredOutput

    @Shared
    StringWriter errorOutput

    // fixtures
    def setup() {
        printFilter = new PrintFilter()
        errorOutput = new StringWriter()
    }

    def setupSpec() {
        given: "Set/verify staging location"
        localEnv += System.getenv()
        if (localEnv["M2_REPO"] == null) {
            def m2 = new File(rootDir, "build/.m2/repository").absoluteFile
            try {
                if (!(m2.exists() && m2.canRead())) {
                    provideRunner()
                            .withProjectDir(rootDir)
                            .withArguments("install", "publish")
                            .build()
                    if (!(m2.exists() && m2.canRead())) {
                        throw new IOException("M2_REPO not found. Run `./gradlew publish` to stage the agent")
                    }
                }
                localEnv.put("M2_REPO", m2.getAbsolutePath())
            } catch (Exception ignored) {
            }
        }
    }

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
                        "-PagentRepo=local",
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
        /**
         * @https: //developer.android.com/studio/releases/gradle-plugin#updating-gradle
         * */
        given: "Run #agent plugin using #agp/#gradle"
        def runner = provideRunner()
                .withGradleVersion(gradle)
                .withArguments(
                        "-Pnewrelic.agent.version=${agent}",
                        "-Pnewrelic.agp.version=${agp}",
                        "-Pcompiler=r8",
                        "-PagentRepo=local",
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

    def cleanup() {
        with(new File(projectRootDir, ".gradle/configuration-cache")) {
            it.deleteDir()
        }
    }

    def provideRunner() {
        def runner = GradleRunner.create()
                .withProjectDir(projectRootDir)
                .forwardStdOutput(printFilter)
                .forwardStdError(errorOutput)
                .withGradleVersion(gradleVersion)

        return debuggable ? runner.withDebug(debuggable) : runner.withEnvironment(localEnv)
    }

}
