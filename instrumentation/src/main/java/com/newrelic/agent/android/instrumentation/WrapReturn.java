package com.newrelic.agent.android.instrumentation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

/**
 * This annotation is used to connect the result of a method to a static agent instrumentation delegate.
 * To wrap static methods, simply specify isStatic = true.
 **
 * This example replaces calls to android.view.LayoutInflater.setFactory(Landroid.view.LayoutInflater.Factory;)V
 * with calls to the following method.
 *
 * <code>
 *  @WrapReturn(className = "java/net/URL", methodName = "openConnection", methodDesc = "()Ljava/net/URLConnection;")
 *  public static URLConnection openConnection(final URLConnection connection) {
 *		// URLConnection connection = java/net/URL.openConnection()
 *	}
 * </code>
 */

@Target(ElementType.METHOD)
public @interface WrapReturn {
	String className();
	String methodName();
	String methodDesc();
}
