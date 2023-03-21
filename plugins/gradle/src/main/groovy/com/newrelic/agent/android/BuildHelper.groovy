/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import org.gradle.api.BuildCancelledException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.ProjectLayout
import org.gradle.api.logging.Logger
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.plugins.ExtraPropertiesExtension
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
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

    public static final String currentGradleVersion = GradleVersion.current().version

    static final String currentSupportedAGPVersion = "8.0"
    static final String minSupportedAGPVersion = '4.0.0'
    static final String minSupportedGradleVersion = '7.0.2'

    static final String minSupportedGradleConfigCacheVersion = '6.6'
    static final String minSupportedAGPConfigCacheVersion = '7.0.2'

    public static String NEWLN = "\r\n"

    final Project project
    final Logger logger
    final ProviderFactory providers
    final ExtensionContainer extensions
    final ProjectLayout layout
    final ObjectFactory objects
    final String agpVersion

    final ApplicationExtension android
    final ApplicationAndroidComponentsExtension androidComponents
    final ExtraPropertiesExtension extraProperties

    DexGuardHelper dexguardHelper

    static final AtomicReference<BuildHelper> instance = new AtomicReference<BuildHelper>(null)

    BuildHelper(Project project) {
        this.project = project
        this.logger = NewRelicGradlePlugin.LOGGER
        this.providers = project.providers
        this.extensions = project.extensions
        this.layout = project.layout
        this.objects = project.objects
        this.dexguardHelper = new DexGuardHelper(project)
        this.extraProperties = extensions.getByName("ext")
                as ExtraPropertiesExtension

        try {
            this.android = extensions.getByName("android")
                    as ApplicationExtension
            this.androidComponents = extensions.getByName("androidComponents")
                    as ApplicationAndroidComponentsExtension
        } catch (Exception e) {
            throw new BuildCancelledException(e)
        }

        this.agpVersion = getAGPVersion()

        // warn or throw if we can't instrument this build
        validatePlugin()

        BuildHelper.instance.compareAndSet(null, this)
        BuildHelper.NEWLN = getSystemPropertyProvider("line.separator").get()

    }

    void validatePlugin() {
        if (!android) {
            throw new BuildCancelledException("The New Relic agent plugin depends on the Android plugin." + NEWLN +
                    "Please apply an Android plugin before the New Relic agent: " + NEWLN +
                    "plugins {" + NEWLN +
                    "   id 'com.android.[application, library, feature, dynamic-feature]'" + NEWLN +
                    "   id 'newrelic'" + NEWLN +
                    "}")
        }

        if (GradleVersion.version(agpVersion) < GradleVersion.version(minSupportedAGPVersion)) {
            throw new BuildCancelledException("The New Relic plugin is not compatible with Android Gradle plugin version ${agpVersion}."
                    + NEWLN
                    + "AGP versions ${minSupportedAGPVersion} - ${currentSupportedAGPVersion} are officially supported.")
        }

        if (GradleVersion.version(agpVersion) > GradleVersion.version(currentSupportedAGPVersion)) {
            def enableWarning = hasOptional(PROP_WARNING_AGP, true).toString().toLowerCase()
            if ((enableWarning != 'false') && (enableWarning != '0')) {
                warnOrHalt("The New Relic plugin may not be compatible with Android Gradle plugin version ${agpVersion}."
                        + NEWLN
                        + "AGP versions ${minSupportedAGPVersion} - ${currentSupportedAGPVersion} are officially supported.")
            }
        }

        if (GradleVersion.version(currentGradleVersion) < GradleVersion.version(minSupportedGradleVersion)) {
            warnOrHalt("The New Relic plugin may not be compatible with Gradle version ${currentGradleVersion}."
                    + NEWLN
                    + "Gradle versions ${minSupportedGradleVersion} and higher are officially supported.")
        }
    }

    /**
     * Fetching the reported AGP is problematic: the Version class has moved around
     * between releases of both the AGP and gradle-api. Try our best here to return the
     * correct value.
     *
     * @return Semver as a String
     */
    String getAGPVersion() {
        String reportedAgpVersion = "unknown"

        try {
            if (androidComponents && androidComponents.pluginVersion) {
                reportedAgpVersion = androidComponents.pluginVersion.major + "." +
                        androidComponents.pluginVersion.minor + "." +
                        androidComponents.pluginVersion.micro

                if (androidComponents.pluginVersion.previewType) {
                    reportedAgpVersion = reportedAgpVersion + "-" +
                            androidComponents.pluginVersion.previewType + androidComponents.pluginVersion.preview
                }
            }

        } catch (MissingPropertyException) {
            // pluginVersion not available in 4.x
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
            GradleVersion version = GradleVersion.version(reportedAgpVersion)

        } catch (IllegalArgumentException e) {
            logger.warn("AGP version [$reportedAgpVersion] is not officially supported")
            def (_, version, qualifier) = (reportedAgpVersion =~ /^(\d{1,3}\.\d{1,3}\.\d{1,3})-(.*)$/)[0]
            reportedAgpVersion = version
        }

        return reportedAgpVersion
    }

    Task getVariantCompileTask(def variant) {
        try {
            def provider = variant.getJavaCompileProvider()
            if (provider && provider.get()) {
                return provider.get()
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
        return layout.projectDirectory.file("${layout.buildDirectory}/outputs/mapping/${variant.dirName}/mapping.txt")
    }

    def getVariantBuildConfigTask(def variant) {
        try {
            def provider = variant.getGenerateBuildConfigProvider()
            if (provider) {
                return provider
            }
        } catch (Exception e) {
            logger.error("getVariantBuildConfigTask: $e")
        }

        return variant.generateBuildConfig
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
                    layout.projectDirectory.file(variantMappingFile)
                }
            }
        } catch (Exception) {
            // ignore
        }

        // Next check the AGP's variant config
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

    def withDexGuardHelper(def dexguardHelper) {
        this.dexguardHelper = dexguardHelper
    }

    def configurationCacheSupported() {
        GradleVersion.version(currentGradleVersion) >= GradleVersion.version(minSupportedGradleConfigCacheVersion)
    }

    def configurationCacheEnabled() {
        try {
            // FIXME May also be enabled through command line: --configuration-cache
            def prop = providers.gradleProperty("org.gradle.unsafe.configuration-cache")
            return prop.present
        } catch (Exception) {
            false
        }
    }

    Provider<String> getSystemPropertyProvider(String key) {
        try {
            def provider = providers.systemProperty(key)
            if (configurationCacheSupported()) {
                return provider.forUseAtConfigurationTime()
            }
            return provider
        } catch (Exception e) {
            logger.error(e)
            return objects.property(String).orElse(System.getProperty(key))
        }
    }

    def shouldUseAGPTransformAPI() {
        try {
            androidComponents && androidComponents.pluginVersion
                    && androidComponents.pluginVersion.major < 7
        } catch (Exception) {
            true
        }
    }

    def shouldUseArtifactsAPI() {
        try {
            androidComponents && androidComponents.pluginVersion
                    && androidComponents.pluginVersion.major >= 7
                    && androidComponents.pluginVersion.minor >= 0
        } catch (Exception) {
            false
        }
    }

    def reconfigure() {
        /* new API configuration
        androidComponents.configure(
                project,
                extension,
                "cliExecutable",
                "orgParameter",
                "projectParameter"
        )
        */
    }

    /**
     * Emit passed string as a warning. If PROP_WARNINGS_AS_ERROR is set, throw a HaltBuildException instead
     * Unsupported at present.
     *
     * @param Message to log ro throw
     */
    void warnOrHalt(final String msg) {
        def haltOnWarning = hasOptional(PROP_HALT_ON_ERROR, false).toString().toLowerCase()
        if ((haltOnWarning != 'false') && (haltOnWarning != '0')) {
            throw new BuildCancelledException(msg)
        } else {
            logger.warn(msg)
        }
    }

    private def hasOptional(def key, def defaultValue) {
        project.rootProject.hasProperty(key) ? project.rootProject[key] : defaultValue
    }

}
