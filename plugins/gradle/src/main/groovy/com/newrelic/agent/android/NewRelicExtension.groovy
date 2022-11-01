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

    List<String> variantExclusionList = []          // ['Debug', 'Staging']
    List<String> variantMapUploadList = ['release'] // ['Release', 'ProdRelease', 'Staging']
    List<String> packageExclusionList = []          // ['android.*', 'androidx.*']

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
     *              instrument false
     *              uploadMappingFile true
     *              mappingFile 'build/outputs/mapping/qa/mapping.txt'
     *          }
     *          release {
     *              uploadMappingFile = true
     *              mappingFile = 'build/outputs/mapping/release/mapping.txt'
     *          }
     *          ... {
     *              mappingFile = 'build/outputs/mapping/<...>/mapping.txt' *
     *          }
     *      }
     * }
     * </pre>
     */

    @Nested
    def variantConfigurations(final Closure closure) {
        variantConfigurations.configure(closure)
    }

    @Nested
    void variantConfigurations(Action<? super NamedDomainObjectContainer<VariantConfiguration>> action) {
        action.execute(variantConfigurations);
    }

    @Deprecated
    void uploadMapsForVariant(Object... e) {
        variantMapUploadList.clear()
        variantMapUploadList.addAll(e.collect { i -> i.toString().toLowerCase() })

        // update variants configs with these values
        variantMapUploadList.each { variantName ->
            try {
                variantConfigurations.getByName(variantName) {
                    uploadMappingFile = true
                }
            } catch (UnknownDomainObjectException) {
                // ignored
            }
        }
    }

    @Deprecated
    void excludeVariantInstrumentation(Object... e) {
        variantExclusionList.clear()
        variantExclusionList.addAll(e.collect { i -> i.toString().toLowerCase() })

        // update variants configs with these values
        variantMapUploadList.each { variantName ->
            try {
                variantConfigurations.getByName(variantName) {
                    instrument = false
                }
            } catch (UnknownDomainObjectException) {
                // ignored
            }
        }
    }

    @Deprecated
    void excludePackageInstrumentation(Object... e) {
        packageExclusionList.clear()
        packageExclusionList.addAll(e.collect { i -> i.toString().toLowerCase() })

        variantMapUploadList.each { variantName ->
            try {
                variantConfigurations.getByName(variantName) {
                    instrument = true
                }
            } catch (UnknownDomainObjectException) {
                // ignored
            }
        }
    }

    boolean shouldExcludeVariant(String variantName) {
        return variantExclusionList.contains(variantName.toLowerCase())
    }

    boolean shouldInstrumentTests() {
        return instrumentTests
    }

    boolean shouldIncludeMapUpload(String variantName) {
        return variantMapUploadList.contains(variantName.toLowerCase())
    }
}
