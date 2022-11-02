package com.newrelic.agent.compile;

import org.objectweb.asm.MethodVisitor;

/**
 * A factory that creates MethodVisitors.
 *
 */
public interface MethodVisitorFactory {
	MethodVisitor create(MethodVisitor mv, int access, String name, String desc);
}