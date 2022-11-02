/*
 *
 * Copyright 2021 New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 *
 */

package com.newrelic.agent.compile.visitor;

import java.text.MessageFormat;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

import com.newrelic.agent.compile.InstrumentationContext;
import com.newrelic.agent.compile.Log;

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
			context.addUniqueTag(Annotations.INSTRUMENTED);
			super.visitAnnotation(Annotations.INSTRUMENTED, false);
			log.info(MessageFormat.format("[AnnotatingClassVisitor] Tagging [{0}] as instrumented", context.getFriendlyClassName()));
		}
		
		super.visitEnd();
	}
}
