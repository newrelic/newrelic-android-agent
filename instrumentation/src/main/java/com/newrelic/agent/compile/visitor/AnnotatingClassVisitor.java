/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.compile.visitor;

import com.newrelic.agent.Constants;
import com.newrelic.agent.compile.InstrumentationContext;
import com.newrelic.agent.compile.Log;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

import java.text.MessageFormat;

public class AnnotatingClassVisitor extends ClassVisitor {
    private final InstrumentationContext context;
    private final Log log;

    public AnnotatingClassVisitor(ClassVisitor cv, final InstrumentationContext context, final Log log) {
        super(Opcodes.ASM8, cv);
        this.context = context;
        this.log = log;
    }

    @Override
    public void visitEnd() {
        //
        // This is one of the last things we do in the transformation: if this class has been modified, it should have happened by now.
        //
        if (context.isClassModified()) {
            context.addUniqueTag(Constants.INSTRUMENTED_CLASS_NAME);
            super.visitAnnotation(Constants.INSTRUMENTED_CLASS_NAME, false);
            log.info(MessageFormat.format("[AnnotatingClassVisitor] Tagging [{0}] as instrumented", context.getFriendlyClassName()));
        }

        super.visitEnd();
    }
}
