/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android

import com.google.common.io.BaseEncoding
import com.newrelic.agent.InstrumentationAgent
import com.newrelic.agent.android.obfuscation.ReactNativeSourceMap
import com.newrelic.agent.util.BuildId
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.Logger
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction

/**
 * Gradle task to upload React Native source maps to New Relic Symbol Ingest API.
 */
abstract class NewRelicReactNativeSourceMapUploadTask extends DefaultTask {
    final static String NAME = "newrelicReactNativeSourceMapUpload"

    @InputFile
    @Optional
    abstract RegularFileProperty getSourceMapFile()

    @Input
    abstract Property<String> getVariantName()

    @Input
    abstract Property<String> getBuildId()

    @Input
    abstract Property<String> getAppVersionId()

    @Internal
    abstract DirectoryProperty getProjectRoot()

    @TaskAction
    def uploadReactNativeSourceMap() {
        try {
            def propertiesFound = false
            def agentOptions = InstrumentationAgent.getAgentOptions()
            def filePattern = ~/${ReactNativeSourceMap.NR_PROPERTIES}/

            // Start search for properties at project's root dir
            projectRoot.get().asFile.eachFileRecurse {
                if (filePattern.matcher(it.name).find()) {
                    logger.debug("Found properties [${it.absolutePath}]")
                    agentOptions.put(ReactNativeSourceMap.PROJECT_ROOT_KEY, new String(BaseEncoding.base64().encode(it.getParent().bytes)))
                    propertiesFound = true
                }
            }

            if (!propertiesFound) {
                logger.error("newrelic.properties was not found! React Native source map for variant [${variantName.get()}] not uploaded.")
                return
            }

            if (sourceMapFile.isPresent()) {
                def sourceMap = sourceMapFile.asFile.get()

                if (sourceMap?.exists()) {
                    logger.debug("React Native source map for variant [${variantName.get()}] detected: [${sourceMap.absolutePath}]")

                    new ReactNativeSourceMap(NewRelicGradlePlugin.LOGGER, agentOptions)
                            .uploadSourceMap(sourceMap, buildId.get(), appVersionId.get())
                } else {
                    logger.debug("No React Native source map for variant [${variantName.get()}] detected at: [${sourceMap?.absolutePath}]")
                }
            } else {
                logger.warn("React Native source map file not specified for variant [${variantName.get()}]")
            }

        } catch (Exception e) {
            logger.error("NewRelicReactNativeSourceMapUploadTask: " + e)
        }
    }

    @Internal
    @Override
    Logger getLogger() {
        return NewRelicGradlePlugin.LOGGER
    }

    /**
     * Returns the set of React Native bundle task names that this task should run after.
     */
    static Set<String> wiredTaskNames(String vnc) {
        return Set.of(
                "bundle${vnc}JsAndAssets",
                "createBundle${vnc}JsAndAssets",
        )
    }
}