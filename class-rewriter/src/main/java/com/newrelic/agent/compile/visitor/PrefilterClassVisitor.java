/*
 *
 * Copyright 2021 New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 *
 */

package com.newrelic.agent.compile.visitor;

import com.newrelic.agent.compile.InstrumentationContext;
import com.newrelic.agent.compile.Log;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.text.MessageFormat;

/**
 * Sniffs out New Relic annotations and adds them to the InstrumentationContext.
 *
 */
public class PrefilterClassVisitor extends ClassVisitor {
    private static final String TRACE_ANNOTATION_CLASSPATH = "Lcom/newrelic/agent/android/instrumentation/Trace;";
    private static final String SKIP_TRACE_ANNOTATION_CLASSPATH = "Lcom/newrelic/agent/android/instrumentation/SkipTrace;";
	private final InstrumentationContext context;
	private final Log log;
	
	public PrefilterClassVisitor(final InstrumentationContext context, final Log log) {
        super(Opcodes.ASM8);
		this.context = context;
		this.log = log;
	}

	@Override
	public void visit(int version, int access, String name, String sig, String superName, String[] interfaces) {
		super.visit(version, access, name, sig, superName, interfaces);
		context.setClassName(name);
		context.setSuperClassName(superName);
	}

	@Override
	public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
		if (Annotations.isNewRelicAnnotation(desc)) {
			log.info(MessageFormat.format("[{0}] class has New Relic tag: {1}", context.getClassName(), desc));
			context.addTag(desc);
		}
		return null;
	}

	@Override
	public FieldVisitor visitField(int access, String name, String desc, String sig, Object value) {
		return null;
	}

	@Override
	public void visitInnerClass(String arg0, String arg1, String arg2, int arg3) {
	}

	@Override
	public MethodVisitor visitMethod(final int access, final String name, final String desc, final String signature, final String[] exceptions) {
        MethodVisitor methodVisitor = new MethodVisitor(Opcodes.ASM8) {
            @Override
            public AnnotationVisitor visitAnnotationDefault() {
                return null;
            }

            @Override
            public AnnotationVisitor visitAnnotation(String annotationDesc, boolean visible) {
                if (annotationDesc.equals(TRACE_ANNOTATION_CLASSPATH)) {
                    context.addTracedMethod(name, desc);
                    return new TraceAnnotationVisitor(name, context);
                }

                if (annotationDesc.equals(SKIP_TRACE_ANNOTATION_CLASSPATH)) {
                    context.addSkippedMethod(name, desc);
                    return null;
                }
                return null;
            }

            @Override
            public AnnotationVisitor visitParameterAnnotation(int i, String s, boolean b) {
                return null;
            }

        };
		return methodVisitor;
	}
}
