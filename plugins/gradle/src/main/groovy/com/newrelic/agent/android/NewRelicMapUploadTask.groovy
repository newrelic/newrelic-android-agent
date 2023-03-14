/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android

import com.google.common.io.BaseEncoding
import com.newrelic.agent.InstrumentationAgent
import com.newrelic.agent.android.obfuscation.Proguard
import com.newrelic.agent.util.BuildId
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.Logger
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

// FIXME https://github.com/android/gradle-recipes/blob/agp-7.0/Kotlin/getMappingFile/app/build.gradle.kts

abstract class NewRelicMapUploadTask extends DefaultTask {

    @Internal
    abstract Property<String> getVariantName()

    @Internal
    abstract DirectoryProperty getProjectRoot()

    @Input
    abstract Property<String> getBuildId()        // variant buildId

    @Input
    abstract Property<String> getMapProvider()    // [proguard, r8, dexguard]

    @InputFile
    abstract RegularFileProperty getMapFile()

    @TaskAction
    def newRelicMapUploadTask() {

        try {
            def propertiesFound = false
            def agentOptions = InstrumentationAgent.getAgentOptions()
            def filePattern = ~/${Proguard.NR_PROPERTIES}/

            // start search for properties at project's root dir
            projectRoot.get().asFile.eachFileRecurse {
                if (filePattern.matcher(it.name).find()) {
                    logger.debug("Found properties [${it.absolutePath}]")
                    agentOptions.put(Proguard.PROJECT_ROOT_KEY, new String(BaseEncoding.base64().encode(it.getParent().bytes)))
                    propertiesFound = true
                }
            }

            if (!propertiesFound) {
                logger.error("newrelic.properties was not found! Mapping file for variant [${variantName.get()}] not uploaded.")
                return
            }

            // we know where map should be (Gradle tells us)
            def mapFilePath = mapFile.getAsFile().get()

            if (mapFilePath) {
                if (mapFilePath.exists()) {
                    logger.debug("Map file for variant [${variantName.get()}] detected: [${mapFilePath.absolutePath}]")
                    if (buildId.present) {
                        agentOptions.put(Proguard.MAPPING_FILE_KEY, mapFilePath.absolutePath)
                        agentOptions.put(Proguard.MAPPING_PROVIDER_KEY, mapProvider.get())
                        agentOptions.put(Proguard.VARIANT_KEY, variantName.get())
                        agentOptions.put(BuildId.BUILD_ID_KEY, buildId.get())

                        new Proguard(NewRelicGradlePlugin.LOGGER, agentOptions).findAndSendMapFile()

                    } else {
                        logger.error("No build ID for variant [${variantName.get()}]")
                    }
                } else {
                    logger.debug("No map file for variant [${variantName.get()}] detected: [${mapFilePath.absolutePath}]")
                }

            } else {
                logger.warning("variant[${variantName.get()}] mappingFile is null")
            }

        } catch (Exception e) {
            logger.error("NewRelicMapUploadTask: " + e)
        }
    }

    @Internal
    @Override
    Logger getLogger() {
        return NewRelicGradlePlugin.LOGGER
    }

}
