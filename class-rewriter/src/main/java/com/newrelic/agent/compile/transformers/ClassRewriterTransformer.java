/*
 *
 * Copyright 2021 New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 *
 */

package com.newrelic.agent.compile.transformers;

import com.newrelic.agent.compile.ClassAdapterBase;
import com.newrelic.agent.compile.ClassVisitorFactory;
import com.newrelic.agent.compile.Log;
import com.newrelic.agent.compile.MethodVisitorFactory;
import com.newrelic.agent.compile.PatchedClassWriter;
import com.newrelic.agent.compile.RewriterAgent;
import com.newrelic.agent.compile.SkipException;
import com.newrelic.agent.compile.visitor.BaseMethodVisitor;
import com.newrelic.agent.compile.visitor.SkipInstrumentedMethodsMethodVisitor;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

import java.lang.instrument.IllegalClassFormatException;
import java.net.URISyntaxException;
import java.security.ProtectionDomain;
import java.util.HashMap;
import java.util.Map;

/**
 * Instruments the class rewriter 'ClassTransform' class to intercept class bytes as they are being
 * processed through the transform filter.
 *
 */

public final class ClassRewriterTransformer implements NewRelicClassTransformer {
    private Log log;
    private final Map<String, ClassVisitorFactory> classVisitors;

    @SuppressWarnings("serial")
    public ClassRewriterTransformer(final Log log) throws URISyntaxException {
        try {
            final String agentJarPath = RewriterAgent.getAgentJarPath();
        } catch (URISyntaxException e) {
            log.error("Unable to get the path to the New Relic class rewriter jar", e);
            throw e;
        }

        this.log = log;

        classVisitors = new HashMap<String, ClassVisitorFactory>();

        classVisitors.put(PROCESS_BUILDER_CLASS_NAME,
                new ClassVisitorFactory(true) {
                    @Override
                    public ClassVisitor create(ClassVisitor cv) {
                        return createProcessBuilderClassAdapter(cv, log);
                    }
                });

        classVisitors.put(NR_CLASS_REWRITER_CLASS_NAME,
                new ClassVisitorFactory(true) {
                    @Override
                    public ClassVisitor create(ClassVisitor cv) {
                        return createTransformClassAdapter(cv, log);
                    }
                });
    }

    public boolean modifies(Class<?> clazz) {
        Type t = Type.getType(clazz);
        return classVisitors.containsKey(t.getInternalName());
    }

    @Override
    public byte[] transform(final ClassLoader classLoader, String className, Class<?> clazz,
                            ProtectionDomain protectionDomain, byte[] bytes)
            throws IllegalClassFormatException {

        ClassVisitorFactory factory = classVisitors.get(className);
        if (factory != null) {
            if (clazz != null && !factory.isRetransformOkay()) {
                log.error("Cannot instrument " + className);
                return null;
            }

			/*
            log.debug("ClassTransformer: classloader[" + classLoader + "] className [" + className + "] clazz[" + clazz + "]");
            log.debug("ClassTransformer protectionDomain[" + protectionDomain + "]");
            log.debug("ClassTransformer: Patching[" + className + "]");
            /**/

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

    /**
     * Injects code into ProcessBuilder.start at the beginning of the method which calls the InvocationDispatcher
     * with the return value of the command() method.  We do this to add the javaagent onto invocations of java.
     */
    private static ClassVisitor createProcessBuilderClassAdapter(ClassVisitor cw, final Log log) {

        return new ClassVisitor(Opcodes.ASM8, cw) {

            @Override
            public MethodVisitor visitMethod(int access, final String name,
                                             final String desc, String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);

                if (NewRelicClassTransformer.PROCESS_BUILDER_METHOD_NAME.equals(name)) {
                    /*
                    log.debug("processBuilder visitMethod[" + name + "] desc[" + desc + "] sig[" + signature + "]");
                    log.debug("processBuilder skipInstrMethods[" + name + "]");
                    /**/
                    mv = new SkipInstrumentedMethodsMethodVisitor(
                            new BaseMethodVisitor(mv, access, name, desc) {

                                @Override
                                protected void onMethodEnter() {
                                    builder.loadInvocationDispatcher().
                                            loadInvocationDispatcherKey(RewriterAgent.getProxyInvocationKey(PROCESS_BUILDER_CLASS_NAME, methodName)).
                                            loadArray(
                                                    // create an array of one item, the "command()" method of ProcessBuilder which is a List
                                                    new Runnable() {
                                                        @Override
                                                        public void run() {
                                                            loadThis();
                                                            invokeVirtual(Type.getObjectType(PROCESS_BUILDER_CLASS_NAME), new Method("command", "()Ljava/util/List;"));
                                                        }

                                                    }).
                                            invokeDispatcher();
                                }
                            });

                }

                return mv;
            }

        };
    }

    @SuppressWarnings("serial")
    private static ClassVisitor createTransformClassAdapter(ClassVisitor cw, final Log log) {

        return new ClassAdapterBase(log, cw, new HashMap<Method, MethodVisitorFactory>() {{
            put(new Method(NewRelicClassTransformer.NR_CLASS_REWRITER_METHOD_NAME, NewRelicClassTransformer.NR_CLASS_REWRITER_METHOD_SIG),
                    new MethodVisitorFactory() {
                        @Override
                        public MethodVisitor create(MethodVisitor mv, int access, String name, String desc) {
                            return new BaseMethodVisitor(mv, access, name, desc) {
                                @Override
                                protected void onMethodEnter() {
                                    builder.loadInvocationDispatcher().
                                            loadInvocationDispatcherKey(RewriterAgent.getProxyInvocationKey(NR_CLASS_REWRITER_CLASS_NAME, methodName)).
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
