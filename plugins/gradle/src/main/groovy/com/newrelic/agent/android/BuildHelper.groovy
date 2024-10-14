/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android

import com.android.build.api.variant.AndroidComponentsExtension
import com.newrelic.agent.InstrumentationAgent
import com.newrelic.agent.android.agp4.AGP4Adapter
import com.newrelic.agent.android.obfuscation.Proguard
import groovy.json.JsonOutput
import kotlin.KotlinVersion
import org.gradle.api.Action
import org.gradle.api.BuildCancelledException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.Logger
import org.gradle.api.plugins.ExtraPropertiesExtension
import org.gradle.api.plugins.UnknownPluginException
import org.gradle.api.provider.Provider
import org.gradle.util.GradleVersion

import java.util.concurrent.atomic.AtomicReference

class BuildHelper {

    /**
     * https://developer.android.com/studio/releases/gradle-plugin#updating-gradle
     * Plugin version	Required Gradle version
     * 4.2.+            6.7.1       // min supported Gradle configuration cache level
     * 7.0              7.2         // min supported AGP that supports configuration caching
     * 7.1	            7.2
     * 7.2              7.3.3
     * 7.3              7.4
     * 7.4              7.5
     * 8.0              8.0
     * 8.1              8.2
     * 8.2              8.2
     * 8.3              8.4
     * 8.4              8.6
     *
     **/

    static final String PROP_WARNING_AGP = "newrelic.warning.agp"
    static final String PROP_HALT_ON_WARNING = "newrelic.halt-on-warning"

    public static final String agentVersion = InstrumentationAgent.version

    public final String gradleVersion = GradleVersion.current().version
    static final String minSupportedAGPVersion = '7.0.0'
    static final String maxSupportedAGPVersion = "8.7"
    static final String minSupportedGradleVersion = '7.1'
    static final String minSupportedGradleConfigCacheVersion = '6.6'
    static final String minSupportedAGPConfigCacheVersion = '7.0.0'

    public static String NEWLN = "\r\n"

    final Project project
    final NewRelicExtension extension
    final def android
    final AndroidComponentsExtension androidComponents
    final ExtraPropertiesExtension extraProperties

    VariantAdapter variantAdapter
    String agpVersion
    Logger logger
    DexGuardHelper dexguardHelper

    static final AtomicReference<BuildHelper> INSTANCE = new AtomicReference<BuildHelper>(null)

    static BuildHelper register(Project project) {
        return new BuildHelper(project)
    }

    BuildHelper(Project project) {
        this.project = project
        this.logger = NewRelicGradlePlugin.LOGGER

        try {
            this.extension = project.extensions.getByType(NewRelicExtension.class) as NewRelicExtension
            this.extraProperties = project.extensions.getByType(ExtraPropertiesExtension.class) as ExtraPropertiesExtension
            this.android = project.extensions.getByName("android")
            this.androidComponents = project.extensions.getByType(AndroidComponentsExtension.class) as AndroidComponentsExtension

            this.agpVersion = getAGPVersionAsSemVer(getReportedAGPVersion())

            // warn or throw if we can't instrument this build
            validatePluginSettings()

            NEWLN = getSystemPropertyProvider("line.separator").get()

            this.variantAdapter = VariantAdapter.register(this)
            this.dexguardHelper = DexGuardHelper.register(this)

        } catch (UnknownPluginException e) {
            throw new BuildCancelledException(e.message)
        }
    }

    void validatePluginSettings() throws UnknownPluginException {
        if (!getAndroidExtension()) {
            throw new UnknownPluginException("The New Relic agent plugin depends on the Android Gradle plugin." + NEWLN +
                    "Please apply an Android Gradle plugin before the New Relic agent: " + NEWLN +
                    "plugins {" + NEWLN +
                    "   id 'com.android.[application, library, dynamic-feature]'" + NEWLN +
                    "   id 'newrelic'" + NEWLN +
                    "}")
        }

        def reportedAGPVersion = getReportedAGPVersion()

        if (GradleVersion.version(getAgpVersion()) < GradleVersion.version(BuildHelper.minSupportedAGPVersion)) {
            throw new UnknownPluginException("The New Relic plugin is not compatible with Android Gradle plugin version ${reportedAGPVersion}."
                    + NEWLN
                    + "AGP versions ${minSupportedAGPVersion} - ${maxSupportedAGPVersion} are officially supported.")
        }

        if (GradleVersion.version(getAgpVersion()) > GradleVersion.version(BuildHelper.maxSupportedAGPVersion)) {
            def enableWarning = hasOptional(BuildHelper.PROP_WARNING_AGP, true).toString().toLowerCase()
            if ((enableWarning != 'false') && (enableWarning != '0')) {
                warnOrHalt("The New Relic plugin may not be compatible with Android Gradle plugin version ${reportedAGPVersion}."
                        + NEWLN
                        + "AGP versions ${minSupportedAGPVersion} - ${maxSupportedAGPVersion} are officially supported."
                        + NEWLN
                        + "Set property '${PROP_WARNING_AGP}=false' to disable this warning.")
            }
        }

        if (GradleVersion.version(getGradleVersion()) < GradleVersion.version(BuildHelper.minSupportedGradleVersion)) {
            warnOrHalt("The New Relic plugin may not be compatible with Gradle version ${getGradleVersion()}."
                    + NEWLN
                    + "Gradle versions ${minSupportedGradleVersion} and higher are officially supported.")
        }
    }

