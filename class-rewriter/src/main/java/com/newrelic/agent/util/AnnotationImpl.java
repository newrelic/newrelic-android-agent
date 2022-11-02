/*
 *
 * Copyright 2021 New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 *
 */

package com.newrelic.agent.util;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Opcodes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class AnnotationImpl extends AnnotationVisitor
{
	private final String name;
	private Map<String, Object> attributes;

	public AnnotationImpl(String name) {
		super(Opcodes.ASM8);
		this.name = name;
	}

	@Override
	public void visitEnum(String name, String desc, String value) {
		if (attributes == null) {
			attributes = new HashMap<String, Object>();
		}
		attributes.put(name, value);
	}

	@Override
	public void visitEnd() {

	}

	@Override
	public AnnotationVisitor visitArray(String name) {
		return new ArrayVisitor(name);
	}

	@Override
	public AnnotationVisitor visitAnnotation(String name, String desc) {
		return null;
	}

	@Override
	public void visit(String name, Object value) {
		if (attributes == null) {
			attributes = new HashMap<String, Object>();
		}
		attributes.put(name, value);
	}

	public String getName() {
		return name;
	}

	public Map<String, Object> getAttributes() {
		return attributes == null ? Collections.<String, Object>emptyMap() : attributes;
	}

	private final class ArrayVisitor extends AnnotationVisitor {
		private final String name;
		private final ArrayList<Object> values = new ArrayList<Object>();

		public ArrayVisitor(final String name) {
			super(Opcodes.ASM8);
			this.name = name;
		}

		@Override
		public void visit(String name, Object value) {
			this.values.add(value);
		}

		@Override
		public AnnotationVisitor visitAnnotation(String arg0, String arg1) {
			return null;
		}

		@Override
		public AnnotationVisitor visitArray(String name) {
			return null;
		}

		@Override
		public void visitEnd() {
			AnnotationImpl.this.visit(name, values.toArray(new String[0]));
		}

		@Override
		public void visitEnum(String arg0, String arg1, String arg2) {
		}
	}
}