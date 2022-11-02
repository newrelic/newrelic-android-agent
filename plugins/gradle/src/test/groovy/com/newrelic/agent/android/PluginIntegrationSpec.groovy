/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
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
@Requires({ System.hasProperty('integrationTests') && System.getProperty('integrationTests', '') != 'dexguard' })
class PluginIntegrationSpec extends Specification {

    static final rootDir = new File("../..")
    static final projectRootDir = new File(rootDir, "agent-test-app/")
    static final buildDir = new File(projectRootDir, "build")

    static final agentVersion = '6.9.0'     // update as needed
    static final agpVersion = BuildHelper.minSupportedAGPConfigCacheVersion.version
    static final gradleVersion = BuildHelper.minSupportedAGPConfigCacheVersion.version

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
        cleanup()

        given: "verify M2 repo location"
        localEnv += System.getenv()
        if (localEnv["M2_REPO"] == null) {
            def m2 = new File(rootDir, "build/.m2/repository")
            if (!(m2.exists() && m2.canRead())) {
                GradleRunner.create()
                        .withProjectDir(rootDir)
                        .withArguments("publish")
                        .build()
                if (!(m2.exists() && m2.canRead())) {
                    throw new IOException("M2_REPO not found. Run `./gradlew publish` to stage the agent")
                }
            }
            localEnv.put("M2_REPO", m2.getAbsolutePath())
        }

