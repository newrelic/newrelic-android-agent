/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android

import javax.inject.Inject

abstract class VariantConfiguration {
    final def name;
    def instrument
    def uploadMappingFile
    def mappingFile

    @Inject
    VariantConfiguration(String name) {
        this.name = name.toLowerCase();
        this.instrument = true
        this.uploadMappingFile = false
        this.mappingFile = null
    }

    String getName() {
        return name;
    }

    void setInstrument(boolean state) {
        this.instrument = state
    }

    void setUploadMappingFile(boolean state) {
        this.uploadMappingFile = state
    }

    void setMappingFile(String mappingFile) {
        this.mappingFile = file(mappingFile)
    }

}
