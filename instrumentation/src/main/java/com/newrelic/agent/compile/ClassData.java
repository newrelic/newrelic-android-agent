/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.compile;

public class ClassData {
    private final byte[] classBytes;
    private final boolean modified;

    public ClassData(final byte[] classBytes, final boolean modified) {
        this.classBytes = classBytes;
        this.modified = modified;
    }

    public byte[] getClassBytes() {
        return classBytes;
    }

    public boolean isModified() {
        return modified;
    }
}
