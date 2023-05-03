/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android

import com.newrelic.agent.InstrumentationAgent
import com.newrelic.agent.util.BuildId
import kotlin.KotlinVersion
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.plugins.UnknownPluginException

class NewRelicGradlePlugin implements Plugin<Project> {
    public static Logger LOGGER = Logging.getLogger(PLUGIN_EXTENSION_NAME)

    public static final String PLUGIN_EXTENSION_NAME = "newrelic"

    private NewRelicExtension pluginExtension
    private BuildHelper buildHelper

    @Override
    void apply(Project project) {
        project.getLogging().captureStandardOutput(LogLevel.WARN)

        // bind the instrumentation agent's logger to the plugin's logger
        InstrumentationAgent.LOGGER = LOGGER

        pluginExtension = NewRelicExtension.register(project)
        if (!pluginExtension.getEnabled()) {
            return
        }

        buildHelper = BuildHelper.register(project)

        project.configure(project) {
            def agentArgs = parseLegacyAgentArgs(project)

            // Gradle now has a complete task execution graph for the requested tasks
            if (pluginExtension.getEnabled()) {

                project.afterEvaluate {
                    def buildMap = getDefaultBuildMap()

                    // set global enable flag
                    BuildId.setVariantMapsEnabled(pluginExtension.variantMapsEnabled.get())

                    logBuildMetrics()

                    try {
                        buildHelper.variantAdapter.configure(pluginExtension)
                        configurePlugin(project)
                        LOGGER.debug("New Relic plugin loaded.")

                    } catch (Exception e) {
                        throw new UnknownPluginException("Not supported: " + e)
                    }
                }
            } else {
                LOGGER.info("The New Relic Gradle plugin is disabled.")
            }
        }
    }

    void configurePlugin(Project project) {

        if (buildHelper.checkDexGuard()) {
            buildHelper.withDexGuardHelper(DexGuardHelper.register(buildHelper))
            buildHelper.dexguardHelper.configureDexGuardTasks()
        }

        pluginExtension.with {
            // Do all the variants if variant maps are disabled, or only those provided
            // in the extension. The default is ['release'].
            if (!variantMapsEnabled.get() || variantMapUploadList.isEmpty()) {
                LOGGER.debug("Maps will be tagged and uploaded for all variants")
            } else {
                LOGGER.debug("Maps will be tagged and uploaded for variants ${variantMapUploadList}")
            }
        }

        // add extension to project's ext data
        project.ext.newrelic = pluginExtension
    }

    void logBuildMetrics() {
        LOGGER.info("New Relic Agent version: " + buildHelper.agentVersion)

        LOGGER.debug("Android Gradle plugin version: " + buildHelper.agpVersion)
        LOGGER.debug("Gradle version: " + buildHelper.gradleVersion)
        LOGGER.debug("Java version: " + buildHelper.getSystemPropertyProvider('java.version').get())
        LOGGER.debug("Kotlin version: " + KotlinVersion.CURRENT)
        LOGGER.debug("Gradle configuration cache supported: " + buildHelper.configurationCacheSupported())
        LOGGER.debug("Gradle configuration cache enabled: " + buildHelper.configurationCacheEnabled())

        if (buildHelper.checkInstantApps()) {
            LOGGER.debug("InstantApp detected.")
        }

        if (buildHelper.checkDexGuard()) {
            LOGGER.info("DexGuard detected.")
            throw new GradleException("DexGuard is not yet supported in this version of the New Relic Android Gradle plugin. Sit tight, it won't be long!")
        }

        LOGGER.info("BuildMetrics[${buildHelper.getBuildMetrics().toMapString()}]")
    }

    private def parseLegacyAgentArgs(Project project) {
        def agentArgs = ""

        if (project.logger.isDebugEnabled()) {
            agentArgs = "loglevel=DEBUG"
        } else if (project.logger.isInfoEnabled()) {
            agentArgs = "loglevel=INFO"
        } else if (project.logger.isWarnEnabled()) {
            agentArgs = "loglevel=WARN"
        } else if (project.logger.isErrorEnabled()) {
            agentArgs = "loglevel=ERROR"
        } else {
            agentArgs = "loglevel=TRACE"
        }

        Throwable argsError = InstrumentationAgent.withAgentArgs(agentArgs)
        if (argsError != null) {
            LOGGER.error(argsError.message)
        }

        agentArgs
    }

    /**
     * Seed the default variant build ID map.
     *
     * Currently, all variants use the same build ID
     *
     * @return Map of variant to build ID
     */
    protected Map<String, String> getDefaultBuildMap() {
        def buildIdCache = [:] as HashMap<String, String>

        buildHelper.variantAdapter.getVariantValues().each { variant ->
            buildIdCache.put(variant.name, BuildId.getBuildId(variant.name))
        }

        return buildIdCache
    }

}