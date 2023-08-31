/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android

import com.google.common.io.Files
import com.newrelic.agent.android.obfuscation.Proguard
import spock.lang.IgnoreIf
import spock.lang.Requires
import spock.lang.Shared
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
@Requires({ jvm.isJavaVersionCompatible(minJdkVersion) })
class PluginDexGuardIntegrationSpec extends PluginSpec {

    static final minJdkVersion = 17
    static final dexguardPluginVersion = "9.4.+"

    /**
     *  According to GuardSquare, you should place your license file dexguard-license.txt
     *  - in a location defined by the Java system property 'dexguard.license',
     *  - in a location defined by the OS environment variable 'DEXGUARD_LICENSE',
     *  - in your home directory,
     *  - in the class path, or
     *  - in the same directory as the DexGuard jar (not working in combination with Gradle v3.1+).
     *
     */
    static final dexguardLicenseFile = System.getenv("DEXGUARD_LICENSE")
    static final dexguardMavenToken = System.getenv("DEXGUARD_MAVENTOKEN")

    @Shared
    def testTask = 'assemble'

    def setupSpec() {
        given: "create the build runner"
        def runner = provideRunner()
                .withGradleVersion(gradleVersion)
                .withArguments(
                        "-Pnewrelic.agent.version=${agentVersion}",
                        "-Pnewrelic.agp.version=${agpVersion}",
                        "-PagentRepo=${localEnv["M2_REPO"]}",
                        "-Pcompiler=dexguard",
                        "-PwithProductFlavors=false",
                        "-Ddexguard.license=${dexguardLicenseFile}",
                        "-PdexguardMavenToken=${dexguardMavenToken}",
                        "-Pdexguard.plugin.version=${dexguardPluginVersion}",
                        "--debug",
                        "clean",
                        testTask)

        /** Inject our test configuration. All assumptions used in tests are derived from this */
        Files.write(getClass().getResource("/gradle/nr-extension-dexguard.gradle").bytes, extensionsFile)

        when: "run the build *once* and use the cached results"
        buildResult = runner.build()
        filteredOutput = printFilter
    }

    def "build the test app with DexGuard"() {
        expect: "the test app was built"
        with(buildResult) {
            with(task(":${testTask}")) {
                outcome == SUCCESS
            }

            filteredOutput.contains("Android Gradle plugin version:")
            filteredOutput.contains("Gradle version:")
            filteredOutput.contains("Java version:")
            filteredOutput.contains("Kotlin version:")
            filteredOutput.contains("BuildMetrics[")
            filteredOutput.contains("DexGuard detected")
        }
    }

    def "verify NewRelicConfig was injected"() {
        expect:
        instrumentationVariants.each { var ->
            buildResult.task(":${NewRelicConfigTask.NAME}${var.capitalize()}").outcome == SUCCESS
            def configTmpl = new File(buildDir,
                    "generated/java/newrelicConfig${var.capitalize()}/com/newrelic/agent/android/NewRelicConfig.java")
            configTmpl.exists() && configTmpl.canRead()
            configTmpl.text.find(~/BUILD_ID = \"(.*)\".*/)

            def configClass = new File(buildDir, "intermediates/javac/${var}/classes/com/newrelic/agent/android/NewRelicConfig.class")
            configClass.exists() && configClass.canRead()
        }
    }

    void "verify class transforms"() {
        expect:
        instrumentationVariants.each { var ->
            buildResult.task(":${ClassTransformWrapperTask.NAME}${var.capitalize()}")?.outcome == SUCCESS
        }
    }

    def "verify map uploads"() {
        expect:
        filteredOutput.contains("Maps will be tagged and uploaded for variants [")
        mapUploadVariants.each { var ->
            buildResult.task(":newrelicMapUpload${var.capitalize()}").outcome == SUCCESS
            with(new File(buildDir, "outputs/dexguard/mapping/${var}/${var}-mapping.txt")) {
                exists()
                text.contains(Proguard.NR_MAP_PREFIX)
                filteredOutput.contains("Map file for variant [${var}] detected: [${getCanonicalPath()}]")
                filteredOutput.contains("Tagging map [${getCanonicalPath()}] with buildID [") ||
                        filteredOutput.contains("Map [${getCanonicalPath()}] has already been tagged")
            }
        }
    }

    def "verify submodules built and instrumented"() {
        expect:
        instrumentationVariants.each { var ->
            (buildResult.task(":library:transformClassesWith${NewRelicTransform.NAME.capitalize()}For${var.capitalize()}")?.outcome == SUCCESS ||
                    buildResult.task("library::${ClassTransformWrapperTask.NAME}${var.capitalize()}")?.outcome == SUCCESS)
            buildResult.task(":library:newrelicMapUpload${var.capitalize()}") == null
            buildResult.task(":library:dexguardAar${var.capitalize()}").outcome == SUCCESS
        }
    }

    def "verify dexguard apk instrumentation"() {
        expect:
        filteredOutput.contains("DexGuard detected")
        instrumentationVariants.each { var ->
            buildResult.task(":assemble${var.capitalize()}").outcome == SUCCESS
            buildResult.task(":dexguardApk${var.capitalize()}").outcome == SUCCESS
        }
        mapUploadVariants.each { var ->
            buildResult.task(":newrelicMapUpload${var.capitalize()}").outcome == SUCCESS
        }
    }

    def "verify dexguard mapping configuration"() {
        expect:
        mapUploadVariants.each { var ->
            buildResult.task(":newrelicMapUpload${var.capitalize()}").outcome == SUCCESS
        }

        ["release", "debug"].each { var ->
            buildResult.task(":newrelicMapUpload${var.capitalize()}") == null
        }
    }

    def "verify disabled mapping upload"() {
        expect:
        ["release"].each { var ->
            buildResult.task(":dexguardApk${var.capitalize()}").outcome == SUCCESS
            buildResult.task(":newrelicMapUpload${var.capitalize()}") == null
            with(new File(buildDir, "outputs/dexguard/mapping/apk/${var}/mapping.txt")) {
                !text.contains(Proguard.NR_MAP_PREFIX)
            }
        }
    }

    def "verify dexguard bundle instrumentation"() {
        given: "Build the app using DexGuard"
        def runner = provideRunner()
                .withGradleVersion(gradleVersion)
                .withArguments(
                        "-Pnewrelic.agent.version=${agentVersion}",
                        "-Pnewrelic.agp.version=${agpVersion}",
                        "-PagentRepo=${localEnv["M2_REPO"]}",
                        "-Pcompiler=dexguard",
                        "-Ddexguard.license=${dexguardLicenseFile}",
                        "-PdexguardMavenToken=${dexguardMavenToken}",
                        "-Pdexguard.plugin.version=${dexguardPluginVersion}",
                        "clean",
                        "bundle")

        when: "run the build and cache the results"
        buildResult = runner.build()
        filteredOutput = printFilter

        then:
        instrumentationVariants.each { var ->
            buildResult.task(":bundle${var.capitalize()}").outcome == SUCCESS
            buildResult.task(":dexguardAab${var.capitalize()}").outcome == SUCCESS
        }
        mapUploadVariants.each { var ->
            buildResult.task(":newrelicMapUpload${var.capitalize()}").outcome == SUCCESS
            with(new File(buildDir, "outputs/dexguard/mapping/bundle/release/mapping.txt")) {
                exists()
                !text.contains(Proguard.NR_MAP_PREFIX)
            }
            with(new File(buildDir, "outputs/dexguard/mapping/${var}/${var}-mapping.txt")) {
                exists()
                text.contains(Proguard.NR_MAP_PREFIX)
            }
        }
    }

}
