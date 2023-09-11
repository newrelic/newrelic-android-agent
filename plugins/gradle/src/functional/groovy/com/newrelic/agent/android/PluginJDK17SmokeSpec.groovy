/*
 * Copyright (c) 2023 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android

import com.newrelic.agent.android.obfuscation.Proguard
import spock.lang.Requires
import spock.lang.Shared

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

@Requires({ jvm.isJava17Compatible() })
class PluginJDK17SmokeSpec extends PluginSpec {

    /* Last levels for JDK 11. Must use JDK 17 for AGP/Gradle 8.+    */
    static final jdkVersion = 17
    static final agpVersion = "8.1.+"
    static final gradleVersion = "8.2"

    def setup() {
        with(new File(projectRootDir, ".gradle/configuration-cache")) {
            it.deleteDir()
        }
    }

    @Shared
    def testVariants = ['googleRelease']

    @Shared
    def mapUploadVariants = ["amazonQa"]

    def setupSpec() {
        given: "create the build runner"
        def runner = provideRunner()
                .withGradleVersion(gradleVersion)
                .withArguments(
                        "-Pnewrelic.agent.version=${agentVersion}",
                        "-Pnewrelic.agp.version=${agpVersion}",
                        "-Pcompiler=r8",
                        "-PagentRepo=${localEnv["M2_REPO"]}",
                        "-PwithProductFlavors=true",
                        "--debug",
                        "clean",
                        testTask, "assembleQa")

        when: "run the build *once* and cache the results"
        buildResult = runner.build()
        filteredOutput = printFilter
    }

    @Requires({ jvm.isJavaVersionCompatible(jdkVersion) })
    def "build the test app"() {
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
        }
    }

    def "verify NewRelicConfig was injected"() {
        expect:
        testVariants.each { var ->
            buildResult.task(":${NewRelicConfigTask.NAME}${var.capitalize()}").outcome == SUCCESS
            def configTmpl = new File(buildDir,
                    "/generated/java/newrelicConfig${var.capitalize()}/com/newrelic/agent/android/NewRelicConfig.java")
            configTmpl.exists() && configTmpl.canRead()
            configTmpl.text.find(~/BUILD_ID = \"(.*)\".*/)
            configTmpl.text.contains("Boolean OBFUSCATED = true;")

            def configClass = new File(buildDir, "/intermediates/javac/${var}/classes/com/newrelic/agent/android/NewRelicConfig.class")
            configClass.exists() && configClass.canRead()
        }
    }

    void "verify class transforms"() {
        expect:
        testVariants.each { var ->
            buildResult.task(":${ClassTransformWrapperTask.NAME}${var.capitalize()}")?.outcome == SUCCESS
        }
    }

    def "verify map uploads"() {
        expect:
        filteredOutput.contains("Maps will be tagged and uploaded for variants [")

        mapUploadVariants.each { var ->
            buildResult.task(":newrelicMapUpload${var.capitalize()}").outcome == SUCCESS
            with(new File(buildDir, "outputs/mapping/${var}/mapping.txt")) {
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
        testVariants.each { var ->
            (buildResult.task(":library:transformClassesWith${NewRelicTransform.NAME.capitalize()}For${var.capitalize()}")?.outcome == SUCCESS ||
                    buildResult.task("library::${ClassTransformWrapperTask.NAME}${var.capitalize()}")?.outcome == SUCCESS)
        }
        mapUploadVariants.each { var ->
            buildResult.task(":library:newrelicMapUpload${var.capitalize()}").outcome == SUCCESS
        }
    }
}