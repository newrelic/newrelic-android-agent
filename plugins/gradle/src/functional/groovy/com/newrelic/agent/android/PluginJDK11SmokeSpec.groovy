/*
 * Copyright (c) 2023 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android

import com.newrelic.agent.android.obfuscation.Proguard
import spock.lang.Requires
import spock.lang.Retry
import spock.lang.Shared

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS
import static org.gradle.testkit.runner.TaskOutcome.UP_TO_DATE

@Retry(delay = 15000, count = 2)
class PluginJDK11SmokeSpec extends PluginSpec {

    /* Last levels for JDK 11. Must use JDK 17 for AGP/Gradle 8.+    */
    static final jdkVersion = 11
    static final agpVersion = "7.4.+"
    static final gradleVersion = "7.6.1"

    @Shared
    def testFlavors = ['google', 'amazon']

    @Shared
    def buildTypes = ['debug', 'qa', 'release']

    def setupSpec() {
        given: "create the build runner"
        with(new File(projectRootDir, ".gradle/configuration-cache")) {
            it.deleteDir()
        }

        def runner = provideRunner()
                .withGradleVersion(gradleVersion)
                .forwardStdOutput(printFilter)
                .withArguments(
                        "-Pnewrelic.agent.version=${agentVersion}",
                        "-Pnewrelic.agp.version=${agpVersion}",
                        "-Pcompiler=r8",
                        "-PagentRepo=${localEnv["M2_REPO"]}",
                        "-PwithProductFlavors=true",
                        "-PincludeLibrary=false",
                        // "-PincludeFeature=true",
                        "--debug",
                        "clean",
                        testTask)

        when: "run the build *once* and cache the results"
        buildResult = runner.build()
        filteredOutput = printFilter

        modules = [':library']
    }

    @Requires({ jvm.isJavaVersionCompatible(jdkVersion) })
    def "build the test app"() {
        expect: "the test app was built"
        with(buildResult) {
            with(task(":clean")) {
                outcome == SUCCESS || outcome == UP_TO_DATE
            }
            with(task(":${testTask}")) {
                outcome == SUCCESS
            }

            filteredOutput.contains("Android Gradle plugin version:")
            filteredOutput.contains("Gradle version:")
            filteredOutput.contains("Java version:")
            filteredOutput.contains("Kotlin version:")
        }
    }

    def "verify NewRelicConfig was injected"() {
        expect:
        testFlavors.each { flavor ->
            buildTypes.each { buildType ->
                // 'debug' is excluded from instrumentation
                if ('debug'.equals(buildType)) {
                    return
                }

                def var = "${flavor.capitalize()}${buildType.capitalize()}"
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
    }

    void "verify class transforms"() {
        expect:
        testFlavors.each { flavor ->
            buildTypes.each { buildType ->
                // 'debug' is excluded from instrumentation
                if ('debug'.equals(buildType)) {
                    return
                }

                def var = "${flavor.capitalize()}${buildType.capitalize()}"
                (buildResult.task(":transformClassesWith${NewRelicTransform.NAME.capitalize()}For${var.capitalize()}")?.outcome == SUCCESS ||
                        buildResult.task(":${ClassTransformWrapperTask.NAME}${var.capitalize()}")?.outcome == SUCCESS)
            }
        }
    }

    def "verify map uploads"() {
        expect:
        filteredOutput.contains("Maps will be tagged and uploaded for variants [")
        testFlavors.each { flavor ->
            buildTypes.each { buildType ->
                // 'qa' is only variant in map uploads
                if (!'qa'.equals(buildType)) {
                    return
                }

                def var = "${flavor}${buildType.capitalize()}"
                buildResult.task(":newrelicMapUpload${var.capitalize()}").outcome == SUCCESS
                with(new File(buildDir, "outputs/mapping/${var}/mapping.txt")) {
                    exists()
                    text.contains(Proguard.NR_MAP_PREFIX)
                    filteredOutput.contains("Map file for variant [${var}] detected: [${getCanonicalPath()}]")
                }
            }
        }
    }

    def "verify submodules built and instrumented"() {
        expect:
        testFlavors.each { flavor ->
            buildTypes.each { buildType ->
                def var = "${flavor.capitalize()}${buildType.capitalize()}"

                modules.each { module ->
                    (buildResult.task(":${modules}:transformClassesWith${NewRelicTransform.NAME.capitalize()}For${var.capitalize()}")?.outcome == SUCCESS ||
                            buildResult.task("${module}::${ClassTransformWrapperTask.NAME}${var.capitalize()}")?.outcome == SUCCESS)
                    buildResult.task(":${module}:newrelicMapUpload${var.capitalize()}").outcome == SUCCESS
                }
            }
        }
    }

    def "verify submodules built and instrumented"() {
        expect:
        testFlavors.each { flavor ->
            buildTypes.each { buildType ->
                def var = "${flavor.capitalize()}${buildType.capitalize()}"

                (buildResult.task(":library:transformClassesWith${NewRelicTransform.NAME.capitalize()}For${var.capitalize()}")?.outcome == SUCCESS ||
                        buildResult.task("library::${ClassTransformWrapperTask.NAME}${var.capitalize()}")?.outcome == SUCCESS)
                buildResult.task(":library:newrelicMapUpload${var.capitalize()}").outcome == SUCCESS
            }
        }
    }

    def "verify package exclusion"() {
        filteredOutput.contains("Package [org/bouncycastle/crypto] has been excluded from instrumentation");
    }

}