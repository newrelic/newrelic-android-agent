/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android

import org.gradle.api.Project

import javax.inject.Inject

abstract class VariantConfiguration {
    final String name
    final Project project

    boolean instrument
    boolean uploadMappingFile
    File mappingFile
    Set<String> packageExclusions

    @Inject
    VariantConfiguration(String name, Project project) {
        this.name = name.toLowerCase()
        this.project = project
        this.instrument = true
        this.uploadMappingFile = false
        this.mappingFile = null
        this.packageExclusions = []

    }

    String getName() {
        return name
    }

    /**
     * Enable or disable instrumentation of this variant's class files
     * @param state (default: true)
     */
    void setInstrument(boolean state) {
        this.instrument = state
    }

    /**
     * Enable or disable uploads of obfuscation maps (mapping.txt) to New Relic
     * @param state (default: true)
     */
    void setUploadMappingFile(boolean state) {
        this.uploadMappingFile = state
    }

    /**.
     * Allows configuration of the map file path. It should only be used in cases where the file
     * exists out side the standard AGP convention (such as DexGuard or overridden
     * Proguard/R8 configurations)
     *
     * @param mappingFile should be an absolute file path, or path relative to the app project dir.
     * It may be a String or File instance.
     *
     * The plugin will also look for and replace these tokens with variant values:
     *   <name> the variant name
     *   <dirName> The variant directory name component (usually the same as name)
     */
    void setMappingFile(Object mappingFilePath) {
        this.mappingFile = project.file(mappingFilePath)
    }

}
