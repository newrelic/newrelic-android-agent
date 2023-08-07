/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android

import com.google.common.io.Files
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import spock.lang.*

abstract class PluginSpec extends Specification {

    static final rootDir = new File("../..")
    static final projectRootDir = new File(rootDir, "samples/Demo_SimpleNavigationCompose") // agent-test-app/")
    static final buildDir = new File(projectRootDir, "build")

    /* AGP/Gradle level 8+ require JDK 17 */
    static final minJdkVersion = 17
    static final agentVersion = System.getProperty("newrelic.agent.version", '7.+')
    static final agpVersion = System.getProperty("newrelic.agp.version", '8.0.+')
    static final gradleVersion = "8.2"

    @Shared
    def extensionsFile = new File(projectRootDir, "nr-extension.gradle")

    @Shared
    Map<String, String> localEnv = [:]

    @Shared
    def modules = [":library", ":feature"]

    @Shared
    BuildResult buildResult

    @Shared
    boolean debuggable = true

    @Shared
    def testTask = 'assembleRelease'

    @Shared
    def instrumentationVariants = ["release", "qa"]

    @Shared
    def mapUploadVariants = ["qa"]

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

        extensionsFile?.delete()
    }

    def setup() {
        printFilter = new PrintFilter()
        errorOutput = new StringWriter()
        extensionsFile = new File(projectRootDir, "nr-extension.gradle")

        extensionsFile.delete()
        extensionsFile.deleteOnExit()

        with(new File(projectRootDir, ".gradle/configuration-cache")) {
            it.deleteDir()
        }
    }

    def cleanup() {
        extensionsFile?.delete()
    }

    def provideRunner() {
        printFilter = new PrintFilter()
        errorOutput = new StringWriter()

        def runner = GradleRunner.create()
                .withProjectDir(projectRootDir)
                .withGradleVersion(gradleVersion)

        return debuggable ? runner.withDebug(true) : runner.withEnvironment(localEnv)
    }

}