        and: "create the build runner"
        printFilter = new PrintFilter()
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
            with(task(":$testTask")) {
                outcome == SUCCESS
            }

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
                configTmpl.text.find(~/BUILD_ID = \"(.*)\".*/)

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

    def "verify unsupported Gradle version"() {
        given: "Apply an unsupported Gradle version"
        def errStringWriter = new StringWriter()
        def incompatVers = "5.6.4"
        def runner = GradleRunner.create()
                .forwardStdError(errStringWriter)
                .withGradleVersion(incompatVers)
                .withProjectDir(projectRootDir)
                .withEnvironment(localEnv)
                .withArguments(
                        "-Pnewrelic.agent.version=${agentVersion}",
                        "-Pnewrelic.agp.version=3.+",
                        "-PagentRepo=${localEnv["M2_REPO"]}")

        when: "run the build"
        try {
            buildResult = runner.build()
        } catch (UnexpectedBuildFailure) {
        }

        then:
        errStringWriter.toString().contains("BUILD FAILED") ||
                buildResult.output.contains("The New Relic plugin may not be compatible with Gradle version")
    }

    def "verify unsupported AGP version"() {
        given: "Apply an unsupported AGP version"
        def errStringWriter = new StringWriter()
        def runner = GradleRunner.create()
                .forwardStdError(errStringWriter)
                .withGradleVersion('5.6.4')
                .withProjectDir(projectRootDir)
                .withEnvironment(localEnv)
                .withArguments(
                        "-Pnewrelic.agent.version=${agentVersion}",
                        "-Pnewrelic.agp.version=3.+",
                        "-PagentRepo=${localEnv["M2_REPO"]}")

        when: "run the build"
        try {
            buildResult = runner.build()
        } catch (UnexpectedBuildFailure) {
        }

        then:
        errStringWriter.toString().contains("The New Relic plugin is not compatible") ||
                buildResult.output.contains("Could not find method registerJavaGeneratingTask() for arguments [provider(task 'newrelicConfig")
    }

    def "verify pre-release AGP version check"() {
        given: "Apply an unsupported AGP version"
        def runner = GradleRunner.create()
                .withGradleVersion("7.2")
                .withProjectDir(projectRootDir)
                .withEnvironment(localEnv)
                .withArguments(
                        "-Pnewrelic.agent.version=${agentVersion}",
                        "-Pnewrelic.agp.version=7.1.0-alpha12",
                        "-PagentRepo=${localEnv["M2_REPO"]}")

        when: "run the build"
        buildResult = runner.build()

        then:
        buildResult.output.contains("AGP version [7.1.0-alpha12] is not officially supported")
    }

    def "verify configuration cache compatibility"() {
        given: "Cache the config task"
        def runner = GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(projectRootDir)
                .withEnvironment(localEnv)
                .withArguments(
                        "-Pnewrelic.agent.version=${agentVersion}",
                        "-Pnewrelic.agp.version=${agpVersion}",
                        "-PagentRepo=${localEnv["M2_REPO"]}",
                        "--configuration-cache",
                        "newrelicConfigRelease")
        when:
        def preResult = runner.build()

        and:
        def postResult = runner.build()

        then:
        preResult.task(":newrelicConfigRelease").outcome == SUCCESS;
        preResult.output.contains("Calculating task graph")
        preResult.output.contains("Configuration cache entry stored")

        postResult.task(":newrelicConfigRelease").outcome == UP_TO_DATE;
        postResult.output.contains("Reusing configuration cache")
        postResult.output.contains("Configuration cache entry reused")
    }

    def "verify min supported Gradle version without config cache"() {
        given: "Build with the lowest supported Gradle/AGP version"
        def runner = GradleRunner.create()
                .withGradleVersion(BuildHelper.minSupportedGradleVersion.version)
                .withProjectDir(projectRootDir)
                .withEnvironment(localEnv)
                .withArguments(
                        "-Pnewrelic.agent.version=${agentVersion}",
                        "-Pnewrelic.agp.version=${BuildHelper.minSupportedAGPVersion.version}",
                        "-PagentRepo=${localEnv["M2_REPO"]}",
                        testTask)
        when:
        buildResult = runner.build()

        then:
        with(buildResult) {
            with(task(":$testTask")) {
                outcome == SUCCESS
            }
        }
    }

    def "verify unsupported configuration cache Gradle version"() {
        given: "Try to cache the task with an unsupported Gradle version"
        def runner = GradleRunner.create()
                .withGradleVersion(BuildHelper.minSupportedGradleConfigCacheVersion.version)
                .withProjectDir(projectRootDir)
                .withEnvironment(localEnv)
                .withArguments(
                        "-Pnewrelic.agent.version=${agentVersion}",
                        "-Pnewrelic.agp.version=4.0.0",
                        "-PagentRepo=${localEnv["M2_REPO"]}",
                        "--configuration-cache=ON",
                        "newRelicConfigRelease")
        when:
        buildResult = runner.build()

        then:
        UnexpectedBuildFailure e = thrown()
        e.message.contains("BUILD FAILED")
    }

    def "verify cached map uploads"() {
        given: "Rerun the cached the task"
        def runner = GradleRunner.create()
                .withGradleVersion(BuildHelper.minSupportedAGPConfigCacheVersion.version)
                .withProjectDir(projectRootDir)
                .withEnvironment(localEnv)
                .withArguments(
                        "-Pnewrelic.agent.version=${agentVersion}",
                        "-Pnewrelic.agp.version=${BuildHelper.minSupportedAGPConfigCacheVersion.version}",
                        "-PagentRepo=${localEnv["M2_REPO"]}",
                        "--configuration-cache",
                        "newrelicMapUploadMinifyReleaseWithR8")
        when:
        buildResult = runner.build()

        and:
        buildResult = runner.build()

        then:
        with(buildResult) {
            (output.contains("Reusing configuration cache") && output.contains("Configuration cache entry reused")) ||
                    (output.contains("configuration cache cannot be reused") && output.contains("Configuration cache entry stored"))
            task(":newrelicMapUploadMinifyReleaseWithR8").outcome == SUCCESS
        }
    }

    def "verify invalidated cached map uploads"() {
        given: "Rerun the cached the task"
        with(new File(buildDir, "outputs/mapping/release/mapping.txt")) {
            it.delete()
        }
        and:
        def runner = GradleRunner.create()
                .withGradleVersion(BuildHelper.minSupportedAGPConfigCacheVersion.version)
                .withProjectDir(projectRootDir)
                .withEnvironment(localEnv)
                .withArguments(
                        "-Pnewrelic.agent.version=${agentVersion}",
                        "-Pnewrelic.agp.version=${BuildHelper.minSupportedAGPConfigCacheVersion.version}",
                        "-PagentRepo=${localEnv["M2_REPO"]}",
                        "--configuration-cache",
                        "newrelicMapUploadMinifyReleaseWithR8")
        when:
        def buildResult = runner.build()

        then:
        buildResult.output.contains("Configuration cache entry stored")
        buildResult.task(":newrelicMapUploadMinifyReleaseWithR8").outcome == SUCCESS
    }

    def "verify min config cache supported agp/gradle"() {
        given: "Cache the task"
        def runner = GradleRunner.create()
                .withGradleVersion(BuildHelper.minSupportedAGPConfigCacheVersion.version)
                .withProjectDir(projectRootDir)
                .withEnvironment(localEnv)
                .withArguments(
                        "-Pnewrelic.agent.version=${agentVersion}",
                        "-Pnewrelic.agp.version=${BuildHelper.minSupportedAGPConfigCacheVersion.version}",
                        "-PagentRepo=${localEnv["M2_REPO"]}",
                        "--configuration-cache",
                        "newRelicConfigRelease")
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

    def "verify buildID is cached with config task"() {
        given: "Cache the config task with new build ID"
        def runner = GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(projectRootDir)
                .withEnvironment(localEnv)
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
        def preBuildId = new File(buildDir, "generated/source/newrelicConfig/release/com/newrelic/agent/android/NewRelicConfig.java").text

        and:
        def postResult = runner.build()

        and:
        def postBuildId = new File(buildDir, "generated/source/newrelicConfig/release/com/newrelic/agent/android/NewRelicConfig.java").text

        then:
        preBuildId.find(~/BUILD_ID = \"(.*)\".*/) == postBuildId.find(~/BUILD_ID = \"(.*)\".*/)

        preResult.output.contains("Calculating task graph as no configuration cache is available for tasks:")
        preResult.output.contains("Configuration cache entry stored")

        postResult.output.contains("Reusing configuration cache")
        postResult.output.contains("Configuration cache entry reused")
    }

    def "verify log level in agent options"() {
        given: "Pass log level in system property"
        def runner = GradleRunner.create()
                .forwardStdOutput(printFilter)
                .withGradleVersion(gradleVersion)
                .withProjectDir(projectRootDir)
                .withEnvironment(localEnv)
                .withArguments("--debug",
                        "-Dnewrelic.agent.args=\"loglevel=VERBOSE\"",
                        "-Pnewrelic.agent.version=${agentVersion}",
                        "-Pnewrelic.agp.version=${agpVersion}",
                        "-PagentRepo=${localEnv["M2_REPO"]}",
                        "newRelicConfigRelease")
        when:
        buildResult = runner.build()

        then:
        printFilter.toString().contains("loglevel=VERBOSE")
    }


    def cleanup() {
        with(new File(projectRootDir, ".gradle/configuration-cache")) {
            it.deleteDir()
        }
    }
}
