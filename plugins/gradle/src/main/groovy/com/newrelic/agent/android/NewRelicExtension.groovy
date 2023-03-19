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

/**
 * NewRelic Android Agent plugin configuration
 */
abstract class NewRelicExtension {
    public static final String PLUGIN_EXTENSION_NAME = "newrelic"

    protected List<String> variantExclusionList = []          // ['Debug', 'Staging']
    protected List<String> variantMapUploadList = []          // ['Release', 'ProdRelease', 'Staging']
    protected List<String> packageExclusionList = []          // ['android.*', 'androidx.*']

    Property<Boolean> enabled
    Property<Boolean> instrumentTests
    Property<Boolean> variantMapsEnabled

    NamedDomainObjectContainer<VariantConfiguration> variantConfigurations

    static NewRelicExtension register(Project project) {
        return project.extensions.create(PLUGIN_EXTENSION_NAME, NewRelicExtension, project.getObjects())
    }

    NewRelicExtension(ObjectFactory objectFactory) {
        this.enabled = objectFactory.property(Boolean.class).convention(true)
        this.instrumentTests = objectFactory.property(Boolean.class).convention(false)
        this.variantMapsEnabled = objectFactory.property(Boolean.class).convention(true)
        this.variantConfigurations = objectFactory.domainObjectContainer(VariantConfiguration, { name ->
            objectFactory.newInstance(VariantConfiguration.class, name)
        })
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
     *          debug {
     *              instrument = false
     *              uploadMappingFile = true
     *          }
     *          release {
     *              uploadMappingFile = true
     *              mappingFile = 'build/outputs/mapping/release/mapping.txt'
     *          }
     *          ... {
     *              instrument = true
     *              uploadMappingFile = true
     *              mappingFile = 'build/outputs/mapping/<variantName>/mapping.txt'
     *          }
     *      }
     * }
     * </pre>
     */

    @Nested
    void variantConfigurations(Action<? super NamedDomainObjectContainer<VariantConfiguration>> action) {
        action.execute(variantConfigurations);
        variantConfigurations.each { config ->
            if (!config.instrument) {
                variantExclusionList.add(config.name.toLowerCase())
            }
            if (config.uploadMappingFile) {
                variantMapUploadList.add(config.name.toLowerCase())
            }
        }
    }

    @Deprecated
    void uploadMapsForVariant(Object... e) {
        variantMapUploadList.clear()
        variantMapUploadList.addAll(e.collect { i -> i.toString().toLowerCase() })

        // update variants configs with these values
        variantMapUploadList.each { variantName ->
            variantConfigurations.findByName(variantName)?.with {
                uploadMappingFile = true
            }
        }
    }

    @Deprecated
    void excludeVariantInstrumentation(Object... e) {
        variantExclusionList.clear()
        variantExclusionList.addAll(e.collect { i -> i.toString().toLowerCase() })

        // update variants configs with these values
        variantExclusionList.each { variantName ->
            variantConfigurations.findByName(variantName)?.with {
                instrument = false
            }
        }
    }

    @Deprecated
    void excludePackageInstrumentation(Object... e) {
        packageExclusionList.clear()
        packageExclusionList.addAll(e.collect { i -> i.toString().toLowerCase() })

        variantMapUploadList.each { variantName ->
            variantConfigurations.findByName(variantName)?.with {
                instrument = true
            }
        }
    }

    boolean shouldExcludeVariant(String variantName) {
        variantConfigurations.findByName(variantName.toLowerCase())?.with {
            return instrument
        }
        return variantExclusionList.contains(variantName.toLowerCase())
    }

    boolean shouldIncludeVariant(String variantName) {
        !shouldExcludeVariant(variantName)
    }

    boolean shouldInstrumentTests() {
        return instrumentTests
    }

    boolean shouldIncludeMapUpload(String variantName) {
        variantConfigurations.findByName(variantName.toLowerCase())?.with {
            return uploadMappingFile
        }
        return variantMapUploadList.contains(variantName.toLowerCase())
    }
}
