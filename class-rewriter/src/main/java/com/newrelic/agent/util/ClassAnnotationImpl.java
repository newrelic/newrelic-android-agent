package com.newrelic.agent.util;

public class ClassAnnotationImpl extends AnnotationImpl implements ClassAnnotation {

	private final String className;

	public ClassAnnotationImpl(String className, String name) {
		super(name);
		this.className = className;
	}

	public String getClassName() {
		return className;
	}

	
}
