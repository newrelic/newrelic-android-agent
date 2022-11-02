package com.newrelic.agent.util;

import java.util.Map;

public interface MethodAnnotation {
	String getMethodName();
	String getMethodDesc();
	String getClassName();
	/**
	 * The name of the annotation.
	 * @return
	 */
	String getName();
	Map<String, Object> getAttributes();
}
