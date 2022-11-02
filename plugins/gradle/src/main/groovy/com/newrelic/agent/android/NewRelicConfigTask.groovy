/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android

import com.newrelic.agent.compile.RewriterAgent
import com.newrelic.agent.util.BuildId
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

abstract class NewRelicConfigTask extends DefaultTask {
    final private String NEWLN = System.getProperty("line.separator");
    final private String CONFIG_CLASS = "com/newrelic/agent/android/NewRelicConfig.java"

    @Input
    def inputVariantName

    @Input
    @Optional
    String mapProvider  // [proguard, r8, dexguard]

    @Input
    @Optional
    def buildTypeMinified

    @Input
    @Optional
    String configClassBody =
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


    // The directory to write source files to
    @OutputDirectory
    abstract DirectoryProperty getSourceOutputDir()

    @TaskAction
    def newRelicConfigTask() {
        try {
            def obfuscated = buildTypeMinified
            def buildGenPath = getSourceOutputDir().getAsFile().get().absolutePath
            def f = getProject().file(buildGenPath + "/" + CONFIG_CLASS)

            f.parentFile.mkdirs()
            f.text = configClassBody
                    .replaceAll("@version@", RewriterAgent.getVersion())
                    .replaceAll("@buildId@", BuildId.getBuildId(inputVariantName))
                    .replaceAll("@obfuscated@", obfuscated ? "true" : "false")
                    .replaceAll("@provider@", mapProvider)

        } catch (Exception e) {
            logger.error("[newrelic.error] Error encountered while configuring the New Relic agent", e)
        }
    }
}
