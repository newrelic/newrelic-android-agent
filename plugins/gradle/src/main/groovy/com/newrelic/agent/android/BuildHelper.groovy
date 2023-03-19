/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android

import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.gradle.AppExtension
import com.newrelic.agent.InstrumentationAgent
import com.newrelic.agent.compile.HaltBuildException
import groovy.json.JsonOutput
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.plugins.ExtraPropertiesExtension
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.util.GradleVersion

import java.util.concurrent.atomic.AtomicReference

class BuildHelper {

    /**
     * https://developer.android.com/studio/releases/gradle-plugin#updating-gradle
     * Plugin version	Required Gradle version
     * 4.0.+            6.1.1
     * 4.1.+            6.5.+
     * 4.2.+            6.7.+       // min supported Gradle configuration cache level
     * 7.0              7.0.2       // min supported AGP that supports configuration caching
     * 7.1	            7.2.+
     * 7.2              7.3.3
     * 7.3              7.4
     * 7.4.+            7.5
     * 8.0.+            8.0
     * 8.1.+            8.1
     *
     * */

    static final String PROP_WARNING_AGP = "newrelic.warning.agp"
    static final String PROP_HALT_ON_ERROR = "newrelic.halt-on-error"

    public static final String agentVersion = InstrumentationAgent.version

    public final String gradleVersion = GradleVersion.current().version

    static final String currentSupportedAGPVersion = "8.0"
    static final String minSupportedAGPVersion = '4.2.0'
    static final String minSupportedGradleVersion = '7.0.2'
    static final String minSupportedGradleConfigCacheVersion = '6.6'
    static final String minSupportedAGPConfigCacheVersion = '7.0.2'

    public static String NEWLN = "\r\n"

    final Project project
    final ExtensionContainer extensions
    final ObjectFactory objects

    final String agpVersion

    final AppExtension android
    final AndroidComponentsExtension androidComponents
    final ExtraPropertiesExtension extraProperties
    final VariantAdapter variantAdapter

    Logger logger
    DexGuardHelper dexguardHelper

    static final AtomicReference<BuildHelper> instance = new AtomicReference<BuildHelper>(null)

    static BuildHelper register(Project project) {
        BuildHelper.instance.compareAndSet(null, new BuildHelper(project))
        return BuildHelper.instance.get()
    }

    BuildHelper(Project project) {
        this.project = project
        this.logger = NewRelicGradlePlugin.LOGGER
        this.extensions = project.extensions
        this.objects = project.objects
        this.dexguardHelper = new DexGuardHelper(project)

        try {
            this.extraProperties = extensions.getByType(ExtraPropertiesExtension.class) as ExtraPropertiesExtension
            this.android = extensions.getByType(AppExtension.class) as AppExtension
            this.androidComponents = extensions.getByType(AndroidComponentsExtension.class) as AndroidComponentsExtension

        } catch (GradleException e) {
            throw new HaltBuildException(e)
        }

        this.agpVersion = getAndNormalizeAGPVersion()

        // warn or throw if we can't instrument this build
        validatePluginSettings()

        this.variantAdapter = VariantAdapter.register(this)

        BuildHelper.NEWLN = getSystemPropertyProvider("line.separator").get()

    }

