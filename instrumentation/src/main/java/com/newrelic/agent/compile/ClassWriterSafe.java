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
        } catch (Throwable e) {
            // Catch Throwable (not just Exception) because ASM's hierarchy walk
            // calls Class.forName, which can throw LinkageError subtypes (e.g.
            // IllegalAccessError under Gradle 9's stricter classloader isolation
            // when an inner class and its enclosing type are loaded by different
            // InstrumentingVisitableURLClassLoader instances). Falling back to
            // java/lang/Object is a safe upper bound for frame computation.
            return "java/lang/Object";
        }
    }
}
