/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android

import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.gradle.AppExtension
import com.newrelic.agent.InstrumentationAgent
import com.newrelic.agent.android.obfuscation.Proguard
import com.newrelic.agent.compile.HaltBuildException
import com.newrelic.agent.util.BuildId
import groovy.transform.CompileDynamic
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.UnknownTaskException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.Logger
import org.gradle.api.plugins.ExtraPropertiesExtension
import org.gradle.api.provider.Provider
import org.gradle.util.GradleVersion

import java.util.concurrent.atomic.AtomicReference

@CompileDynamic
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

    static final String DEX_PROGUARD = "proguard"
    static final String DEX_R8 = "r8"

    public static final String agentVersion = InstrumentationAgent.version

    public final String gradleVersion = GradleVersion.current().version

    static final String currentSupportedAGPVersion = "8.0"
    static final String minSupportedAGPVersion = '4.2.0'
    static final String minSupportedGradleVersion = '7.0.2'
    static final String minSupportedGradleConfigCacheVersion = '6.6'
    static final String minSupportedAGPConfigCacheVersion = '7.0.2'

    public static String NEWLN = "\r\n"

    final Project project
    final NewRelicExtension extension
    final AppExtension android
    final AndroidComponentsExtension androidComponents
    final ExtraPropertiesExtension extraProperties

    VariantAdapter variantAdapter
    String agpVersion
    Logger logger
    DexGuardHelper dexguardHelper

    static final AtomicReference<BuildHelper> INSTANCE = new AtomicReference<BuildHelper>(null)

    static BuildHelper register(Project project) {
        BuildHelper.INSTANCE.compareAndSet(null, new BuildHelper(project))
        return BuildHelper.INSTANCE.get()
    }

    BuildHelper(Project project) {
        this.project = project
        this.logger = NewRelicGradlePlugin.LOGGER
        this.dexguardHelper = DexGuardHelper.register(this)

        try {
            this.extension = project.extensions.getByType(NewRelicExtension.class) as NewRelicExtension
            this.extraProperties = project.extensions.getByType(ExtraPropertiesExtension.class) as ExtraPropertiesExtension
            this.android = project.extensions.getByType(AppExtension.class) as AppExtension
            this.androidComponents = project.extensions.getByType(AndroidComponentsExtension.class) as AndroidComponentsExtension

            this.agpVersion = getAndNormalizeAGPVersion()

            // warn or throw if we can't instrument this build
            validatePluginSettings()

            NEWLN = getSystemPropertyProvider("line.separator").get()

            this.variantAdapter = VariantAdapter.register(this)

        } catch (Exception e) {
            throw new HaltBuildException(e)
        }
    }

    void validatePluginSettings() throws GradleException {
        if (!getAndroid()) {
            throw new GradleException("The New Relic agent plugin depends on the Android plugin." + NEWLN +
                    "Please apply an Android plugin before the New Relic agent: " + NEWLN +
                    "plugins {" + NEWLN +
                    "   id 'com.android.[application, library, feature, dynamic-feature]'" + NEWLN +
                    "   id 'newrelic'" + NEWLN +
                    "}")
        }

        if (GradleVersion.version(getAgpVersion()) < GradleVersion.version(BuildHelper.minSupportedAGPVersion)) {
            throw new GradleException("The New Relic plugin is not compatible with Android Gradle plugin version ${agpVersion}."
                    + NEWLN
                    + "AGP versions ${minSupportedAGPVersion} - ${currentSupportedAGPVersion} are officially supported.")
        }

        if (GradleVersion.version(getAgpVersion()) > GradleVersion.version(BuildHelper.currentSupportedAGPVersion)) {
            def enableWarning = hasOptional(BuildHelper.PROP_WARNING_AGP, true).toString().toLowerCase()
            if ((enableWarning != 'false') && (enableWarning != '0')) {
                warnOrHalt("The New Relic plugin may not be compatible with Android Gradle plugin version ${getAgpVersion()}."
                        + NEWLN
                        + "AGP versions ${minSupportedAGPVersion} - ${currentSupportedAGPVersion} are officially supported.")
            }
        }

        if (GradleVersion.version(getGradleVersion()) < GradleVersion.version(BuildHelper.minSupportedGradleVersion)) {
            warnOrHalt("The New Relic plugin may not be compatible with Gradle version ${getGradleVersion()}."
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
    String getAndNormalizeAGPVersion() {
        String reportedAgpVersion = "unknown"

        try {
            getAndroidComponents().getPluginVersion().with {
                reportedAgpVersion = major + "." + minor + "." + micro
                if (previewType) {
                    reportedAgpVersion = reportedAgpVersion + "-" + previewType + preview
                }
            }

        } catch (MissingPropertyException) {
            // pluginVersion not available
            try {
                // AGP 3.6.+: com.android.Version.ANDROID_GRADLE_PLUGIN_VERSION
                final Class versionClass = BuildHelper.class.getClassLoader().loadClass("com.android.Version")
                reportedAgpVersion = versionClass.getDeclaredField("ANDROID_GRADLE_PLUGIN_VERSION").get(null).toString()

            } catch (ClassNotFoundException e) {
                try {
                    logger.error("AGP 3.6.+: $e")

                    // AGP 3.4 - 3.5.+: com.android.builder.model.Version.ANDROID_GRADLE_PLUGIN_VERSION
                    final Class versionClass = BuildHelper.class.getClassLoader().loadClass("com.android.builder.model.Version")
                    reportedAgpVersion = versionClass.getDeclaredField("ANDROID_GRADLE_PLUGIN_VERSION").get(null).toString()

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

    RegularFileProperty getDefaultMapPathProvider(String variantDirName) {
        // Proguard/R8/DG8 report through AGP to this location
        return project.objects.fileProperty().set("${project.layout.buildDirectory}/outputs/mapping/${variantDirName}/mapping.txt")
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
            logger.error("getSystemPropertyProvider: ${e.message}")
        }

        return project.objects.property(String).orElse(System.getProperty(key))
    }

    def isUsingLegacyTransform() {
        variantAdapter instanceof VariantAdapter.AGP4Adapter
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
            throw new GradleException(msg)
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

    def configureVariantModel() {
        finalizeMapUploadTasks()
    }

    // gettors to assist in mocking

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

    boolean checkDexGuard() {
        return project.plugins.hasPlugin("dexguard")
    }

    boolean checkInstantApps() {
        return project.plugins.hasPlugin("com.android.instantapp") ||
                project.plugins.hasPlugin("com.android.feature") ||
                project.plugins.hasPlugin("com.android.dynamic-feature")
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

        if (dexguardHelper.enabled) {
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

    def injectMapUploadFinalizer(Task targetTask, String variantName, Closure closure) {
        try {
            def targetNameCap = targetTask.getName().capitalize()
            def mapUploadTaskName = "${NewRelicMapUploadTask.NAME}}${targetNameCap}"
            def mapUploadTaskProvider = project.tasks.named(mapUploadTaskName, NewRelicMapUploadTask)

            if (mapUploadTaskProvider.isPresent()) {
                mapUploadTaskProvider.configure { mapUploadTask ->
                    try {
                        // update the map file iif needed
                        if (closure) {
                            mapUploadTask.mappingFile.set(closure(mapUploadTask.mappingFile.getAsFile().get()))
                        }

                        // connect config task buildId to map upload task
                        project.tasks.named("${NewRelicConfigTask.NAME}${variantName.capitalize()}").configure { configTask ->
                            mapUploadTask.dependsOn configTask
                        }

                    } catch (Exception e) {
                        mapUploadTask.buildId = BuildId.getBuildId(variantName)
                        logger.error("injectMapUploadFinalizer: $e")
                    }

                    mapUploadTask.dependsOn targetTask
                }
            }

            targetTask.finalizedBy mapUploadTaskProvider

            return mapUploadTaskProvider

        } catch (Exception e) {
            // task for this variant not available
            logger.error("injectMapUploadFinalizer: $e")
        }
    }

    def injectMapUploadFinalizer(String targetTaskName, String variantName) {
        try {
            def provider = project.tasks.named("${targetTaskName}${variantName.capitalize()}") { targetTask ->
                return injectMapUploadFinalizer(targetTask, variantName, { File mappingFile ->
                    logger.debug("[injectMapUploadFinalizer] Injecting NewRelicMapUploadTask[${mappingFile}] as finalizer to ${targetTask.name}")
                    mappingFile
                })
            }
        } catch (UnknownTaskException ignored) {
            // task for this variant not available
        }
    }


    // called during config phase
    void finalizeMapUploadTasks() {
        try {
            // library projects do not produce maps
            if (checkLibrary()) {
                return
            }

            // dexguard maps are handled separately
            if (dexguardHelper.enabled) {
                return
            }

            // do all the variants if variantMapsEnabled, or only those provided in the extension. Default is *release*.
            if (!extension.variantMapsEnabled.get() || extension.variantMapUploadList.isEmpty()) {
                logger.debug("Maps will be tagged and uploaded for all variants")
            } else {
                logger.debug("Maps will be tagged and uploaded for variants ${extension.variantMapUploadList}")
            }

            // FIXME move to variantadapter
            variantAdapter.getVariantValues().each { variant ->
                if (variantAdapter.shouldUploadVariantMap(variant.name)) {
                    def buildType = variantAdapter.getBuildTypeProvider(variant.name)

                    if (buildType.isPresent() && buildType.get().minifyEnabled) {
                        [DEX_PROGUARD, DEX_R8].each { dexName ->
                            ["transformClassesAndResourcesWith${dexName.capitalize()}For",
                             "minify${variant.name.capitalize()}With${dexName.capitalize()}"].each { taskName ->
                                injectMapUploadFinalizer(taskName, variant.name)
                            }
                        }

                    } else {
                        logger.debug("Map upload ignored: build type[$variant.buildType.name] is not minified.")
                    }
                } else {
                    logger.debug("Map upload ignored for variant[$variant.name]")
                }
            }
        } catch (Exception e) {
            // ignored: task doesn't exist if proguard not enabled
            logger.warn("finalizeMapUploadTasks: $e")
        }
    }

}
