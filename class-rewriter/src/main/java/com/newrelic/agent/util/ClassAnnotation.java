package com.newrelic.agent.util;

import java.util.Map;

public interface ClassAnnotation {
	String getClassName();
	/**
	 * The name of the annotation.
	 * @return
	 */
	String getName();
	Map<String, Object> getAttributes();
}
