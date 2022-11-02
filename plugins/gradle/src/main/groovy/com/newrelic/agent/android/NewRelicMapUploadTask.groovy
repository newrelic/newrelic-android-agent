/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android

import com.google.common.base.Strings
import com.google.common.io.BaseEncoding
import com.newrelic.agent.compile.RewriterAgent
import com.newrelic.agent.compile.SystemErrLog
import com.newrelic.agent.obfuscation.Proguard
import com.newrelic.agent.util.BuildId
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction

abstract class NewRelicMapUploadTask extends DefaultTask {

    @Input
    String inputVariantName

    @Input
    String mapProvider  // [proguard, r8, dexguard]

    @Optional
    @InputFile
    abstract RegularFileProperty getMapInput()

    @TaskAction
    def newRelicMapUploadTask() {
        try {
            def propertiesFound = false
            def agentOptions = RewriterAgent.parseAgentArgs(System.getProperty(NewRelicGradlePlugin.NR_AGENT_ARGS_KEY))
            def filePattern = ~/${Proguard.NR_PROPERTIES}/

            // start search for properties at project's root dir
           project.rootDir.eachFileRecurse {
                if (filePattern.matcher(it.name).find()) {
                    logger.debug("[newrelic.debug] Found properties [${it.absolutePath}]")
                    agentOptions.put(Proguard.PROJECT_ROOT_KEY, new String(BaseEncoding.base64().encode(it.getParent().bytes)))
                    propertiesFound = true
                }
            }

            if (!propertiesFound) {
                logger.error("[newrelic] newrelic.properties was not found! Mapping file for variant [${inputVariantName}] not uploaded.")
                return
            }

            // we know where map should be (Gradle tells us)
            def mapPathFile =  mapInput.getAsFile().get()

            if (mapPathFile) {
                if (mapPathFile.exists()) {
                    logger.debug("[newrelic.debug] Map file for variant [${inputVariantName}] detected: [${mapPathFile.absolutePath}]")
                    def buildId = BuildId.getBuildId(inputVariantName)
                    if (!Strings.isNullOrEmpty(buildId)) {
                        agentOptions.put(Proguard.MAPPING_FILE_KEY, new String(mapPathFile.absolutePath))
                        agentOptions.put(Proguard.MAPPING_PROVIDER_KEY, mapProvider)
                        agentOptions.put(Proguard.VARIANT_KEY, inputVariantName)
                        agentOptions.put(BuildId.BUILD_ID_KEY, buildId)

                        new Proguard(new SystemErrLog(agentOptions), agentOptions).findAndSendMapFile()

                    } else {
                        logger.error("[newrelic.error] No build ID for variant [${inputVariantName}]")
                    }
                } else {
                    logger.debug("[newrelic.debug] No map file for variant [${inputVariantName}] detected: [${mapPathFile.absolutePath}]")
                }

            } else {
                logger.warning("[newrelic.warn] variant[${inputVariantName}] mappingFile is null")
            }

        } catch (Exception e) {
            logger.error("[newrelic.error] NewRelicMapUploadTask: " + e)
        }
    }

}
