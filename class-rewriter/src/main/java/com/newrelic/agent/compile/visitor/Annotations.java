package com.newrelic.agent.compile.visitor;



public final class Annotations {
	public static final String INSTRUMENTED = "Lcom/newrelic/agent/android/instrumentation/Instrumented;";
	
	public static boolean isNewRelicAnnotation(final String descriptor) {
		return descriptor.startsWith("Lcom/newrelic/agent/android/instrumentation/");
	}
	
	private Annotations() {}
}
