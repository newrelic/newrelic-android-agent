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
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

abstract class NewRelicConfigTask extends DefaultTask {
    final private String NEWLN = BuildHelper.NEWLN
    final private String CONFIG_CLASS = "com/newrelic/agent/android/NewRelicConfig.java"

    @Input
    abstract Property<String> getBuildId()        // variant buildId

    @Input
    abstract Property<String> getMapProvider()    // [proguard, r8, dexguard]

    @Input
    abstract Property<Boolean> getMinifyEnabled()

    @OutputDirectory
    abstract DirectoryProperty getSourceOutputDir()

    private final String configClassBody =
            "package com.newrelic.agent.android;" + NEWLN +
                    "final class NewRelicConfig {" + NEWLN +
                    "\tstatic final String VERSION = \"@version@\";" + NEWLN +
                    "\tstatic final String BUILD_ID = \"@buildId@\";" + NEWLN +
                    "\tstatic final Boolean OBFUSCATED = @obfuscated@;" + NEWLN +
                    "\tstatic final String MAP_PROVIDER = \"@provider@\";" + NEWLN +
                    "\tpublic static String getBuildId() {" + NEWLN +
                    "\t\treturn BUILD_ID;" + NEWLN +
                    "\t}" + NEWLN +
                    "}" + NEWLN

    @TaskAction
    def newRelicConfigTask() {
        try {
            def obfuscated = minifyEnabled.present && minifyEnabled.get()
            def f = getSourceOutputDir().file(CONFIG_CLASS).get().asFile

            f.parentFile.mkdirs()
            f.text = configClassBody
                    .replaceAll("@version@", InstrumentationAgent.getVersion())
                    .replaceAll("@buildId@", buildId.get())
                    .replaceAll("@obfuscated@", obfuscated ? "true" : "false")
                    .replaceAll("@provider@", mapProvider.get())

        } catch (Exception e) {
            logger.error("Error encountered while configuring the New Relic agent: ", e)
        }
    }

    @Internal
    @Override
    Logger getLogger() {
        return NewRelicGradlePlugin.LOGGER
    }
}
