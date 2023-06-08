/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.compile.visitor;

import com.newrelic.agent.compile.InstrumentationContext;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

public class ContextInitializationClassVisitor extends ClassVisitor {
    private final InstrumentationContext context;

    public ContextInitializationClassVisitor(final ClassVisitor cv, final InstrumentationContext context) {
        super(Opcodes.ASM9, cv);
        this.context = context;
    }

    @Override
    public void visit(int version, int access, String name, String sig, String superName, String[] interfaces) {
        context.setClassName(name);
        context.setSuperClassName(superName);

        super.visit(version, access, name, sig, superName, interfaces);
    }
}