    /**
     * Return the semantic version reported by the plugin
     * @return Semver as a String
     */
    String getAGPVersionAsSemVer(String version) {
        // filter for unofficial (non-semver) version numbers
        try {
            // AGP version may contain '*-{qualifier}', which GradleVersion doesn't recognize
            GradleVersion.version(version)

        } catch (Exception ignored) {
            warnOrHalt("AGP version [$version] is not officially supported")
            def (_, semVer, delimiter, qualifier) = (version =~ /^(\d{1,3}\.\d{1,3}\.\d{1,3})(-)?(.*)?$/)[0]
            return semVer
        }

        return version
    }

    /**
     * Try our best here to return the correct plugin version.
     *
     * @return Full version reported by plugin
     */
    String getReportedAGPVersion() {
        String reportedVersion = "unknown"

        try {
            getAndroidComponentsExtension().getPluginVersion().with {
                reportedVersion = major + "." + minor + "." + micro
                if (previewType) {
                    reportedVersion = reportedVersion + "-" + previewType + preview
                }
            }

        } catch (MissingPropertyException ignored) {
            /**
             * pluginVersion not available. Fetching the reported AGP is problematic: the
             * Version class has moved around between releases of both the AGP and gradle-api.
             */
            try {
                // AGP 3.6.+: com.android.Version.ANDROID_GRADLE_PLUGIN_VERSION
                final Class versionClass = BuildHelper.class.getClassLoader().loadClass("com.android.Version")
                reportedVersion = versionClass.getDeclaredField("ANDROID_GRADLE_PLUGIN_VERSION").get(null).toString()

            } catch (ClassNotFoundException e) {
                try {
                    logger.error("AGP 3.6.+: $e")

                    // AGP 3.4 - 3.5.+: com.android.builder.model.Version.ANDROID_GRADLE_PLUGIN_VERSION
                    final Class versionClass = BuildHelper.class.getClassLoader().loadClass("com.android.builder.model.Version")
                    reportedVersion = versionClass.getDeclaredField("ANDROID_GRADLE_PLUGIN_VERSION").get(null).toString()

                } catch (ClassNotFoundException e1) {
                    logger.error("AGP 3.4 - 3.5.+: $e1")
                    throw e1
                }
            }
        }

        return reportedVersion
    }

    def withDexGuardHelper(final DexGuardHelper dexguardHelper) {
        this.dexguardHelper = dexguardHelper
    }

    def configurationCacheSupported() {
        GradleVersion.version(getGradleVersion()) >= GradleVersion.version(BuildHelper.minSupportedGradleConfigCacheVersion)
    }

    def configurationCacheEnabled() {
        try {
            // FIXME May also be enabled through command line: --configuration-cache
            def prop = project.providers.gradleProperty("org.gradle.configuration-cache")
            return prop.present
        } catch (Exception ignored) {
            false
        }
    }

    Provider<String> getSystemPropertyProvider(final String key) {
        try {
            def provider = project.providers.systemProperty(key)
            if (configurationCacheSupported()) {
                return provider.forUseAtConfigurationTime()
            }
            return provider
        } catch (Exception ignored) {
        }

        return project.objects.property(String).orElse(System.getProperty(key))
    }

    /**
     * @return true if AGP 4.x Gradle components are present
     */
    def isUsingLegacyTransform() {
        variantAdapter instanceof AGP4Adapter
    }

