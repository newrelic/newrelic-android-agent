/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android

import com.newrelic.agent.InstrumentationAgent
import groovy.json.JsonOutput
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.Logger
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*

@CacheableTask
abstract class NewRelicConfigTask extends DefaultTask {
    final static String NAME = "newrelicConfig"
    final static String CONFIG_FILE = "newrelic_config.json"
    final static String METADATA = ".metadata"

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
    abstract DirectoryProperty getAssetsOutputDir()

    @OutputFile
    @Optional
    abstract RegularFileProperty getConfigMetadata()

    @TaskAction
    def newRelicConfigTask() {
        try {
            def f = assetsOutputDir.file(CONFIG_FILE).get().asFile

            def config = [
                    version    : InstrumentationAgent.getVersion(),
                    buildId    : buildId.get(),
                    obfuscated : minifyEnabled.getOrElse(false),
                    mapProvider: mapProvider.get(),
                    metrics    : buildMetrics.getOrElse("")
            ]

            f.with {
                parentFile.mkdirs()
                text = JsonOutput.prettyPrint(JsonOutput.toJson(config))
            }

            def metaFile = configMetadata.get().asFile
            metaFile.parentFile.mkdirs()
            metaFile.text = buildId.get()

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