    void validatePluginSettings() {
        if (!getAndroid()) {
            throw new HaltBuildException("The New Relic agent plugin depends on the Android plugin." + NEWLN +
                    "Please apply an Android plugin before the New Relic agent: " + NEWLN +
                    "plugins {" + NEWLN +
                    "   id 'com.android.[application, library, feature, dynamic-feature]'" + NEWLN +
                    "   id 'newrelic'" + NEWLN +
                    "}")
        }

        if (GradleVersion.version(agpVersion) < GradleVersion.version(minSupportedAGPVersion)) {
            throw new HaltBuildException("The New Relic plugin is not compatible with Android Gradle plugin version ${agpVersion}."
                    + NEWLN
                    + "AGP versions ${minSupportedAGPVersion} - ${currentSupportedAGPVersion} are officially supported.")
        }

        if (GradleVersion.version(agpVersion) > GradleVersion.version(currentSupportedAGPVersion)) {
            def enableWarning = hasOptional(PROP_WARNING_AGP, true).toString().toLowerCase()
            if ((enableWarning != 'false') && (enableWarning != '0')) {
                warnOrHalt("The New Relic plugin may not be compatible with Android Gradle plugin version ${agpVersion}."
                        + NEWLN
                        + "AGP versions ${BuildHelper.minSupportedAGPVersion} - ${BuildHelper.currentSupportedAGPVersion} are officially supported.")
            }
        }

        if (GradleVersion.version(getGradleVersion()) < GradleVersion.version(BuildHelper.minSupportedGradleVersion)) {
            warnOrHalt("The New Relic plugin may not be compatible with Gradle version ${gradleVersion}."
                    + NEWLN
                    + "Gradle versions ${BuildHelper.minSupportedGradleVersion} and higher are officially supported.")
        }
    }

    /**
     * Fetching the reported AGP is problematic: the Version class has moved around
     * between releases of both the AGP and gradle-api. Try our best here to return the
     * correct value.
     *
     * @return Semver as a String
     */
    String getAndNormalizeAGPVersion() {
        String reportedAgpVersion = "unknown"

        try {
            getAndroidComponents().getPluginVersion().with {
                reportedAgpVersion = major + "." + minor + "." + micro
                if (previewType) {
                    reportedAgpVersion = reportedAgpVersion + "-" + previewType + preview
                }
            }

        } catch (MissingPropertyException e2) {
            // pluginVersion not available
            try {
                // AGP 3.6.+: com.android.Version.ANDROID_GRADLE_PLUGIN_VERSION
                final Class versionClass = BuildHelper.class.getClassLoader().loadClass("com.android.Version");
                reportedAgpVersion = versionClass.getDeclaredField("ANDROID_GRADLE_PLUGIN_VERSION").get(null).toString();

            } catch (ClassNotFoundException e) {
                try {
                    logger.error("AGP 3.6.+: $e")

                    // AGP 3.4 - 3.5.+: com.android.builder.model.Version.ANDROID_GRADLE_PLUGIN_VERSION
                    final Class versionClass = BuildHelper.class.getClassLoader().loadClass("com.android.builder.model.Version");
                    reportedAgpVersion = versionClass.getDeclaredField("ANDROID_GRADLE_PLUGIN_VERSION").get(null).toString();

                } catch (ClassNotFoundException e1) {
                    logger.error("AGP 3.4 - 3.5.+: $e1")
                    throw e1
                }
            }
        }

        // filter for unofficial (non-semver) version numbers
        try {
            // AGP version may contain '*-{qualifier}', which GradleVersion doesn't recognize
            GradleVersion.version(reportedAgpVersion)

        } catch (IllegalArgumentException e) {
            logger.warn("AGP version [$reportedAgpVersion] is not officially supported")
            def (_, version, qualifier) = (reportedAgpVersion =~ /^(\d{1,3}\.\d{1,3}\.\d{1,3})-(.*)$/)[0]
            reportedAgpVersion = version
        }

        return reportedAgpVersion
    }

    TaskProvider getVariantCompileTaskProvider(def variant) {
        try {
            def provider = variant.getJavaCompileProvider()
            if (provider) {
                return provider
            }
        } catch (Exception e) {
            logger.error("getVariantCompileTask: $e")
        }

        return variant.javaCompiler
    }

    def getProviderDefaultMapPath(def variant) {
        if (dexguardHelper.enabled) {
            return dexguardHelper.getDefaultMapPath(variant)
        }

        // Proguard/R8/DG8 report through AGP to this location
        return project.layout.projectDirectory.file("${project.layout.buildDirectory}/outputs/mapping/${variant.dirName}/mapping.txt")
    }

    TaskProvider getVariantBuildConfigTask(def variant) {
        TaskProvider provider
        try {
            provider = variantAdapter.getBuildConfigProvider(variant.name)
        } catch (Exception e) {
            logger.error("getVariantBuildConfigTask: $e")
        }

        return provider
    }

