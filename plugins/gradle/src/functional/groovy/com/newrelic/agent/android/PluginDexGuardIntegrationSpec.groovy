/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android

import com.newrelic.agent.android.obfuscation.Proguard
import org.gradle.testkit.runner.GradleRunner
import spock.lang.IgnoreIf
import spock.lang.IgnoreRest
import spock.lang.Requires
import spock.lang.Stepwise

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
@IgnoreIf({ System.getProperty('integrations') != 'dexguard' })
@Requires({ jvm.isJavaVersionCompatible(PluginDexGuardIntegrationSpec.minJdkVersion) })
class PluginDexGuardIntegrationSpec extends PluginSpec {

    static final minJdkVersion = 11
    static final agentVersion = System.getProperty("newrelic.agent.version", '7.+')
    static final agpVersion = "7.+"
    static final gradleVersion = "7.6"
    static final dexguardBaseVersion = "9.3.+"

    /* According to GuardSquare, you should place your license file dexguard-license.txt
    1) in a location defined by the Java system property 'dexguard.license',
    2) in a location defined by the OS environment variable 'DEXGUARD_LICENSE',
    3) in your home directory,
    4) in the class path, or
    5) in the same directory as the DexGuard jar (not working in combination with Gradle v3.1+).
    */
    static final dexguardLicenseFile = System.getenv("DEXGUARD_LICENSE")
    static final dexguardMavenToken = System.getenv("DEXGUARD_MAVENTOKEN")

    def setup() {
        with(new File(projectRootDir, ".gradle/configuration-cache")) {
            it.deleteDir()
        }
        provideRunner()
                .withArguments("-Pnewrelic.agp.version=${agpVersion}", "clean")
                .build()
    }

    @IgnoreRest
    def "verify dexguard apk instrumentation"() {
        given: "Build the app using DexGuard"
        def runner = provideRunner()
                .withGradleVersion(gradleVersion)
                .withArguments(// "--debug",
                        "-Pnewrelic.agent.version=${agentVersion}",
                        "-Pnewrelic.agp.version=${agpVersion}",
                        "-PagentRepo=${localEnv["M2_REPO"]}",
                        "-Pcompiler=dexguard",
                        "-Pdexguard.base.version=${dexguardBaseVersion}",
                        "-Ddexguard.license=${dexguardLicenseFile}",
                        "-PdexguardMavenToken=${dexguardMavenToken}",
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
                text.contains(Proguard.NR_MAP_PREFIX)
                filteredOutput.contains("Map file for variant [${var}] detected: [${getCanonicalPath()}]")
                filteredOutput.contains("Tagging map [${getCanonicalPath()}] with buildID [")
            }
        }
    }

    def "verify dexguard bundle instrumentation"() {
        given: "Build the app using DexGuard"
        def runner = provideRunner()
                .withGradleVersion(gradleVersion)
                .withArguments("--debug",
                        "-Pnewrelic.agent.version=${agentVersion}",
                        "-Pnewrelic.agp.version=${agpVersion}",
                        "-PagentRepo=${localEnv["M2_REPO"]}",
                        "-Pcompiler=dexguard",
                        "-Pdexguard.base.version=${dexguardBaseVersion}",
                        "-Ddexguard.license=${dexguardLicenseFile}",
                        "-PdexguardMavenToken=${dexguardMavenToken}",
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
                text.contains(Proguard.NR_MAP_PREFIX)
                filteredOutput.contains("Map file for variant [${var}] detected: [${getCanonicalPath()}]")
                filteredOutput.contains("Tagging map [${getCanonicalPath()}] with buildID [")
            }
        }
    }

    def "verify dexguard mapping configuration"() {
        given: "Build the app using DexGuard"
        def runner = provideRunner()
                .withGradleVersion(gradleVersion)
                .withArguments("--debug",
                        "-Pnewrelic.agent.version=${agentVersion}",
                        "-Pnewrelic.agp.version=${agpVersion}",
                        "-PagentRepo=${localEnv["M2_REPO"]}",
                        "-Pcompiler=dexguard",
                        "-Pdexguard.base.version=${dexguardBaseVersion}",
                        "-Ddexguard.license=${dexguardLicenseFile}",
                        "-PdexguardMavenToken=${dexguardMavenToken}",
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
                text.contains(Proguard.NR_MAP_PREFIX)
                filteredOutput.contains("Map file for variant [${var}] detected: [${getCanonicalPath()}]")
                filteredOutput.contains("Tagging map [${getCanonicalPath()}] with buildID [")
            }
        }
    }

    def "verify disabled mapping upload"() {
        given: "Build the app using DexGuard"
        def runner = provideRunner()
                .withGradleVersion(gradleVersion)
                .withArguments("--debug",
                        "-Pnewrelic.agent.version=${agentVersion}",
                        "-Pnewrelic.agp.version=${agpVersion}",
                        "-PagentRepo=${localEnv["M2_REPO"]}",
                        "-Pcompiler=dexguard",
                        "-Pdexguard.base.version=${dexguardBaseVersion}",
                        "-Ddexguard.license=${dexguardLicenseFile}",
                        "-PdexguardMavenToken=${dexguardMavenToken}",
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
                !text.contains(Proguard.NR_MAP_PREFIX)
                !filteredOutput.contains("Map file for variant [${var}] detected: [${getCanonicalPath()}]")
                !filteredOutput.contains("Tagging map [${getCanonicalPath()}] with buildID [")
            }
        }
    }
}
