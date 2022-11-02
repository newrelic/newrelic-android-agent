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
import com.newrelic.agent.compile.visitor.SafeInstrumentationMethodVisitor;
import com.newrelic.agent.compile.visitor.SkipInstrumentedMethodsMethodVisitor;
import com.newrelic.agent.util.BytecodeBuilder;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import java.io.File;
import java.lang.instrument.IllegalClassFormatException;
import java.net.URISyntaxException;
import java.security.ProtectionDomain;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Instruments the main dex class to intercept class bytes as they are being converted to the dex format.
 *
 */

public final class DexClassTransformer implements NewRelicClassTransformer {
    private Log log;
    private final Map<String, ClassVisitorFactory> classVisitors;

    @SuppressWarnings("serial")
    public DexClassTransformer(final Log log) throws URISyntaxException {
        final String agentJarPath;
        try {
            agentJarPath = RewriterAgent.getAgentJarPath();
        } catch (URISyntaxException e) {
            log.error("Unable to get the path to the New Relic class rewriter jar", e);
            throw e;
        }

        this.log = log;

        classVisitors = new HashMap<String, ClassVisitorFactory>() {{
            put(DEXER_CLASS_NAME,
                    new ClassVisitorFactory(true) {
                        @Override
                        public ClassVisitor create(ClassVisitor cv) {
                            return createDexerMainClassAdapter(cv, log);
                        }
                    });

            put(ANT_DEX_CLASS_NAME,
                    new ClassVisitorFactory(false) {
                        @Override
                        public ClassVisitor create(ClassVisitor cv) {
                            return createAntTaskClassAdapter(cv, log);
                        }
                    });

            put(MAVEN_DEX_CLASS_NAME,
                    new ClassVisitorFactory(true) {
                        @Override
                        public ClassVisitor create(ClassVisitor cv) {
                            return createMavenClassAdapter(cv, log, agentJarPath);
                        }
                    });

            put(PROCESS_BUILDER_CLASS_NAME,
                    new ClassVisitorFactory(true) {
                        @Override
                        public ClassVisitor create(ClassVisitor cv) {
                            return createProcessBuilderClassAdapter(cv, log);
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
                            ProtectionDomain protectionDomain, byte[] bytes)
            throws IllegalClassFormatException {

        ClassVisitorFactory factory = classVisitors.get(className);
        if (factory != null) {
            if (clazz != null && !factory.isRetransformOkay()) {
                log.error("Cannot instrument " + className);
                return null;
            }

			/*
            log.debug("DexTransform: classloader[" + classLoader + "] className [" + className + "] clazz[" + clazz + "]");
            log.debug("DexTransform: protectionDomain[" + protectionDomain + "]");
            log.debug("DexTransform: Patching[" + className + "]");
            /**/

            try {
                ClassReader cr = new ClassReader(bytes);
                ClassWriter cw = new PatchedClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES, classLoader);

                ClassVisitor adapter = factory.create(cw);
                cr.accept(adapter, ClassReader.SKIP_FRAMES);

                log.debug("DexTransform: Transformed[" + className + "] Bytes In[" + bytes.length + "] Bytes Out[" + cw.toByteArray().length + "]");

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
     * We instrument the main dexer class to inject our instrumentation as class bytes are being dexed.
     */
    @SuppressWarnings("serial")
    private static ClassVisitor createDexerMainClassAdapter(ClassVisitor cw, final Log log) {

        return new ClassAdapterBase(log, cw, new HashMap<Method, MethodVisitorFactory>() {{
            put(new Method(NewRelicClassTransformer.DEXER_METHOD_NAME, "(Ljava/lang/String;[B)Z"),
                    new MethodVisitorFactory() {
                        @Override
                        public MethodVisitor create(MethodVisitor mv, int access, String name, String desc) {
                            return new BaseMethodVisitor(mv, access, name, desc) {
                                @Override
                                protected void onMethodEnter() {
                                    // log.debug("Dex onMethodEnter [" + NewRelicClassTransformer.DEXER_METHOD_NAME + "]");
                                    builder.loadInvocationDispatcher().
                                            loadInvocationDispatcherKey(RewriterAgent.getProxyInvocationKey(DEXER_CLASS_NAME, methodName)).
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


    /**
     * Instrument the Ant DexExecTask preDexLibraries method to iterate the libraries and find our
     * agent jar.  If it's found it's stored in an instance field that we create on the DexExecTask.
     * Later when runDx is invoked (it's not invoked by preDexLibraries), call
     * SET_INSTRUMENTATION_DISABLED_FLAG on our InvocationDispatcher invocation handler and pass an array with
     * the instance field.  If it's null the dexer will be disabled while the task runs.
     *
     */
    @SuppressWarnings("serial")
    private static ClassVisitor createAntTaskClassAdapter(ClassVisitor cw, Log log) {
        final String agentFileFieldName = "NewRelicAgentFile";
        final Map<Method, MethodVisitorFactory> methodVisitors = new HashMap<Method, MethodVisitorFactory>() {{

            put(new Method(NewRelicClassTransformer.ANT_DEX_METHOD_NAME, "(Ljava/util/List;)V"),
                    new MethodVisitorFactory() {
                        @Override
                        public MethodVisitor create(MethodVisitor mv, int access, String name, String desc) {
                            return new BaseMethodVisitor(mv, access, name, desc) {

                                @Override
                                protected void onMethodEnter() {
                                    builder.
                                            loadInvocationDispatcher().
                                            loadInvocationDispatcherKey(RewriterAgent.getProxyInvocationKey(ANT_DEX_CLASS_NAME, methodName)).
                                            loadArray(new Runnable() {
                                                @Override
                                                public void run() {
                                                    loadArg(0);
                                                }
                                            }).
                                            invokeDispatcher(false);

                                    // store the return value in the agentFileFieldName field
                                    loadThis();
                                    swap();
                                    putField(Type.getObjectType(ANT_DEX_CLASS_NAME), agentFileFieldName, Type.getType(Object.class));
                                }

                            };
                        }
                    });

            put(new Method("runDx", "(Ljava/util/Collection;Ljava/lang/String;Z)V"),
                    new MethodVisitorFactory() {
                        @Override
                        public MethodVisitor create(MethodVisitor mv, int access, String name, String desc) {
                            return new SafeInstrumentationMethodVisitor(mv, access, name, desc) {

                                @Override
                                protected void onMethodEnter() {
                                    builder.
                                            loadInvocationDispatcher().
                                            loadInvocationDispatcherKey(RewriterAgent.SET_INSTRUMENTATION_DISABLED_FLAG).
                                            loadArray(
                                                    new Runnable() {
                                                        @Override
                                                        public void run() {
                                                            loadThis();
                                                            getField(Type.getObjectType(ANT_DEX_CLASS_NAME), agentFileFieldName, Type.getType(Object.class));
                                                        }

                                                    }).
                                            invokeDispatcher();
                                }
                            };
                        }
                    });

        }};

        return new ClassAdapterBase(log, cw, methodVisitors) {

            /**
             * Create a field to store the agent jar file which is used to toggle our instrumentation on and off.
             */
            @Override
            public void visitEnd() {
                super.visitEnd();
                visitField(Opcodes.ACC_PRIVATE, agentFileFieldName, Type.getType(Object.class).getDescriptor(), null, null);
            }
        };
    }

    /**
     * Injects code into ProcessBuilder.start at the beginning of the method which calls the InvocationDispatcher
     * with the return value of the command() method.  We do this to add the javaagent onto invocations of java and
     * the dx script.
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

    /**
     * Instrument Maven to include our instrumentation agent when it launches a java process for dx.
     * <p/>
     * This is the cruftiest of all of our bytecode modifications.
     *
     */
    @SuppressWarnings("serial")
    private static ClassVisitor createMavenClassAdapter(ClassVisitor cw, final Log log, final String agentJarPath) {

        final Map<Method, MethodVisitorFactory> methodVisitors = new HashMap<Method, MethodVisitorFactory>() {{

            put(new Method("runDex", "(Lcom/jayway/maven/plugins/android/CommandExecutor;Ljava/io/File;Ljava/util/Set;)V"),
                    new MethodVisitorFactory() {
                        @Override
                        public MethodVisitor create(MethodVisitor mv, int access, String name, String desc) {
                            return new GeneratorAdapter(Opcodes.ASM8, mv, access, name, desc) {

                                @Override
                                public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean isInterface) {
                                    if ("executeCommand".equals(name) &&
                                            "(Ljava/lang/String;Ljava/util/List;Ljava/io/File;Z)V".equals(desc)) {

                                        // pop the args down to the list of arguments for the java exec
                                        int arg3 = newLocal(Type.BOOLEAN_TYPE);
                                        storeLocal(arg3);
                                        int arg2 = newLocal(Type.getType(File.class));
                                        storeLocal(arg2);

                                        // dup the args so we can invoke on them
                                        dup();

                                        // add our javaagent argument at index 0 of the commands list
                                        push(0);

                                        String agentCommand = "-javaagent:" + agentJarPath;
                                        if (RewriterAgent.getAgentArgs() != null) {
                                            agentCommand += "=" + RewriterAgent.getAgentArgs();
                                        }

                                        new BytecodeBuilder(this).printToInfoLogFromBytecode("Maven agent jar: " + agentCommand);

                                        visitLdcInsn(agentCommand);
                                        invokeInterface(Type.getType(List.class), new Method("add", "(ILjava/lang/Object;)V"));

                                        // load the args back on the stack
                                        loadLocal(arg2);
                                        loadLocal(arg3);
                                    }
                                    super.visitMethodInsn(opcode, owner, name, desc, isInterface);
                                }

                            };
                        }
                    });

            // we may be able to intercept this to check for the agent jar
            // [newrelic.info] Debug.  Method:getDexInputFiles()Ljava/util/Set;
        }};

        return new ClassAdapterBase(log, cw, methodVisitors);
    }

}
