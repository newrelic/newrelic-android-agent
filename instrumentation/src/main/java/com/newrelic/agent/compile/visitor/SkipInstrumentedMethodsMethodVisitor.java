/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.compile.visitor;

import com.newrelic.agent.compile.InstrumentedMethod;
import com.newrelic.agent.compile.SkipException;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public class SkipInstrumentedMethodsMethodVisitor extends MethodVisitor {

    public SkipInstrumentedMethodsMethodVisitor(MethodVisitor mv) {
        super(Opcodes.ASM8, mv);
    }

    @Override
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        if (Type.getDescriptor(InstrumentedMethod.class).equals(desc)) {
            throw new SkipException();
        }
        return super.visitAnnotation(desc, visible);
    }
}