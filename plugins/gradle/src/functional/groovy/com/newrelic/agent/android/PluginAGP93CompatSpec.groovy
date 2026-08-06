/*
 * Copyright (c) 2026 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android

import com.newrelic.agent.android.obfuscation.Proguard
import spock.lang.Requires
import spock.lang.Shared

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

/**
 * Compatibility coverage for the AGP 9.3.x support ceiling (NR-586734).
 *
 * Pins AGP 9.3.0 / Gradle 9.5.0 (minifyEnabled=true, compiler=r8) and asserts the AGP90Adapter
 * path instruments and re-emits classes correctly:
 *   - NewRelicConfigTask (build id / source + resource generation)
 *   - ClassTransformWrapperTask (ScopedArtifacts.ALL transform)
 *   - minify<Variant>WithR8 map-upload wiring
 * It also guards {@link BuildHelper#maxSupportedAGPVersion} against regressing below 9.3.0,
 * which would re-introduce the "may not be compatible" warning for this toolchain.
 */
@Requires({ jvm.isJava17Compatible() })
class PluginAGP93CompatSpec extends PluginSpec {

    static final agpVersion = "9.3.0"
    static final gradleVersion = "9.5.0"

    @Shared
    def testVariants = ['googleQa']

    @Shared
    def mapUploadVariants = ["amazonQa"]

    def setup() {
        with(new File(projectRootDir, ".gradle/configuration-cache")) {
            it.deleteDir()
        }
    }

    def setupSpec() {
        given: "create the build runner pinned to the AGP 9.3.x toolchain"
        // Run the inner build in a forked daemon rather than TestKit's default embedded
        // (withDebug(true)) mode. AGP 9.x requires Gradle 9.x, and embedded mode leaks the
        // outer Gradle 8.1.1 runtime onto the inner build's classpath, which fails during the
        // JdkImageTransform on JavaVersion.VERSION_26 (a constant 8.1.1 predates). Forking gives
        // the inner build a clean Gradle 9.x runtime.
        debuggable = false
        def runner = provideRunner()
                .withGradleVersion(gradleVersion)
                .forwardStdOutput(printFilter)
                .withArguments(
                        "-Pnewrelic.agent.version=${agentVersion}",
                        "-Pnewrelic.agp.version=${agpVersion}",
                        "-Pcompiler=r8",
                        "-PagentRepo=${localEnv["M2_REPO"]}",
                        "-PwithProductFlavors=true",
                        "-PincludeFeature=true",
                        "--debug",
                        "clean",
                        testTask)

        when: "run the build *once* and cache the results"
        buildResult = runner.build()
        filteredOutput = printFilter
    }

    def "build the test app on AGP 9.3.x"() {
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

    def "verify AGP 9.3.x is within the supported ceiling"() {
        expect: "no unsupported-AGP warning is emitted for the supported toolchain"
        !filteredOutput.contains("may not be compatible with Android Gradle plugin version")
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

            def buildIdResource = new File(buildDir,
                    "/generated/res/newrelicConfig${var.capitalize()}/values/com_newrelic_android_agent_config.xml")
            buildIdResource.exists() && buildIdResource.canRead()
            buildIdResource.text.contains('name="com_newrelic_android_buildId"')
            buildIdResource.text.contains('name="com_newrelic_android_metrics"')
        }
    }

    void "verify AGP90Adapter class transforms ran"() {
        expect:
        testVariants.each { var ->
            buildResult.task(":${ClassTransformWrapperTask.NAME}${var.capitalize()}")?.outcome == SUCCESS
        }
    }

    def "verify R8 map uploads wired"() {
        expect:
        filteredOutput.contains("Maps will be tagged and uploaded for variants [")

        mapUploadVariants.each { var ->
            buildResult.task(":newrelicMapUpload${var.capitalize()}").outcome == SUCCESS
            with(new File(buildDir, "outputs/newrelic/${var}/mapping.txt")) {
                exists()
                text.contains(Proguard.NR_MAP_PREFIX)
            }
        }
    }

    def "verify submodules built and instrumented"() {
        expect:
        filteredOutput.contains("[ActivityClassVisitor] Added Trace object to com/newrelic/agent/android/testapp/library/MainActivity")
        filteredOutput.contains("[AnnotatingClassVisitor] Tagging [com.newrelic.agent.android.testapp.library.MainActivity] as instrumented")
        testVariants.each { var ->
            buildResult.task(":library:${ClassTransformWrapperTask.NAME}${var.capitalize()}")?.outcome == SUCCESS
        }
    }
}
