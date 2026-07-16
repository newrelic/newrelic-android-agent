/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android

import com.newrelic.agent.InstrumentationAgent
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.Logger
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*

@CacheableTask
abstract class NewRelicConfigTask extends DefaultTask {
    final static String NAME = "newrelicConfig"
    final static String CONFIG_CLASS = "com/newrelic/agent/android/NewRelicConfig.java"
    final static String METADATA = ".metadata"

    // BUILD_ID is intentionally NOT embedded here anymore - see CONFIG_RESOURCE_FILE below.
    // Keeping a stable placeholder (instead of the real, per-build value) means this generated
    // source is byte-identical across builds when nothing else changed, so compileReleaseJavaWithJavac
    // / compileReleaseKotlin / minifyReleaseWithR8 stay cache-hits.
    final static String BUILD_ID_PLACEHOLDER = ""

    // The real, per-build value lives here instead: a generated Android string resource, which
    // merges through aapt2 and never reaches javac/kotlinc/R8. Named generically (not "buildid")
    // so this same file can carry other dynamic, per-build values later without another rename.
    final static String CONFIG_RESOURCE_FILE = "values/com_newrelic_android_agent_config.xml"

    // Underscores, not periods: AAPT2's resource-shrinker keep/discard matching (tools:keep)
    // works against the Java-safe symbol table, which canonicalizes periods to underscores.
    // Using the same underscored name for the declaration avoids relying on two different
    // spellings of the same resource across the codebase.
    final static String BUILD_ID_RESOURCE_NAME = "com_newrelic_android_buildId"

    // METRICS (toolchain/environment info) can differ across CI machines even when no source
    // changed, so it's kept out of the compiled source for the same reason as BUILD_ID above -
    // otherwise it would bust remote build caching across a fleet of build agents.
    final static String METRICS_PLACEHOLDER = ""
    final static String METRICS_RESOURCE_NAME = "com_newrelic_android_metrics"

    @Input
    abstract Property<String> getBuildId()      // variant buildId

    @Input
    abstract Property<String> getMapProvider()  // [proguard, r8, dexguard]

    @Input
    abstract Property<Boolean> getMinifyEnabled()

    @Input
    @Optional
    abstract Property<String> getBuildMetrics()

    @OutputDirectory
    abstract DirectoryProperty getSourceOutputDir()

    @OutputDirectory
    abstract DirectoryProperty getResourceOutputDir()

    @OutputFile
    @Optional
    abstract RegularFileProperty getConfigMetadata()

    @TaskAction
    def newRelicConfigTask() {
        try {
            def f = sourceOutputDir.file(CONFIG_CLASS).get().asFile

            f.with {
                parentFile.mkdirs()
                text = """
                        package com.newrelic.agent.android;
    
                        final class NewRelicConfig {
                            static final String VERSION = "${InstrumentationAgent.getVersion()}";
                            static final String BUILD_ID = "${BUILD_ID_PLACEHOLDER}";
                            static final Boolean OBFUSCATED = ${minifyEnabled.getOrElse(false)};
                            static final String MAP_PROVIDER = "${mapProvider.get()}";
                            static final String METRICS = "${METRICS_PLACEHOLDER}";
                            public static String getBuildId() {
                                return BUILD_ID;
                            }
                        }
                        """.stripIndent()
            }

            configMetadata.get().asFile.text = buildId.get()

            def resourceFile = resourceOutputDir.file(CONFIG_RESOURCE_FILE).get().asFile
            resourceFile.with {
                parentFile.mkdirs()
                text = """<?xml version="1.0" encoding="utf-8"?>
                        <resources>
                            <string name="${BUILD_ID_RESOURCE_NAME}" translatable="false">${buildId.get()}</string>
                            <string name="${METRICS_RESOURCE_NAME}" translatable="false">${escapeXml(buildMetrics.getOrElse(""))}</string>
                        </resources>
                        """.stripIndent()
            }

        } catch (Exception e) {
            logger.error("Error encountered while configuring the New Relic plugin: ", e)
        }
    }

    private static String escapeXml(String value) {
        value.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;')
    }

    @Internal
    @Override
    Logger getLogger() {
        return NewRelicGradlePlugin.LOGGER
    }

}
