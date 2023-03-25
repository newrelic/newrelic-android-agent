/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android

import com.newrelic.agent.InstrumentationAgent
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.logging.Logger
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*

abstract class NewRelicConfigTask extends DefaultTask {
    final static String NAME = "newrelicConfig"
    final private String CONFIG_CLASS = "com/newrelic/agent/android/NewRelicConfig.java"

    @Input
    abstract Property<String> getBuildId()        // variant buildId

    @Input
    abstract Property<String> getMapProvider()    // [proguard, r8, dexguard]

    @Input
    abstract Property<Boolean> getMinifyEnabled()

    @Input
    @Optional
    abstract Property<String> getBuildMetrics()

    @OutputDirectory
    abstract DirectoryProperty getSourceOutputDir()

    @TaskAction
    def newRelicConfigTask() {
        try {
            def f = getSourceOutputDir().file(CONFIG_CLASS).get().asFile

            f.with {
                parentFile.mkdirs()
                text = """
                    package com.newrelic.agent.android;

                    final class NewRelicConfig {
                        static final String VERSION = "${InstrumentationAgent.getVersion()}";
                        static final String BUILD_ID = "${buildId.get()}";
                        static final Boolean MINIFIED = ${minifyEnabled.getOrElse(false)};
                        static final String MAP_PROVIDER = "${mapProvider.get()}";
                        static final String METRICS = "${buildMetrics.getOrElse("")}";
                        public static String getBuildId() {
                            return BUILD_ID;
                        }
                    }
                    """.stripIndent()
            }

        } catch (Exception e) {
            logger.error("Error encountered while configuring the New Relic plugin: ", e)
        }
    }

    @Internal
    @Override
    Logger getLogger() {
        return NewRelicGradlePlugin.LOGGER
    }
}
