/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android

import com.newrelic.agent.compile.HaltBuildException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.ProjectLayout
import org.gradle.api.logging.Logger
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.util.GradleVersion

import java.util.concurrent.atomic.AtomicReference

class BuildHelper {

    /**
     * @https: //developer.android.com/studio/releases/gradle-plugin#updating-gradle
     * Plugin version	Required Gradle version
     * 3.4.0 - 3.4.1	5.1.1
     * 3.5.0 - 3.5.3    5.4.1
     * 3.6.+	        5.6.4
     * 4.0.+            6.1.1
     * 4.1.+            6.5.+       // min supported Gradle configuration cache level
     * 4.2.+            6.7.+
     * 7.0              7.0.2       // min supported AGP that supports configuration caching
     * 7.1	            7.2.+
     * 7.2              7.3.3
     *
     * */

    static final String PROP_WARNING_AGP = "newrelic.warning.agp"

    public static final GradleVersion currentGradleVersion = GradleVersion.current()

    static final GradleVersion minSupportedAGPVersion = GradleVersion.version('4.0.1')
    static final GradleVersion minSupportedGradleVersion = GradleVersion.version('6.1.1')
    static final GradleVersion currentSupportedAGPVersion = GradleVersion.version('7.4.0')
    static final GradleVersion legacyGradleVersion = GradleVersion.version('4.10.0')
    static final GradleVersion breakingGradleVersion = GradleVersion.version('5.6.4')

    static final GradleVersion minSupportedGradleConfigCacheVersion = GradleVersion.version('6.5')
    static final GradleVersion minSupportedAGPConfigCacheVersion = GradleVersion.version('7.0.2')

    public static final boolean legacyGradle = (currentGradleVersion < legacyGradleVersion)

    final Logger logger
    final ProviderFactory providers
    final ExtensionContainer extensions
    final ProjectLayout layout
    final ObjectFactory objects
    final GradleVersion agpVersion
    final String NEWLN

    DexGuardHelper dexguardHelper

    static final AtomicReference<BuildHelper> instance = new AtomicReference<BuildHelper>(null)

    BuildHelper(Project project) {
        this.logger = project.logger
        this.providers = project.providers
        this.extensions = project.extensions
        this.layout = project.layout
        this.objects = project.objects
        this.dexguardHelper = new DexGuardHelper(project)
        this.NEWLN = getSystemPropertyProvider("line.separator").get()

        def reportedAGPVersion = getAGPVersion()

        try {
            // AGP version may contain '*-{qualifier}', which GradleVersion doesn't recognize
            this.agpVersion = GradleVersion.version(reportedAGPVersion)

        } catch (IllegalArgumentException e) {
            logger.warn("[newrelic.warn] AGP version [$reportedAGPVersion] is not officially supported")
            def (_, agpVersion, qualifier) = (reportedAGPVersion =~ /^(\d{1,3}\.\d{1,3}\.\d{1,3})-(.*)$/)[0]
            this.agpVersion = GradleVersion.version(agpVersion)
        }

        if (agpVersion.baseVersion < minSupportedAGPVersion) {
            throw new HaltBuildException("The New Relic plugin is not compatible with Android Gradle plugin version ${agpVersion.version}."
                    + NEWLN
                    + "AGP versions ${minSupportedAGPVersion.getVersion()} - ${currentSupportedAGPVersion.getVersion()} are officially supported.")
        }

        def hasOptional = { key, defaultValue -> project.rootProject.hasProperty(key) ? project.rootProject[key] : defaultValue }
        def enableWarning = hasOptional(PROP_WARNING_AGP, true).toString().toLowerCase()
        if ((enableWarning != 'false') && (enableWarning != '0')) {
            if (agpVersion.baseVersion > currentSupportedAGPVersion) {
                logger.warn("[newrelic.warn] The New Relic plugin may not be compatible with Android Gradle plugin version ${agpVersion.version}."
                        + NEWLN
                        + "[newrelic.warn] AGP versions ${minSupportedAGPVersion.getVersion()} - ${currentSupportedAGPVersion.getVersion()} are officially supported.")
            }
        }

        if (currentGradleVersion < minSupportedGradleVersion) {
            logger.warn("[newrelic.warn] The New Relic plugin may not be compatible with Gradle version ${currentGradleVersion.version}."
                    + NEWLN
                    + "[newrelic.warn] Gradle versions ${minSupportedGradleVersion.getVersion()} and higher are officially supported.")
        }

        BuildHelper.instance.compareAndSet(null, this)
    }

