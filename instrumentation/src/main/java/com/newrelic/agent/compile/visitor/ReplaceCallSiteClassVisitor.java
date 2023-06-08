/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.compile.visitor;

import com.newrelic.agent.Constants;
import com.newrelic.agent.compile.InstrumentationContext;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.slf4j.Logger;

public class ReplaceCallSiteClassVisitor extends ClassVisitor {
    private final InstrumentationContext context;
    private final Logger log;

    public ReplaceCallSiteClassVisitor(ClassVisitor cv, final InstrumentationContext context, final Logger log) {
        super(Opcodes.ASM9, cv);
        this.context = context;
        this.log = log;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] exceptions) {
        return new MethodWrapMethodVisitor(super.visitMethod(access, name, desc, sig, exceptions), access, name, desc);
    }

    private final class MethodWrapMethodVisitor extends GeneratorAdapter {
        private final String name;
        private final String desc;
        private boolean isReplaceClassSite;

        public MethodWrapMethodVisitor(MethodVisitor mv, final int access, final String name, final String desc) {
            super(mv, access, name, desc);
            this.name = name;
            this.desc = desc;
        }

        @Override
        public AnnotationVisitor visitAnnotation(String name, boolean arg1) {
            if (Constants.REPLACE_CALLSITE_CLASS_NAME.equals(name)) {
                isReplaceClassSite = true;
            }
            return super.visitAnnotation(name, arg1);
        }
    }
}
