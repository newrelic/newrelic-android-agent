/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.compile.visitor;

import com.newrelic.agent.compile.InstrumentationContext;
import com.newrelic.agent.compile.Log;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class TraceAnnotationClassVisitor extends ClassVisitor {
    private final InstrumentationContext context;

    public TraceAnnotationClassVisitor(ClassVisitor cv, InstrumentationContext context, Log log) {
        super(Opcodes.ASM8, cv);
        this.context = context;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        MethodVisitor methodVisitor = super.visitMethod(access, name, desc, signature, exceptions);

        if (context.isTracedMethod(name, desc) & !context.isSkippedMethod(name, desc)) {
            context.markModified();
            return new TraceMethodVisitor(methodVisitor, access, name, desc, context);
        }

        return methodVisitor;
    }
}