    /**
     * Fetching the reported AGP is problematic: the Version class has moved around
     * between releases of both the AGP gradle-api. Try our best here to return the correct value.
     *
     * For AGP 3.5.+ and below, the version is read from com.android.builder.model.Version
     * Otherwise fetch it from com.android.Version
     *
     * @return Semver as String
     */
    String getAGPVersion() {
        String agpVersion = "unknown"
        ClassLoader classLoader = BuildHelper.class.getClassLoader();
        Class versionClass
        try {
            // AGP 3.6.+: com.android.Version.ANDROID_GRADLE_PLUGIN_VERSION
            versionClass = classLoader.loadClass("com.android.Version");
            agpVersion = versionClass.getDeclaredField("ANDROID_GRADLE_PLUGIN_VERSION").get(null).toString();
        } catch (java.lang.ClassNotFoundException unused) {
            try {
                // AGP 3.4 - 3.5.+: com.android.builder.model.Version.ANDROID_GRADLE_PLUGIN_VERSION
                logger.error("[newrelic.debug] getAGPVersion: $unused")
                versionClass = classLoader.loadClass("com.android.builder.model.Version");
                agpVersion = versionClass.getDeclaredField("ANDROID_GRADLE_PLUGIN_VERSION").get(null).toString();
            } catch (java.lang.ClassNotFoundException ignored) {
                logger.error("[newrelic.debug] getAGPVersion: unknown - $ignored")
            }
        }

        return agpVersion
    }

    Task getVariantCompileTask(def variant) {
        try {
            if (!legacyGradle) {
                def provider = variant.getJavaCompileProvider()
                if (provider && provider.get()) {
                    return provider.get()
                }
            }
        } catch (Exception e) {
            logger.error("[newrelic.debug] getVariantCompileTask: $e")
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
            if (!legacyGradle) {
                def provider = variant.getGenerateBuildConfigProvider()
                if (provider) {
                    return provider
                }
            }
        } catch (Exception e) {
            logger.error("[newrelic.debug] getVariantBuildConfigTask: $e")
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
        } catch (Exception UnknownDomainObjectException) {
            // ignore
        }

        // Next check the AGP's variant config
        try {
            if (currentGradleVersion >= breakingGradleVersion) {
                def provider = variant.getMappingFileProvider()
                if (provider && provider.get()) {
                    def fileCollection = provider.get()
                    if (!fileCollection.empty) {
                        return fileCollection.singleFile
                    }
                }
            }
        } catch (Exception e) {
            logger.error("[newrelic.debug] getVariantMappingFile: Map provider not found in variant [$variant.name]")
        }

        // If all else fails, default to default map locations
        return getProviderDefaultMapPath(variant)
    }

    def withDexGuardHelper(def dexguardHelper) {
        this.dexguardHelper = dexguardHelper
    }

    def BuildCacheSupported() {
        false   // TODO
    }

    def buildCacheEnabled() {
        false   // TODO
    }

    def configurationCacheSupported() {
        (BuildHelper.currentGradleVersion.compareTo(BuildHelper.minSupportedGradleConfigCacheVersion) == 1)
    }

    def configurationCacheEnabled() {
        try {
            // TODO May also be enabled through command line: --configuration-cache
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
            return objects.property(String).orElse(System.getProperty(key))
        }
    }
}