    /**
     * Returns a File object representing the variant's mapping file
     * This can be null for Dexguard 8.5.+
     *
     * If the extention's variantConfiguration is present, use that map file
     * if one is provided
     *
     * @param variant
     * @return File(variantMappingFile)
     */
    def getVariantMappingFile(def variant) {
        // First look in the plugin extension's variantConfiguration for overriding values:
        try {
            NewRelicExtension extension = extensions.getByName(NewRelicGradlePlugin.PLUGIN_EXTENSION_NAME)
            def variantConfiguration = extension.variantConfigurations.findByName(variant.name)

            if (variantConfiguration && variantConfiguration.mappingFile) {
                def variantMappingFile = variantConfiguration.mappingFile
                        .replace("<name>", variant.name)
                        .replace("<dirName>", variant.dirName)

                if (variantMappingFile) {
                    project.layout.projectDirectory.file(variantMappingFile)
                }
            }
        } catch (Exception) {
            // ignore
        }

        /*
        if (variant.respondsTo("getMappingFileProvider")) {
            FileCollection mappingFileProvider = variant.mappingFileProvider.getOrNull()

            // We will warn about not finding a mapping file later, so there's no need to warn here
            if (mappingFileProvider == null || mappingFileProvider.isEmpty()) {
                return null
            }
            return mappingFileProvider.first()
        }
        */


        try {
            def provider = variant.getMappingFileProvider()
            if (provider && provider.get()) {
                def fileCollection = provider.get()
                if (!fileCollection.empty) {
                    return fileCollection.singleFile
                }
            }
        } catch (Exception e) {
            logger.error("getVariantMappingFile: Map provider not found in variant [$variant.name]")
        }

        // If all else fails, default to default map locations
        return getProviderDefaultMapPath(variant)
    }

    def withDexGuardHelper(final def dexguardHelper) {
        this.dexguardHelper = dexguardHelper
    }

    def configurationCacheSupported() {
        GradleVersion.version(getGradleVersion()) >= GradleVersion.version(BuildHelper.minSupportedGradleConfigCacheVersion)
    }

    def configurationCacheEnabled() {
        try {
            // FIXME May also be enabled through command line: --configuration-cache
            def prop = project.providers.gradleProperty("org.gradle.unsafe.configuration-cache")
            return prop.present
        } catch (Exception) {
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
        } catch (Exception e) {
            logger.error(e)
            return objects.property(String).orElse(System.getProperty(key))
        }
    }

    def shouldApplyLegacyTransform() {
        try {
            getAndroidComponents().with {
                getPluginVersion().with {
                    return major < 7
                }
            }
        } catch (Exception) {}
        true
    }

    /**
     * Emit passed string as a warning. If PROP_WARNINGS_AS_ERROR is set, throw a HaltBuildException instead
     * Unsupported at present.
     *
     * @param Message to log or throw
     */
    void warnOrHalt(final String msg) {
        def haltOnWarning = hasOptional(BuildHelper.PROP_HALT_ON_ERROR, false).toString().toLowerCase()
        if ((haltOnWarning != 'false') && (haltOnWarning != '0')) {
            throw new HaltBuildException(msg)
        }
        logger.warn(msg)
    }

    def hasOptional(def key, Object defaultValue) {
        project.rootProject.hasProperty(key) ? project.rootProject[key] : defaultValue
    }

    def buildMetrics() {
        [
                agent      : agentVersion,
                agp        : agpVersion,
                gradle     : gradleVersion,
                java       : getSystemPropertyProvider('java.version').get(),
                dexguard   : [enabled: dexguardHelper.enabled, version: dexguardHelper.currentVersion],
                configCache: [supported: configurationCacheSupported(), enabled: configurationCacheEnabled()],
        ]
    }

    String buildMetricsAsJson() {
        try {
            JsonOutput.toJson(buildMetrics())
        } catch (Throwable) {
            ""
        }
    }

    String getGradleVersion() {
        return gradleVersion
    }

    String getAgpVersion() {
        return agpVersion
    }

    AppExtension getAndroid() {
        return android
    }

    AndroidComponentsExtension getAndroidComponents() {
        return androidComponents
    }
}
