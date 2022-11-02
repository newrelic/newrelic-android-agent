package com.newrelic.agent.compile;

import org.objectweb.asm.ClassVisitor;

/**
 * A factory that creates ClassVisitors.
 *
 */
public abstract class ClassVisitorFactory {
	private final boolean retransformOkay;

	public ClassVisitorFactory(boolean retransformOkay) {
		this.retransformOkay = retransformOkay;
	}

	public boolean isRetransformOkay() {
		return retransformOkay;
	}

	public abstract ClassVisitor create(ClassVisitor cv);
}