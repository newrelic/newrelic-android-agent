/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import spock.lang.IgnoreIf
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

@IgnoreIf({ System.getProperty('regressionTests', 'dexguard') == 'dexguard' })
class PluginRegressionSpec extends Specification {

    static final rootDir = new File("../..")
    static final projectRootDir = new File(rootDir, "samples/agent-test-app/")
    static final buildDir = new File(projectRootDir, "build")
    static final agentVersion = '6.10.0'         // update as needed

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

    // fixtures
    def setup() {
        printFilter = new PrintFilter()
    }

    def setupSpec() {
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
    }

    @Unroll
    def "regress supported #agp/#gradle pair"() {
        /**
         * @https: //developer.android.com/studio/releases/gradle-plugin#updating-gradle
         * */
        expect: "run plugin using the AGP/Gradle combination"
        def printFilter = new PrintFilter()
        def runner = GradleRunner.create()
                .forwardStdOutput(printFilter)
                .withDebug(debuggable)
                .withProjectDir(projectRootDir)
                .withGradleVersion(gradle)
                .withArguments(
                        "-Pnewrelic.agent.version=${agentVersion}",
                        "-Pnewrelic.agp.version=${agp}",
                        "-PminifyEnabled=true",
                        "-PagentRepo=${localEnv["M2_REPO"]}",
                        "clean",
                        testTask)

        when:
        buildResult = runner.build()
        filteredOutput = printFilter

        then:
        testVariants.each { var ->
            buildResult.task(":assemble${var.capitalize()}").outcome == SUCCESS

            // must inject agent config as a class
            ['newrelicConfig'].each { task ->
                buildResult.task(":${task}${var.capitalize()}").outcome == SUCCESS
                def configTmpl = new File(buildDir,
                        "/generated/source/newrelicConfig/${var}/com/newrelic/agent/android/NewRelicConfig.java")
                configTmpl.exists() && configTmpl.canRead()

                def configClass = new File(buildDir, "/intermediates/javac/${var}/classes/com/newrelic/agent/android/NewRelicConfig.class")
                configClass.exists() && configClass.canRead()
            }

            with(new File(buildDir, "outputs/mapping/${var}/mapping.txt")) {
                exists()
                text.contains("# NR_BUILD_ID -> ")
            }
        }

        where:
        agp     | gradle
        '4.0.+' | '7.0.2'
        '4.1.+' | '7.0.2'
        '4.2.+' | '7.0.2'
        '7.0.+' | '7.0.2'
        '7.1.+' | '7.2'
        '7.2.+' | '7.3.3'
    }
}
