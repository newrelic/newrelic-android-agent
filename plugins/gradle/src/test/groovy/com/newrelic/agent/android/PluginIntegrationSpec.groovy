/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.UnexpectedBuildFailure
import spock.lang.*

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS
import static org.gradle.testkit.runner.TaskOutcome.UP_TO_DATE

@Stepwise
@IgnoreIf({ System.getProperty('integrationTests', 'dexguard') == 'dexguard' })
class PluginIntegrationSpec extends Specification {

    static final rootDir = new File("../..")
    static final projectRootDir = new File(rootDir, "samples/agent-test-app/")
    static final buildDir = new File(projectRootDir, "build")

    // update as needed
    static final agentVersion = System.getProperty("newrelic.agent.version", '6.+')
    static final agpVersion = "7.1.+"
    static final gradleVersion = "7.2"

    def extensionsFile = new File(projectRootDir, "nr-extension.gradle")

    @Shared
    Map<String, String> localEnv = [:]

    @Shared
    BuildResult buildResult

    @Shared
    boolean debuggable = false

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
        extensionsFile = new File(projectRootDir, "nr-extension.gradle")
    }

    def setupSpec() {
        given: "verify M2 repo location"
        localEnv += System.getenv()
        if (localEnv["M2_REPO"] == null) {
            def m2 = new File(rootDir, "build/.m2/repository").absoluteFile
            try {
            if (!(m2.exists() && m2.canRead())) {
                    provideRunner()
                        .withProjectDir(rootDir)
                            .withArguments("publish")
                        .build()
                if (!(m2.exists() && m2.canRead())) {
                    throw new IOException("M2_REPO not found. Run `./gradlew publish` to stage the agent")
                }
            }
            localEnv.put("M2_REPO", m2.getAbsolutePath())
            } catch (Exception e) {
                e
            }
        }

        and: "create the build runner"
        def runner = provideRunner()
                .withArguments("--debug",
                        "-Pnewrelic.agent.version=${agentVersion}",
                        "-Pnewrelic.agp.version=${agpVersion}",
                        "-Pcompiler=r8",
                        "-PagentRepo=${localEnv["M2_REPO"]}",
                        "clean",
                        testTask)

        when: "run the build *once* and cache the results"
        try {
        buildResult = runner.build()
        filteredOutput = printFilter
        } catch (Exception e) {
            throw e
        }
    }

    @IgnoreRest
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
        given: "Build with AGP/Gradle 7.2, or the last AGP/Gradle pair that supports the Transform API"
        def runner = provideRunner()
                .withGradleVersion("7.2")
                .withArguments(
                        "-Pnewrelic.agent.version=${agentVersion}",
                        "-Pnewrelic.agp.version=${BuildHelper.minSupportedAGPVersion}",
                        "-PagentRepo=${localEnv["M2_REPO"]}",
                        testTask)
        when:
        buildResult = runner.build()

        then:
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
        def runner = provideRunner()
                .withArguments(
                        "-Pnewrelic.agent.version=${agentVersion}",
                        "-Pnewrelic.agp.version=${agpVersion}",
                        "-PagentRepo=${localEnv["M2_REPO"]}",
                        testTask)

        when:
        buildResult = runner.build()

        then:
        // rerun the build use local results
        testVariants.each { var ->
            ['newrelicConfig'].each { taskName ->
                buildResult.task(":${taskName}${var.capitalize()}").outcome == UP_TO_DATE
            }
        }
    }

    def "test min supported AGP version"() {
        given: "Build the app using the minimum supported Gradle version"

        // rerun the build use local results
        def runner = provideRunner()
                .withArguments(
                        "-Pnewrelic.agent.version=${agentVersion}",
                        "-Pnewrelic.agp.version=${BuildHelper.minSupportedAGPVersion}",
                        "-PagentRepo=${localEnv["M2_REPO"]}",
                        testTask)

        when:
        buildResult = runner.build()

        then:
        with(buildResult.task(":$testTask")) {
            outcome == SUCCESS
        }
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
                        testTask)

        when:
        buildResult = runner.build()

        then:
        with(buildResult.task(":$testTask")) {
            outcome == SUCCESS
        }
    }

    def "verify unsupported Gradle version"() {
        given: "Apply an unsupported Gradle version"
        def runner = provideRunner()
                .withGradleVersion("6.7.1")
                .withArguments(
                        "-Pnewrelic.agent.version=${agentVersion}",
                        "-Pnewrelic.agp.version=3.+",
                        "-PagentRepo=${localEnv["M2_REPO"]}")

        when:
        try {
            buildResult = runner.build()
        } catch (Exception) {
        }

        then:
        errorOutput.toString().contains("BUILD FAILED") ||
                buildResult.output.contains("The New Relic plugin may not be compatible with Gradle version")
    }

    def "verify unsupported AGP version"() {
        given: "Apply an unsupported AGP version: AGP 3.6.+ Gradle 5.6.4"
        def runner = provideRunner()
                .withArguments(
                        "-Pnewrelic.agent.version=${agentVersion}",
                        "-Pnewrelic.agp.version=3.+",
                        "-PagentRepo=${localEnv["M2_REPO"]}")

        when:
        try {
            buildResult = runner.build()
        } catch (Exception) {
        }

        then:
        errorOutput.toString().contains("The New Relic plugin is not compatible") ||
                buildResult.output.contains("Could not find method registerJavaGeneratingTask() for arguments [provider(task 'newrelicConfig")
    }

    def "verify pre-release AGP version check"() {
        given: "Apply an unsupported AGP version"
        def runner = provideRunner()
                .withGradleVersion("7.2")
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

    def "verify configuration cache compatibility"() {
        given: "Cache the config task"
        def runner = provideRunner()
                .withGradleVersion(BuildHelper.minSupportedGradleVersion)
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
            with(task(":$testTask")) {
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

    def "verify cached map uploads"() {
        given: "Rerun the cached the task"
        def runner = provideRunner()
                .withGradleVersion(BuildHelper.minSupportedAGPConfigCacheVersion)
                .withArguments(
                        "-Pnewrelic.agent.version=${agentVersion}",
                        "-Pnewrelic.agp.version=${BuildHelper.minSupportedAGPConfigCacheVersion}",
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
        def runner = provideRunner()
                .withGradleVersion(BuildHelper.minSupportedAGPConfigCacheVersion)
                .withArguments(
                        "-Pnewrelic.agent.version=${agentVersion}",
                        "-Pnewrelic.agp.version=${BuildHelper.minSupportedAGPConfigCacheVersion}",
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
        def runner = provideRunner()
                .withGradleVersion(BuildHelper.minSupportedAGPConfigCacheVersion)
                .withArguments(
                        "-Pnewrelic.agent.version=${agentVersion}",
                        "-Pnewrelic.agp.version=${BuildHelper.minSupportedAGPConfigCacheVersion}",
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
        def runner = provideRunner()
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

    def "verify AGP8"() {
        given: "Apply AGP8"
        def runner = provideRunner()
                .withGradleVersion("7.5.1")
                .withArguments(
                        "-Pnewrelic.agent.version=${agentVersion}",
                        "-Pnewrelic.agp.version=7.4.+",
                        "-PagentRepo=${localEnv["M2_REPO"]}",
                        "clean")

        when: "run the build"
        buildResult = runner.build()

        then:
        buildResult.output.contains("FIXME")
    }

    def cleanup() {
        extensionsFile?.delete()
        with(new File(projectRootDir, ".gradle/configuration-cache")) {
            it.deleteDir()
        }
    }

    def provideRunner() {
        printFilter = new PrintFilter()
        errorOutput = new StringWriter()

        def runner = GradleRunner.create()
                .withProjectDir(projectRootDir)
                .forwardStdOutput(printFilter)
                .forwardStdError(errorOutput)
                .withGradleVersion(gradleVersion)

        return debuggable ? runner.withDebug(debuggable) : runner.withEnvironment(localEnv)
    }

}