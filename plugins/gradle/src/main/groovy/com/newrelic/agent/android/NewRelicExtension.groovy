/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android

import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.Nested

/**
 * NewRelic Android Agent plugin configuration
 */
abstract class NewRelicExtension {

    protected List<String> variantExclusionList = []          // ['Debug', 'Staging']
    protected List<String> variantMapUploadList = []          // ['Release', 'ProdRelease', 'Staging']
    protected List<String> packageExclusionList = []          // ['android.*', 'androidx.*']

    boolean enabled = true
    boolean instrumentTests = false
    boolean variantMapsEnabled = true

    NamedDomainObjectContainer<VariantConfiguration> variantConfigurations

    NewRelicExtension(ObjectFactory objectFactory) {
        this.variantConfigurations = objectFactory.domainObjectContainer(VariantConfiguration, { name ->
            objectFactory.newInstance(VariantConfiguration.class, name)
        })
    }

    boolean isEnabled() {
        return enabled
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
