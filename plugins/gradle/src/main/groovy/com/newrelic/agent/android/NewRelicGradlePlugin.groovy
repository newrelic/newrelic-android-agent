/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android

import com.newrelic.agent.InstrumentationAgent
import com.newrelic.agent.util.BuildId
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

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
            def agentArgs = parseLegacyAgentArgs(buildHelper.project)

            // Gradle now has a complete task execution graph for the requested tasks
            if (pluginExtension.getEnabled()) {

                project.afterEvaluate {
                    // set global enable flag
                    BuildId.setVariantMapsEnabled(pluginExtension.variantMapsEnabled.get())

                    def buildMap = getDefaultBuildMap()

                    logBuildMetrics()

                    try {
                        buildHelper.variantAdapter.configure(pluginExtension)

                        configurePlugin(project)

                        LOGGER.debug("New Relic plugin loaded.")

                    } catch (MissingPropertyException e) {
                        LOGGER.warn("Not supported: " + e)
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
            buildHelper.dexguardHelper.configureDexGuardTasks(project)
        }

        buildHelper.configureVariantModel()

        // add extension to project's ext data
        project.ext.newrelic = pluginExtension
    }

    void logBuildMetrics() {
        LOGGER.info("New Relic Agent version: " + buildHelper.agentVersion)

        LOGGER.debug("Android Gradle plugin version: " + buildHelper.agpVersion)
        LOGGER.debug("Gradle version: " + buildHelper.gradleVersion)
        LOGGER.debug("Java version: " + buildHelper.getSystemPropertyProvider('java.version').get())
        LOGGER.debug("Gradle configuration cache supported: " + buildHelper.configurationCacheSupported())
        LOGGER.debug("Gradle configuration cache enabled: " + buildHelper.configurationCacheEnabled())

        if (buildHelper.checkInstantApps()) {
            LOGGER.debug("InstantApp detected.")
        }

        if (buildHelper.checkDexGuard()) {
            LOGGER.info("DexGuard detected.")
        }

        LOGGER.info("BuildMetrics[${buildHelper.buildMetrics().toMapString()}]")
    }

    private def parseLegacyAgentArgs(Project project) {
        def prop = buildHelper.getSystemPropertyProvider(InstrumentationAgent.NR_AGENT_ARGS_KEY)
        def agentArgs = ""

        if (prop.present) {
            agentArgs = prop.get()

        } else {
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

        getProjectVariants().each { variant ->
            buildIdCache.put(variant.name, BuildId.getBuildId(variant.name))
        }

        return buildIdCache
    }

    // FIXME
    private def getProjectVariants() {
        return buildHelper.variantAdapter.getVariantValues()
    }

}