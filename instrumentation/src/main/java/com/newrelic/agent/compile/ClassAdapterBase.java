/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.compile;

import com.newrelic.agent.compile.visitor.SkipInstrumentedMethodsMethodVisitor;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.Method;
import org.slf4j.Logger;

import java.util.Map;

/**
 * A class adapter that uses a map of MethodVisitor factories to create a method visitor
 * for methods of interest.
 */
public class ClassAdapterBase extends ClassVisitor {
    final Map<Method, MethodVisitorFactory> methodVisitors;
    private final Logger log;

    public ClassAdapterBase(Logger log, ClassVisitor cv, Map<Method, MethodVisitorFactory> methodVisitors) {
        super(Opcodes.ASM8, cv);
        this.methodVisitors = methodVisitors;
        this.log = log;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
        MethodVisitorFactory factory = methodVisitors.get(new Method(name, desc));
        if (factory != null) {
            return new SkipInstrumentedMethodsMethodVisitor(
                    factory.create(mv, access, name, desc));
        }

        return mv;
    }
}
