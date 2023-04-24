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
import org.gradle.api.tasks.*

abstract class NewRelicMapUploadTask extends DefaultTask {
    final static String NAME = "newrelicMapUpload"

    @Input
    abstract Property<String> getVariantName()

    @InputDirectory
    abstract DirectoryProperty getProjectRoot()

    @Input
    abstract Property<String> getBuildId()          // variant buildId

    @Input
    abstract Property<String> getMapProvider()      // [proguard, r8, dexguard]

    @InputFile
    abstract RegularFileProperty getMappingFile()

    @OutputFile
    abstract RegularFileProperty getTaggedMappingFile()

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

            if (taggedMappingFile.isPresent()) {
                if (mappingFile.isPresent()) {
                    def infile = mappingFile.asFile.get()
                    def outfile = taggedMappingFile.asFile.get()
                    outfile.with {
                        parentFile.mkdirs()
                        text = infile.text + BuildHelper.NEWLN +
                                Proguard.NR_MAP_PREFIX + buildId.get() + BuildHelper.NEWLN
                    }
                }

                // we know where map should be (Gradle tells us)
                def mapFilePath = taggedMappingFile.asFile.get()

                if (mapFilePath?.exists()) {
                    logger.debug("Map file for variant [${variantName.get()}] detected: [${mapFilePath.absolutePath}]")
                    agentOptions.put(Proguard.MAPPING_FILE_KEY, mapFilePath.absolutePath)
                    agentOptions.put(Proguard.MAPPING_PROVIDER_KEY, mapProvider.get())
                    agentOptions.put(Proguard.VARIANT_KEY, variantName.get())
                    agentOptions.put(BuildId.BUILD_ID_KEY, buildId.get())

                    new Proguard(NewRelicGradlePlugin.LOGGER, agentOptions).findAndSendMapFile()
                } else {
                    logger.debug("No map file for variant [${variantName.get()}] detected: [${mapFilePath.absolutePath}]")
                }

            } else {
                logger.warn("variant[${variantName.get()}] taggedMappingFile is null")
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

    static Set<String> getDependentTaskNames(String variantNameCap) {
        def taskSet = [] as Set

        taskSet.addAll([
                "lintVitalAnalyze${variantNameCap}",
                "lintVitalReport${variantNameCap}",
                /*
                "lintVital${variantNameCap}"
                "minify${variantNameCap}WithProguard",
                "minify${variantNameCap}WithR8",
                "transformClassesAndResourcesWithProguardFor${variantNameCap}",
                "transformClassesAndResourcesWithR8For${variantNameCap}",
                */
        ])

        taskSet
    }
}
