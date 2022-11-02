/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.UnexpectedBuildFailure
import spock.lang.Requires
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS
import static org.gradle.testkit.runner.TaskOutcome.UP_TO_DATE

@Stepwise
@Requires({ System.getProperty('integrationTests', '') != 'dexguard' })
class PluginIntegrationSpec extends Specification {

    static final rootDir = new File("../..")
    static final projectRootDir = new File(rootDir, "agent-test-app/")
    static final buildDir = new File(projectRootDir, "build")

    static final agentVersion = '6.6.0'     // update as needed
    static final agpVersion = '4.+'
    static final gradleVersion = "7.2"

    @Shared
    Map<String, String> localEnv = [:]

    @Shared
    BuildResult buildResult

    @Shared
    def testTask = 'assembleRelease'

    @Shared
    def testVariants = ['release']

    @Shared
    def printFilter

    @Shared
    String filteredOutput

    // fixtures
    def setup() {
        printFilter = new PrintFilter()
    }

    def setupSpec() {
        given: "Publish the agent to local M2 repo location"
        buildResult = GradleRunner.create()
                .withProjectDir(rootDir)
                .withArguments("publish")
                .build()

        and: "verify M2 repo location"
        localEnv += System.getenv()
        if (localEnv["M2_REPO"] == null) {
            def m2 = new File(rootDir, "build/.m2/repository")
            if (!(m2.exists() && m2.canRead())) {
                throw new IOException("M2_REPO not found. Run `./gradlew publish` to stage the agent")
            }
            localEnv.put("M2_REPO", m2.getAbsolutePath())
        }

        and: "create the build runner"
        setup() // alloc the printFilter
        def runner = GradleRunner.create()
                .forwardStdOutput(printFilter)
                .withDebug(true)
                .withGradleVersion(gradleVersion)
                .withProjectDir(projectRootDir)
                .withArguments("--debug",
                        "-Pnewrelic.agent.version=${agentVersion}",
                        "-Pnewrelic.agp.version=${agpVersion}",
                        "-Pcompiler=r8",
                        "-PagentRepo=${localEnv["M2_REPO"]}",
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
            task(":assembleRelease").outcome == SUCCESS

            output.contains("Android Gradle plugin version: ")
            output.contains("Gradle version:")
            output.contains("Java version:")
        }
    }

    def "verify NewRelicConfig was injected"() {
        expect:
        testVariants.each { var ->
            ['newrelicConfig'].each { task ->
                buildResult.task(":${task}${var.capitalize()}").outcome == SUCCESS
                def configTmpl = new File(buildDir,
                        "/generated/source/newrelicConfig/${var}/com/newrelic/agent/android/NewRelicConfig.java")
                configTmpl.exists() && configTmpl.canRead()

                def configClass = new File(buildDir, "/intermediates/javac/${var}/classes/com/newrelic/agent/android/NewRelicConfig.class")
                configClass.exists() && configClass.canRead()
            }
        }
    }

    void "verify class transforms"() {
        expect:
        testVariants.each { var ->
            ['transformClassesWithNewrelicTransformFor'].each { task ->
                buildResult.task(":${task}${var.capitalize()}").outcome == SUCCESS
            }
        }
    }

    def "verify build IDs"() {
        expect:
        filteredOutput.find(~/newrelicConfigDebug buildId\[(.*)\]/) != null
        filteredOutput.find(~/newrelicConfigRelease buildId\[(.*)\]/) != null
    }

    def "verify map uploads"() {
        expect:
        filteredOutput.contains("Maps will be tagged and uploaded for variants [")

        testVariants.each { var ->
            buildResult.task(":newrelicMapUploadMinify${var.capitalize()}WithR8").outcome == SUCCESS

            with(new File(buildDir, "outputs/mapping/${var}/mapping.txt")) {
                exists()
                text.contains("# NR_BUILD_ID -> ")
                filteredOutput.contains("Map file for variant [${var}] detected: [${getCanonicalPath()}]")
                filteredOutput.contains("Tagging map [${getCanonicalPath()}] with buildID [")
            }
        }
    }

    def "verify config task with incremental builds"() {
        given: "Build the app again without cleaning"

        // rerun the build use local results
        def runner = GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(projectRootDir)
                .withEnvironment(localEnv)
                .withArguments(
                        "-Pnewrelic.agent.version=${agentVersion}",
                        "-Pnewrelic.agp.version=${agpVersion}",
                        "-PagentRepo=${localEnv["M2_REPO"]}",
                        testTask)

        when: "run the cached build"
        buildResult = runner.build()

        then:
        // rerun the build use local results
        testVariants.each { var ->
            ['newrelicConfig'].each { taskName ->
                buildResult.task(":${taskName}${var.capitalize()}").outcome == UP_TO_DATE
            }
        }
    }

    def "verify unsupported AGP version check"() {
        given: "Apply an unsupported AGP version"
        def errStringWriter = new StringWriter()
        def runner = GradleRunner.create()
                .forwardStdError(errStringWriter)
                .withGradleVersion(gradleVersion)
                .withProjectDir(projectRootDir)
                .withEnvironment(localEnv)
                .withArguments(
                        "-Pnewrelic.agent.version=${agentVersion}",
                        "-Pnewrelic.agp.version=3.3.+",
                        "-PagentRepo=${localEnv["M2_REPO"]}",
                        "clean")

        when: "run the build"
        try {
            buildResult = runner.build()
        } catch (UnexpectedBuildFailure e) {
        }

        then:
        errStringWriter.toString().contains("The New Relic plugin is not compatible with Android Gradle plugin")
        errStringWriter.toString().contains("AGP versions ${BuildHelper.minSupportedAGPVersion.getVersion()} - ${BuildHelper.currentSupportedAGPVersion.getVersion()} are officially supported.")
    }

    def "verify pre-release AGP version check"() {
        given: "Apply an unsupported AGP version"
        def runner = GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(projectRootDir)
                .withEnvironment(localEnv)
                .withArguments(
                        "-Pnewrelic.agent.version=${agentVersion}",
                        "-Pnewrelic.agp.version=7.1.0-alpha12",
                        "-PagentRepo=${localEnv["M2_REPO"]}",
                        "clean")

        when: "run the build"
        buildResult = runner.build()

        then:
        buildResult.output.contains("AGP version [7.1.0-alpha12] is not officially supported")
    }
}
