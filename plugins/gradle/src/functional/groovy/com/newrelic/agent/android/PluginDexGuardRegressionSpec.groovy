/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android

import com.newrelic.agent.android.obfuscation.Proguard
import spock.lang.*

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

/**
 * Dexguard Regressions Tests
 *
 * Running DexGuard integration tests require additional resources:
 * 1. a valid DexGuard license (from GuardSquare user account)
 * 2. a valid GuardSquare Maven repo app token (also from GuardSquare user account)
 * 3. a collection of legacy Dexguard archives (for legacy DG test)
 *
 */
@Stepwise
@IgnoreIf({ System.getProperty('regressions') != 'dexguard' })
@Requires({ jvm.isJavaVersionCompatible(minJdkVersion) })
class PluginDexGuardRegressionSpec extends PluginSpec {

    static final minJdkVersion = 17

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

    @Unroll
    def "verify DexGuard [#dexguardPluginVersion/#agp/#gradle] instrumentation"() {
        given: "Build the app using DexGuard"
        def runner = provideRunner()
                .withGradleVersion(gradle)
                .withArguments(
                        "-Pnewrelic.agent.version=${agentVersion}",
                        "-Pnewrelic.agp.version=${agp}",
                        "-PagentRepo=${localEnv["M2_REPO"]}",
                        "-Pcompiler=dexguard",
                        "-Pdexguard.plugin.version=${dexguardPluginVersion}",
                        "-Ddexguard.license=${dexguardLicenseFile}",
                        "-PdexguardMavenToken=${dexguardMavenToken}",
                        "--debug",
                        "clean",
                        testTask)

        when: "run the build and cache the results"
        buildResult = runner.build()
        filteredOutput = printFilter

        then:
        filteredOutput.contains("DexGuard detected")

        instrumentationVariants.each { var ->
            buildResult.task(":assemble${var.capitalize()}").outcome == SUCCESS
            buildResult.task(":${DexGuardHelper.DEXGUARD_APK_TASK}${var.capitalize()}").outcome == SUCCESS
            buildResult.task(":${NewRelicConfigTask.NAME}${var.capitalize()}").outcome == SUCCESS

            def configTmpl = new File(buildDir,
                    "generated/java/newrelicConfig${var.capitalize()}/com/newrelic/agent/android/NewRelicConfig.java")

            configTmpl.exists() && configTmpl.canRead()
            configTmpl.text.find(~/BUILD_ID = \"(.*)\".*/)
            configTmpl.text.contains("MINIFIED = true;")

            def configClass = new File(buildDir, "intermediates/javac/${var}/classes/com/newrelic/agent/android/NewRelicConfig.class")
            configClass.exists() && configClass.canRead()
        }

        mapUploadVariants.each { var ->
            buildResult.task(":newrelicMapUpload${var.capitalize()}")?.outcome == SUCCESS ||
                    buildResult.task(":newrelicMapUploadDexguard${var.capitalize()}")?.outcome == SUCCESS
            with(new File(buildDir, "outputs/dexguard/mapping/${var}/${var}-mapping.txt")) {
                exists()
                text.contains(Proguard.NR_MAP_PREFIX)
                filteredOutput.contains("Map file for variant [${var}] detected: [${getCanonicalPath()}]")
                filteredOutput.contains("Tagging map [${getCanonicalPath()}] with buildID [") ||
                        filteredOutput.contains("Map [${getCanonicalPath()}] has already been tagged")
            }
        }

        where:
        dexguardPluginVersion | agp     | gradle
        '9.4.0'               | '7.+'   | '7.6'
        '9.4.+'               | '7.+'   | '7.6'
        '9.4.+'               | '8.0.+' | '8.1'
        '9.+'                 | '8.1.+' | '8.2'
    }

}
