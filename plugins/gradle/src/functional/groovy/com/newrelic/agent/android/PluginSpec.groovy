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

abstract class PluginSpec extends Specification {

    static final rootDir = new File("../..")
    static final projectRootDir = new File(rootDir, "samples/agent-test-app/")
    static final buildDir = new File(projectRootDir, "build")

    /* AGP/Gradle level 8+ require JDK 17 */
    static final minJdkVersion = 17
    static final agentVersion = System.getProperty("newrelic.agent.version", '7.+')
    static final agpVersion = "8.0.+"
    static final gradleVersion = "8.1.1"

    def extensionsFile = new File(projectRootDir, "nr-extension.gradle")

    @Shared
    Map<String, String> localEnv = [:]

    @Shared
    def modules = [":library"]

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
    def setupSpec() {
        given: "verify M2 repo location"
        localEnv += System.getenv()

        if (localEnv["M2_REPO"] == null) {
            def m2 = new File(rootDir, "build/.m2/repository").absoluteFile
            try {
                if (!(m2.exists() && m2.canRead())) {
                    provideRunner()
                            .withProjectDir(rootDir)
                            .withArguments("publish", "install")
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

    def setup() {
        printFilter = new PrintFilter()
        errorOutput = new StringWriter()
        extensionsFile = new File(projectRootDir, "nr-extension.gradle")

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