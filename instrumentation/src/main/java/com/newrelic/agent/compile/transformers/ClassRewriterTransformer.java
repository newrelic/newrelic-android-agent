/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.compile.transformers;

import com.newrelic.agent.Constants;
import com.newrelic.agent.InstrumentationAgent;
import com.newrelic.agent.compile.ClassAdapterBase;
import com.newrelic.agent.compile.ClassVisitorFactory;
import com.newrelic.agent.compile.MethodVisitorFactory;
import com.newrelic.agent.compile.PatchedClassWriter;
import com.newrelic.agent.compile.SkipException;
import com.newrelic.agent.compile.visitor.BaseMethodVisitor;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;
import org.slf4j.Logger;

import java.net.URISyntaxException;
import java.security.ProtectionDomain;
import java.util.HashMap;
import java.util.Map;

/**
 * Instruments the class rewriter 'ClassTransform' class to intercept class bytes as they are being
 * processed through the transform filter.
 */

@Deprecated
public final class ClassRewriterTransformer implements NewRelicClassTransformer {
    private final Logger log;
    private final Map<String, ClassVisitorFactory> classVisitors;

    public ClassRewriterTransformer(final Logger log) throws URISyntaxException {
        try {
            final String agentJarPath = InstrumentationAgent.getAgentJarPath();
        } catch (URISyntaxException e) {
            log.error("Unable to get the path to the New Relic class rewriter jar", e);
            throw e;
        }

        this.log = log;

        classVisitors = new HashMap<String, ClassVisitorFactory>() {{
            put(Constants.CLASS_TRANSFORMER_CLASS_NAME,
                    new ClassVisitorFactory(true) {
                        @Override
                        public ClassVisitor create(ClassVisitor cv) {
                            return createTransformClassAdapter(cv, log);
                        }
                    });
        }};
    }

    public boolean modifies(Class<?> clazz) {
        Type t = Type.getType(clazz);
        return classVisitors.containsKey(t.getInternalName());
    }

    @Override
    public byte[] transform(final ClassLoader classLoader, String className, Class<?> clazz,
                            ProtectionDomain protectionDomain, byte[] bytes) {

        ClassVisitorFactory factory = classVisitors.get(className);
        if (factory != null) {
            if (clazz != null && !factory.isRetransformOkay()) {
                log.error("Cannot instrument " + className);
                return null;
            }

            log.debug("ClassTransformer: classloader[" + classLoader + "] className [" + className + "] clazz[" + clazz + "]");
            log.debug("ClassTransformer protectionDomain[" + protectionDomain + "]");
            log.debug("ClassTransformer: Patching[" + className + "]");

            try {
                ClassReader cr = new ClassReader(bytes);
                ClassWriter cw = new PatchedClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES, classLoader);

                ClassVisitor adapter = factory.create(cw);
                cr.accept(adapter, ClassReader.SKIP_FRAMES);

                log.debug("ClassTransformer: Transformed[" + className + "] Bytes In[" + bytes.length + "] Bytes Out[" + cw.toByteArray().length + "]");

                return cw.toByteArray();

            } catch (SkipException ex) {
                // ignore this.  safety check so this agent doesn't double instrument classes
            } catch (Exception ex) {
                log.error("Error transforming class " + className, ex);
            }

        }

        return null;
    }

    private static ClassVisitor createTransformClassAdapter(ClassVisitor cw, final Logger log) {

        return new ClassAdapterBase(log, cw, new HashMap<Method, MethodVisitorFactory>() {{
            put(new Method(Constants.CLASS_TRANSFORMER_METHOD_NAME, Constants.CLASS_TRANSFORMER_METHOD_SIGNATURE),
                    new MethodVisitorFactory() {
                        @Override
                        public MethodVisitor create(MethodVisitor mv, int access, String name, String desc) {
                            return new BaseMethodVisitor(mv, access, name, desc) {
                                @Override
                                protected void onMethodEnter() {
                                    builder.loadInvocationDispatcher().
                                            loadInvocationDispatcherKey(InstrumentationAgent.getProxyInvocationKey(Constants.CLASS_TRANSFORMER_CLASS_NAME, methodName)).
                                            loadArgumentsArray(methodDesc).
                                            invokeDispatcher(false);

                                    // store the bytes returned in the bytes argument
                                    checkCast(Type.getType(byte[].class));
                                    storeArg(1);
                                }
                            };
                        }
                    });
        }});
    }

}
