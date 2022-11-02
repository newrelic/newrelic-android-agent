/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import spock.lang.Requires
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise
import spock.lang.Unroll

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

/**
 * Dexguard Integrations Tests
 *
 * Running DexGuard integration tests require additional resources:
 * 1. a valid DexGuard license (from GuardSquare user account)
 * 2. a valid GuardSquare Maven repo app token (also from GuardSquare user account)
 * 3. a collection of legacy Dexguard archives (for legacy DG test)
 *
 */
@Stepwise
@Requires({ System.getProperty('integrationTests', '') == 'dexguard' })
class PluginDexGuardIntegrationSpec extends Specification {

    static final rootDir = new File("../..")
    static final projectRootDir = new File(rootDir, "agent-test-app/")
    static final buildDir = new File(projectRootDir, "build")
    static final debuggable = true             // set to true to break in plugin/Gradle code

    // Current values (update as needed)
    static final agentVersion = "6.6.0"
    static final agpVersion = "4.+"
    static final gradleVersion = "6.7.1"
    static final dexguardBaseVersion = "9.3.7"
    static final dexguardPluginVersion = "1.+"
    static final dexguardHome                   // = /path/to/dexguard/artifacts"

    /* According to GuardSquare, you should place your license file dexguard-license.txt
    1) in a location defined by the Java system property 'dexguard.license',
    2) in a location defined by the OS environment variable 'DEXGUARD_LICENSE',
    3) in your home directory,
    4) in the class path, or
    5) in the same directory as the DexGuard jar (not working in combination with Gradle v3.1+).
    */
    static final dexguardLicenseFile            // = "/path/to/license"

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
    }

    def "verify dexguard apk instrumentation"() {
        given: "Build the app using DexGuard"
        def runner = GradleRunner.create()
                .forwardStdOutput(printFilter)
                .withDebug(debuggable)
                .withGradleVersion(gradleVersion)
                .withProjectDir(projectRootDir)
                .withArguments("--debug",
                        "-Pnewrelic.agent.version=${agentVersion}",
                        "-Pnewrelic.agp.version=${agpVersion}",
                        "-Pcompiler=dexguard",
                        "-Pdexguard.base.version=${dexguardBaseVersion}",
                        "-Pdexguard.plugin.version=${dexguardPluginVersion}",
                        "-PagentRepo=${localEnv["M2_REPO"]}",
                        "-Ddexguard.license=${dexguardLicenseFile}",
                        testTask)

        when: "run the build and cache the results"
        buildResult = runner.build()
        filteredOutput = printFilter

        then:
        filteredOutput.contains("DexGuard detected")

        testVariants.each { var ->
            buildResult.task(":assemble${var.capitalize()}").outcome == SUCCESS
            buildResult.task(":dexguardApk${var.capitalize()}").outcome == SUCCESS
            buildResult.task(":newrelicMapUploadDexguardApk${var.capitalize()}").outcome == SUCCESS
            with(new File(buildDir, "outputs/dexguard/mapping/apk/${var}/mapping.txt")) {
                exists()
                text.contains("# NR_BUILD_ID -> ")
                filteredOutput.contains("Map file for variant [${var}] detected: [${getCanonicalPath()}]")
                filteredOutput.contains("Tagging map [${getCanonicalPath()}] with buildID [")
            }
        }
    }

    def "verify dexguard bundle instrumentation"() {
        given: "Build the app using DexGuard"
        def runner = GradleRunner.create()
                .forwardStdOutput(printFilter)
                .withDebug(debuggable)
                .withGradleVersion(gradleVersion)
                .withProjectDir(projectRootDir)
                .withArguments("--debug",
                        "-Pnewrelic.agent.version=${agentVersion}",
                        "-Pnewrelic.agp.version=${agpVersion}",
                        "-Pcompiler=dexguard",
                        "-Pdexguard.base.version=${dexguardBaseVersion}",
                        "-Pdexguard.plugin.version=${dexguardPluginVersion}",
                        "-PagentRepo=${localEnv["M2_REPO"]}",
                        "-Ddexguard.license=${dexguardLicenseFile}",
                        "bundleR")

        when: "run the build and cache the results"
        buildResult = runner.build()
        filteredOutput = printFilter

        then:
        filteredOutput.contains("DexGuard detected")

        testVariants.each { var ->
            buildResult.task(":bundle${var.capitalize()}").outcome == SUCCESS
            buildResult.task(":dexguardAab${var.capitalize()}").outcome == SUCCESS
            buildResult.task(":newrelicMapUploadDexguardAab${var.capitalize()}").outcome == SUCCESS
            with(new File(buildDir, "outputs/dexguard/mapping/bundle/${var}/mapping.txt")) {
                exists()
                text.contains("# NR_BUILD_ID -> ")
                filteredOutput.contains("Map file for variant [${var}] detected: [${getCanonicalPath()}]")
                filteredOutput.contains("Tagging map [${getCanonicalPath()}] with buildID [")
            }
        }
    }

    def "verify dexguard mapping configuration"() {
        given: "Build the app using DexGuard"
        def runner = GradleRunner.create()
                .forwardStdOutput(printFilter)
                .withDebug(debuggable)
                .withGradleVersion(gradleVersion)
                .withProjectDir(projectRootDir)
                .withArguments("--debug",
                        "-Pnewrelic.agent.version=${agentVersion}",
                        "-Pnewrelic.agp.version=${agpVersion}",
                        "-Pcompiler=dexguard",
                        "-Pdexguard.base.version=${dexguardBaseVersion}",
                        "-Pdexguard.plugin.version=${dexguardPluginVersion}",
                        "-PagentRepo=${localEnv["M2_REPO"]}",
                        "-Ddexguard.license=${dexguardLicenseFile}",
                        "assembleQa")

        when: "run the build and cache the results"
        buildResult = runner.build()
        filteredOutput = printFilter

        then:
        filteredOutput.contains("DexGuard detected")

        ["qa"].each { var ->
            buildResult.task(":assemble${var.capitalize()}").outcome == SUCCESS
            buildResult.task(":dexguardApk${var.capitalize()}").outcome == SUCCESS
            buildResult.task(":newrelicMapUploadDexguardApk${var.capitalize()}").outcome == SUCCESS
            with(new File(buildDir, "outputs/dexguard/mapping/${var}/qa-mapping.txt")) {
                exists()
                text.contains("# NR_BUILD_ID -> ")
                filteredOutput.contains("Map file for variant [${var}] detected: [${getCanonicalPath()}]")
                filteredOutput.contains("Tagging map [${getCanonicalPath()}] with buildID [")
            }
        }
    }

    def "verify disabled mapping upload"() {
        given: "Build the app using DexGuard"
        def runner = GradleRunner.create()
                .forwardStdOutput(printFilter)
                .withDebug(debuggable)
                .withGradleVersion(gradleVersion)
                .withProjectDir(projectRootDir)
                .withArguments("--debug",
                        "-Pnewrelic.agent.version=${agentVersion}",
                        "-Pnewrelic.agp.version=${agpVersion}",
                        "-Pcompiler=dexguard",
                        "-Pdexguard.base.version=${dexguardBaseVersion}",
                        "-Pdexguard.plugin.version=${dexguardPluginVersion}",
                        "-PagentRepo=${localEnv["M2_REPO"]}",
                        "-Ddexguard.license=${dexguardLicenseFile}",
                        "assembleD")

        when: "run the build and cache the results"
        buildResult = runner.build()
        filteredOutput = printFilter

        then:
        ["debug"].each { var ->
            buildResult.task(":assemble${var.capitalize()}").outcome == SUCCESS
            buildResult.task(":dexguardApk${var.capitalize()}").outcome == SUCCESS
            !buildResult.task(":newrelicMapUploadDexguardApk${var.capitalize()}")
            with(new File(buildDir, "outputs/dexguard/mapping/apk/${var}/mapping.txt")) {
                exists()
                !text.contains("# NR_BUILD_ID -> ")
                !filteredOutput.contains("Map file for variant [${var}] detected: [${getCanonicalPath()}]")
                !filteredOutput.contains("Tagging map [${getCanonicalPath()}] with buildID [")
            }
        }
    }
}
