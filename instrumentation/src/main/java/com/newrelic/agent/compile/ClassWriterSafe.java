/*
 * Copyright (c) 2021-2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.compile;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

/**
 * If the ClassLoader cannot find teh requested type in the current hierarchy, return
 * the common ancestor of all classes: java.lang.Object
 */
public class ClassWriterSafe extends ClassWriter {
    public ClassWriterSafe(ClassReader cr, int classWriterFlags) {
        super(cr, classWriterFlags);
    }

    public ClassWriterSafe(int flags) {
        super(flags);
    }

    @Override
    protected String getCommonSuperClass(String type1, String type2) {
        try {
            return super.getCommonSuperClass(type1, type2);
        } catch (Exception e) {
            return "java/lang/Object";
        }
    }
}
