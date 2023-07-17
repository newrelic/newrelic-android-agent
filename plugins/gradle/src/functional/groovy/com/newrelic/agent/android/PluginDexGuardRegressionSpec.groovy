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
class PluginDexGuardRegressionSpec extends PluginRegressionSpec {

   static final dexguardHome                   // = /path/to/dexguard/artifacts"

    /* According to GuardSquare, you should place your license file dexguard-license.txt
    1) in a location defined by the Java system property 'dexguard.license',
    2) in a location defined by the OS environment variable 'DEXGUARD_LICENSE',
    3) in your home directory,
    4) in the class path, or
    5) in the same directory as the DexGuard jar (not working in combination with Gradle v3.1+).
    */
    static final dexguardLicenseFile            // = "/path/to/license"

    @Unroll
    def "verify legacy #base/#plugin/#agp/#gradle instrumentation"() {
        given: "Build the app using DexGuard"
        def runner = provideRunner()
                .withGradleVersion(gradle)
                .withArguments("--debug",
                        "-Pnewrelic.agent.version=${agentVersion}",
                        "-Pnewrelic.agp.version=${agp}",
                        "-Pcompiler=dexguardLegacy",
                        "-Pdexguard.home=${dexguardHome}",
                        "-Pdexguard.base.version=${base}",
                        "-Pdexguard.plugin.version=${plugin}",
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
            buildResult.task(":dexguard${var.capitalize()}").outcome == SUCCESS
            buildResult.task(":newrelicMapUploadDexguard${var.capitalize()}").outcome == SUCCESS
            with(new File(buildDir, "outputs/mapping/${var}/mapping.txt")) {
                exists()
                text.contains(Proguard.NR_MAP_PREFIX)
                filteredOutput.contains("Map file for variant [${var}] detected: [${getCanonicalPath()}]")
                filteredOutput.contains("Tagging map [${getCanonicalPath()}] with buildID [")
            }
        }

        where:
        base     | plugin | agp     | gradle
        '8.7.09' | ''     | '3.6.+' | '5.6.4'     // highest support by DG8
        '9.3.6'  | '1.+'  | '4.+'   | '6.7.1'
        '9.3.6'  | '1.+'  | '7.1.+' | '7.2'
    }

}
