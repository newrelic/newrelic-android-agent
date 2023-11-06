/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android

import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Nested

import javax.inject.Inject

/**
 * NewRelic Android Agent plugin configuration
 */
abstract class NewRelicExtension {
    public static final String PLUGIN_EXTENSION_NAME = "newrelic"

    /**
     * Variant name spec is loose: it can be the literal name of the variant, the build type
     * or product flavor.
     */
    protected final Set<String> variantExclusions = []              // ['Debug', 'staging']
    protected final Set<String> variantMapUploads = ['release']     // ['Release', 'ProdRelease', 'Staging']
    protected final Set<String> packageExclusions = []              // ['android.*', 'androidx.*']

    Property<Boolean> enabled
    Property<Boolean> instrumentTests
    Property<Boolean> variantMapsEnabled

    NamedDomainObjectContainer<VariantConfiguration> variantConfigurations

    static NewRelicExtension register(Project project) {
        try {
            return project.extensions.create(PLUGIN_EXTENSION_NAME, NewRelicExtension, project)
        } catch (Exception) {
            return project.extensions.getByType(NewRelicExtension.class) as NewRelicExtension
        }
    }

    @Inject
    NewRelicExtension(Project project) {
        ObjectFactory objectFactory = project.getObjects()

        this.enabled = objectFactory.property(Boolean.class).convention(true)
        this.instrumentTests = objectFactory.property(Boolean.class).convention(false)
        this.variantMapsEnabled = objectFactory.property(Boolean.class).convention(true)
        this.variantConfigurations = objectFactory.domainObjectContainer(VariantConfiguration, { name ->
            objectFactory.newInstance(VariantConfiguration.class, name, project)
        })
    }

    boolean setEnabled(boolean state) {
        return enabled.set(state)
    }

    boolean getEnabled() {
        return enabled.get()
    }

    /*
     * Use NamedDomainObjectContainer.configure() in DSL to
     * automatically create VariantConfiguration instances.
     *
     * <pre>
     * newRelic {
     *      variantConfigurations {
     *          // The variant name spec is loose:it can be the literal name of the the variant,
     *          // the build type or product flavor:
     *
     *          debug {
     *              instrument = false
     *              uploadMappingFile = true
     *          }
     *          release {
     *              uploadMappingFile = true
     *              mappingFile = 'build/outputs/mapping/release/mapping.txt'
     *          }
     *
     *          // The plugin will also look for and replace these tokens with variant values:
     *          //  * <name> the variant name
     *          //  * <dirName> The variant directory name component (usually the same as name)
     *
     *          debugFlavor {
     *              instrument = true
     *              uploadMappingFile = true
     *              mappingFile = 'build/outputs/outofbounds/<dirName>/<name>-mapping.txt'
     *          }
     *      }
     * }
     * </pre>
     */

    @Nested
    void variantConfigurations(Action<? super NamedDomainObjectContainer<VariantConfiguration>> action) {
        action.execute(variantConfigurations);
        variantConfigurations.each { config ->
            def normalizedConfigName = config.name.toLowerCase()

            if (!config.instrument) {
                variantExclusions.add(normalizedConfigName)
            }

            if (config.uploadMappingFile) {
                variantMapUploads.add(normalizedConfigName)
            }
        }
    }

    void uploadMapsForVariant(String... e) {
        variantMapUploads.clear()
        variantMapUploads.addAll(e.collect { i -> i.toString().toLowerCase() })

        // update variants configs with these values
        variantMapUploads.each { variantName ->
            variantConfigurations.findByName(variantName)?.with {
                it.uploadMappingFile = true
            }
        }
    }

    void excludeVariantInstrumentation(String... e) {
        variantExclusions.clear()
        variantExclusions.addAll(e.collect { i -> i.toString().toLowerCase() })

        // update variants configs with these values
        variantExclusions.each { variantName ->
            variantConfigurations.findByName(variantName)?.with {
                it.instrument = false
            }
        }
    }

    void excludePackageInstrumentation(String... e) {
        packageExclusions.clear()
        packageExclusions.addAll(e.collect { i -> i.toString().toLowerCase().replace('/', '.') })

        // update variants configs with these values
        variantExclusions.each { variantName ->
            variantConfigurations.findByName(variantName)?.with {
                it.packageExclusions = packageExclusions
            }
        }
    }

    boolean shouldExcludeVariant(String variantName) {
        variantConfigurations.findByName(variantName.toLowerCase())?.with {
            return instrument
        }
        return !variantExclusions.findAll { variantName.toLowerCase() == it || variantName.endsWith(it.capitalize()) }.empty
    }

    boolean shouldIncludeVariant(String variantName) {
        !shouldExcludeVariant(variantName)
    }

    boolean shouldInstrumentTests() {
        return instrumentTests.get()
    }

    boolean shouldIncludeMapUpload(String variantName) {
        variantConfigurations.findByName(variantName.toLowerCase())?.with {
            return uploadMappingFile
        }

        return variantMapUploads.isEmpty() ||
                !variantMapUploads.findAll { variantName.toLowerCase() == it || variantName.endsWith(it.capitalize()) }.empty
    }

    boolean shouldExcludePackageInstrumentation(String packageName) {
        def pkg = packageName.toLowerCase().replace('/', '.')
        return !packageExclusions.findAll { it ->
            pkg.toLowerCase().startsWith(it) || pkg.toLowerCase().matches(it)
        }.empty
    }
}
