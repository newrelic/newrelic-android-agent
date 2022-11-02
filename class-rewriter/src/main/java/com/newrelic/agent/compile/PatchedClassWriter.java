package com.newrelic.agent.compile;

import org.objectweb.asm.ClassWriter;

/**
 * The default ClassWriter tries to load classes through the wrong classloader.  Patch it
 * to use the correct classloader.
 *
 */
public class PatchedClassWriter extends ClassWriter {

	private final ClassLoader classLoader;

	public PatchedClassWriter(int flags, ClassLoader classLoader) {
		super(flags);
		this.classLoader = classLoader;
	}

	/**
	 * Very annoying.  We have to override this method and reimplement it to use the correct classloader.
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	protected String getCommonSuperClass(final String type1,
			final String type2) {
		Class c, d;
		try {
			c = Class.forName(type1.replace('/', '.'), true, classLoader);
			d = Class.forName(type2.replace('/', '.'), true, classLoader);
		} catch (Exception e) {
			throw new RuntimeException(e.toString());
		}
		if (c.isAssignableFrom(d)) {
			return type1;
		}
		if (d.isAssignableFrom(c)) {
			return type2;
		}
		if (c.isInterface() || d.isInterface()) {
			return "java/lang/Object";
		} else {
			do {
				c = c.getSuperclass();
			} while (!c.isAssignableFrom(d));
			return c.getName().replace('.', '/');
		}
	}
}