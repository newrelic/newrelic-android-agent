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
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.GeneratorAdapter;

public class ReplaceCallSiteClassVisitor extends ClassVisitor {
	private final InstrumentationContext context;
	private final Log log;
	
	public ReplaceCallSiteClassVisitor(ClassVisitor cv, final InstrumentationContext context, final Log log) {
		super(Opcodes.ASM8, cv);
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
			if ("Lcom/newrelic/agent/android/instrumentation/ReplaceCallSite;".equals(name)) {
				isReplaceClassSite = true;
			}
			return super.visitAnnotation(name, arg1);
		}
	}
}
