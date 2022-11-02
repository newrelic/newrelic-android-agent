package com.newrelic.agent.compile.transformers;

import org.objectweb.asm.Type;

import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import java.util.HashSet;

public final class NoOpClassTransformer implements NewRelicClassTransformer {
    private static HashSet<String> classVisitors = new HashSet<String>(){{
        add(NewRelicClassTransformer.DEXER_CLASS_NAME);
        add(NewRelicClassTransformer.ANT_DEX_CLASS_NAME);
        add(NewRelicClassTransformer.MAVEN_DEX_CLASS_NAME);
        add(NewRelicClassTransformer.PROCESS_BUILDER_CLASS_NAME);
        add(NewRelicClassTransformer.NR_CLASS_REWRITER_CLASS_NAME);
    }};

    @Override
    public byte[] transform(ClassLoader classLoader, String s, Class<?> aClass, ProtectionDomain protectionDomain, byte[] bytes) throws IllegalClassFormatException {
        return null;
    }

    public boolean modifies(Class<?> clazz) {
        Type t = Type.getType(clazz);
        return classVisitors.contains(t.getInternalName());
    }
}

