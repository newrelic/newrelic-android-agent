/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android

import com.newrelic.agent.InstrumentationAgent
import com.newrelic.agent.util.BuildId
import kotlin.KotlinVersion
import org.gradle.api.BuildCancelledException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logger
import org.gradle.api.plugins.UnknownPluginException
import org.slf4j.LoggerFactory
import org.gradle.api.provider.Property


class NewRelicGradlePlugin implements Plugin<Project> {
    public static Logger LOGGER = LoggerFactory.getLogger(PLUGIN_EXTENSION_NAME)

    public static final String PLUGIN_EXTENSION_NAME = "newrelic"

    private NewRelicExtension pluginExtension
    private BuildHelper buildHelper

    NewRelicGradlePlugin() {
        // bind the instrumentation agent's logger to the plugin's logger
        InstrumentationAgent.logger = LOGGER
    }

    @Override
    void apply(Project project) {
        project.getLogging().captureStandardOutput(LogLevel.WARN)

        if (!isSupportedModule(project)) {
            throw new BuildCancelledException("Instrumentation of this module type is not supported in this version.")
        }

        pluginExtension = NewRelicExtension.register(project)
        if (!pluginExtension.getEnabled()) {
            return
        }

        buildHelper = BuildHelper.register(project)

        project.configure(project) {
            // Gradle now has a complete task execution graph for the requested tasks
            if (pluginExtension.getEnabled()) {

                project.afterEvaluate {
                    // set global enable flag
                    parseLegacyAgentArgs(project, pluginExtension.logInstrumentationEnabled)

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

        if (buildHelper.dexguardHelper?.getEnabled()) {
            buildHelper.dexguardHelper.configureDexGuard()
        }

        pluginExtension.with {
            if (!variantExclusions.isEmpty()) {
                LOGGER.info("Instrumentation will be disabled for variants ${variantExclusions}")
            }

            if (!packageExclusions.isEmpty()) {
                LOGGER.info("Instrumentation will be disabled for classes ${packageExclusions}")
            }

            // Do all the variants if variant maps are disabled, or only those provided
            // in the extension. The default is ['release'].
            if (!variantMapsEnabled.get() || variantMapUploads.isEmpty()) {
                LOGGER.debug("Maps will be tagged and uploaded for all variants")
            } else {
                LOGGER.debug("Maps will be tagged and uploaded for variants ${variantMapUploads}")
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
        LOGGER.debug("Gradle configuration cache enabled: " + buildHelper.configurationCacheEnabled())

        if (buildHelper.checkDynamicFeature()) {
            LOGGER.debug("Dynamic feature module detected.")
        }

        if (buildHelper.checkDexGuard()) {
            LOGGER.info("DexGuard detected " + buildHelper.dexguardHelper?.currentVersion)
        }

        if (buildHelper.checkApplication()) {
            LOGGER.info("BuildMetrics[${buildHelper.getBuildMetrics()}]")
        }
    }

    private def parseLegacyAgentArgs(Project project,Property<Boolean> logInstrumentationEnabled) {
        def agentArgs = ""
        def logInstrumentationEnabledStr = logInstrumentationEnabled.get().toString()

        LOGGER.debug("logInstrumentationEnabled: " + logInstrumentationEnabledStr)

        if (project.logger.isDebugEnabled()) {
            agentArgs = "loglevel=DEBUG;logInstrumentationEnabled=${logInstrumentationEnabledStr}"
        } else if (project.logger.isInfoEnabled()) {
            agentArgs = "loglevel=INFO;logInstrumentationEnabled=${logInstrumentationEnabledStr}"
        } else if (project.logger.isWarnEnabled()) {
            agentArgs = "loglevel=WARN;logInstrumentationEnabled=${logInstrumentationEnabledStr}"
        } else if (project.logger.isErrorEnabled()) {
            agentArgs = "loglevel=ERROR;logInstrumentationEnabled=${logInstrumentationEnabledStr}"
        } else {
            agentArgs = "loglevel=TRACE;logInstrumentationEnabled=${logInstrumentationEnabledStr}"
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

    static def isSupportedModule(Project project) {
        return project.pluginManager.hasPlugin("com.android.application") ||
                project.pluginManager.hasPlugin("com.android.library") ||
                project.pluginManager.hasPlugin("com.android.feature") ||
                project.pluginManager.hasPlugin("com.android.dynamic-feature")
    }
}