    /**
     * Emit passed string as a warning. If PROP_HALT_ON_WARNING is set, throw a HaltBuildException instead
     * Unsupported at present.
     *
     * @param Message to log or throw
     */
    void warnOrHalt(final String msg) {
        def haltOnWarning = hasOptional(BuildHelper.PROP_HALT_ON_WARNING, false).toString().toLowerCase()
        if ((haltOnWarning != 'false') && (haltOnWarning != '0')) {
            throw new UnknownPluginException(msg)
        }
        logger.warn(msg)
        logger.warn("Set property '${PROP_HALT_ON_WARNING}=true' to treat warnings as fatal errors.")
    }

    def hasOptional(String key, Object defaultValue) {
        project.rootProject.hasProperty(key) ? project.rootProject[key] : defaultValue
    }

    def getBuildMetrics() {
        def metrics = [
                agent             : agentVersion,
                agp               : agpVersion,
                gradle            : gradleVersion,
                java              : getSystemPropertyProvider('java.version').get(),
                kotlin            : KotlinVersion.CURRENT,
                configCacheEnabled: configurationCacheEnabled(),
                variants          : variantAdapter.getBuildMetrics()
        ]

        if (dexguardHelper?.enabled) {
            metrics["dexguard"] = dexguardHelper?.currentVersion
        }

        metrics
    }

    String getBuildMetricsAsJson() {
        try {
            JsonOutput.toJson(getBuildMetrics())
        } catch (Throwable ignored) {
            ""
        }
    }

    // gettors to assist in mocking

    String getGradleVersion() {
        return gradleVersion
    }

    String getAgpVersion() {
        return agpVersion
    }

    def getAndroidExtension() {
        return android
    }

    AndroidComponentsExtension getAndroidComponentsExtension() {
        return androidComponents
    }

    boolean checkDexGuard() {
        return project.plugins.hasPlugin("dexguard")
    }

    boolean checkDynamicFeature() {
        return project.plugins.hasPlugin("com.android.instantapp") ||
                project.plugins.hasPlugin("com.android.feature") ||
                project.plugins.hasPlugin("com.android.dynamic-feature")
    }

    boolean checkApplication() {
        return project.plugins.hasPlugin("com.android.application")
    }

    boolean checkLibrary() {
        return project.plugins.hasPlugin("com.android.library")
    }


    /**
     * Returns literal name of obfuscation compiler
     * @param project
     * @return Compiler name (R8, Proguard_603 or DexGuard)
     */
    String getMapCompilerName() {

        if (dexguardHelper?.enabled) {
            return Proguard.Provider.DEXGUARD
        }

        if (GradleVersion.version(agpVersion) < GradleVersion.version("3.3")) {
            return Proguard.Provider.PROGUARD_603
        }

        // Gradle 3.3 was experimental R8, driven by properties
        if (checkLibrary() && project.hasProperty("android.enableR8.libraries")) {
            return (project.getProperty("android.enableR8.libraries").toLowerCase().equals("false") ? Proguard.Provider.PROGUARD_603 : Proguard.Provider.R8)
        }

        if (project.hasProperty("android.enableR8")) {
            return (project.getProperty("android.enableR8").toLowerCase().equals("false") ? Proguard.Provider.PROGUARD_603 : Proguard.Provider.R8)
        }

        // Gradle 3.4+ uses proguard by default, unless enabled by properties above
        if (GradleVersion.version(agpVersion) < GradleVersion.version("3.4")) {
            return Proguard.Provider.PROGUARD_603
        }

        // Gradle 3.4+ uses r8 by default, unless disabled by properties above
        return Proguard.Provider.DEFAULT
    }

    Set<?> getTaskProvidersFromNames(Set<String> taskNameSet) {
        def taskSet = [] as Set

        taskNameSet.each { taskName ->
            try {
                taskSet.add(project.tasks.named(taskName))
            } catch (Exception ignored) {
                ignored
            }
        }

        taskSet
    }

    def wireTaskProviderToDependencyNames(Set<String> taskNames,
                                          Action<Task> action = null) {
        return wireTaskProviderToDependencies(getTaskProvidersFromNames(taskNames), action)
    }

    def wireTaskProviderToDependencies(Set<?> dependencyTaskProviders,
                                       Action<Task> action) {
        dependencyTaskProviders.each { dependencyTaskProvider ->
            try {
                action?.execute(dependencyTaskProvider)
            } catch (Exception ignored) {
                ignored
            }
        }
    }

    Project getProject() {
        return project
    }
}