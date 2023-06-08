/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.compile.visitor;

import com.newrelic.agent.compile.InstrumentedMethod;
import com.newrelic.agent.util.BytecodeBuilder;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AdviceAdapter;

public abstract class BaseMethodVisitor extends AdviceAdapter {

    protected final String methodName;
    protected final BytecodeBuilder builder = new BytecodeBuilder(this);

    protected BaseMethodVisitor(MethodVisitor mv, int access, String methodName, String desc) {
        super(Opcodes.ASM9, mv, access, methodName, desc);
        this.methodName = methodName;
    }

    /**
     * Mark the method as instrumented so we don't try to modify it again.
     */
    @Override
    public void visitEnd() {
        super.visitAnnotation(Type.getDescriptor(InstrumentedMethod.class), false);
        super.visitEnd();
    }
}

