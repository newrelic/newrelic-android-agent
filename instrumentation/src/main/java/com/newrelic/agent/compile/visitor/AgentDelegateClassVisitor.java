/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.compile.visitor;

import com.newrelic.agent.Constants;
import com.newrelic.agent.compile.InstrumentationContext;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class AgentDelegateClassVisitor extends ClassVisitor {

    protected String superName;
    protected boolean instrument = false;
    protected int access = 0;
    private Pattern classPackagePattern = Pattern.compile(Constants.ANDROID_PACKAGE_RE);

    // A map of base classes as compiled regex patterns
    protected final InstrumentationContext context;
    protected final Logger log;
    protected final Map<String, Pattern> delegatedClassPatterns;
    protected final Map<Method, AgentDelegateMethodVisitorFactory> methodVisitors;
    protected final Map<String, Integer> methodAccessMap; // Return the access level for these methods

    public AgentDelegateClassVisitor(ClassVisitor cv,
                                     InstrumentationContext context,
                                     Logger log,
                                     Set<String> delegateClasses,
                                     Map<Method, Method> delegateMethods,
                                     Map<String, Integer> delegateMethodAccessMap) {
        super(Opcodes.ASM9, cv);
        this.context = context;
        this.log = log;

        this.delegatedClassPatterns = new HashMap<>() {{
            for (String pattern : delegateClasses) {
                put(pattern, Pattern.compile(pattern));
            }
        }};

        this.methodVisitors = new HashMap<>() {{
            for (Entry<Method, Method> entry : delegateMethods.entrySet()) {
                put(entry.getKey(), new AgentDelegateMethodVisitorFactory(entry.getValue()));
            }
        }};

        this.methodAccessMap = delegateMethodAccessMap;
        this.superName = context.getSuperClassName();
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        super.visit(version, access, name, signature, superName, interfaces);
        this.superName = superName;
        this.access = access;
        this.instrument = isInstrumentable(name, superName);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);

        if (!instrument) {
            return mv;
        }

        Method method = new Method(name, desc);
        AgentDelegateMethodVisitorFactory v = methodVisitors.get(method);
        if (v != null) {
            // remove the method so we don't try to add it during visitEnd()
            methodVisitors.remove(method);
            return v.createMethodVisitor(access, method, mv, false);
        }
        return mv;
    }


    @Override
    public void visitEnd() {
        if (!instrument) {
            super.visitEnd();
            return;
        }

        context.markModified();

        // The unimplemented methods remain.  Add them, and be sure to call the super implementation
        // and return the correct result for type

        for (Entry<Method, AgentDelegateMethodVisitorFactory> entry : methodVisitors.entrySet()) {
            String className = entry.getKey().getName();
            String classDescr = entry.getKey().getDescriptor();
            int access = provideAccessForMethod(className);
            MethodVisitor mv = super.visitMethod(access, className, classDescr, null, null);

            mv = entry.getValue().createMethodVisitor(access, entry.getKey(), mv, true);
            mv.visitCode();

            Type methodReturn = entry.getValue().agentDelegateMethod.getReturnType();

            if (methodReturn == Type.VOID_TYPE) {
                mv.visitInsn(Opcodes.RETURN);
            } else if (methodReturn == Type.BOOLEAN_TYPE) {
                mv.visitInsn(Opcodes.IRETURN);
            } else {
                mv.visitInsn(Opcodes.LRETURN);
            }

            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }
        super.visitEnd();
    }

    // require implementation to inject method calls
    protected abstract void injectIntoMethod(GeneratorAdapter generatorAdapter, Method method, Method monitorMethod);

    protected int provideAccessForMethod(final String methodName) {
        Integer v = methodAccessMap.get(methodName);
        return (v != null) ? v.intValue() : Opcodes.ACC_PROTECTED;
    }

    /**
     * Determine if this class is something we'd like to instrument, which
     * is to say, derived from an Android SDK Activity class, excluding
     * Android SDK classes themselves, and not an abstract definition.
     *
     * @param className
     * @param superName
     * @return Return true if this class should be instrumented
     */
    protected boolean isInstrumentable(String className, String superName) {
        if (!classPackagePattern.matcher(className.toLowerCase()).matches()) {
            for (Pattern baseClassPattern : delegatedClassPatterns.values()) {
                Matcher matcher = baseClassPattern.matcher(superName);
                if (matcher.matches()) {
                    return true;
                }
            }
        }

        return false;
    }

    protected boolean isInstrumentable(final Method method) {
        for (Method methodVisitor : methodVisitors.keySet()) {
            if (methodVisitor.getName().equalsIgnoreCase(method.getName())) {
                return true;
            }
        }

        return false;
    }

    protected class AgentDelegateMethodVisitorFactory {
        /**
         * The method on the agent delegate class that will be invoked
         */
        final Method agentDelegateMethod;

        public AgentDelegateMethodVisitorFactory(Method delegateMethod) {
            this.agentDelegateMethod = delegateMethod;
        }

        public MethodVisitor createMethodVisitor(int access, final Method method, MethodVisitor mv, final boolean callSuper) {
            return new GeneratorAdapter(Opcodes.ASM9, mv, access, method.getName(), method.getDescriptor()) {
                @Override
                public void visitCode() {
                    super.visitCode();
                    if (callSuper) {
                        // call the super implementation
                        loadThis();
                        for (int i = 0; i < method.getArgumentTypes().length; i++) {
                            loadArg(i);
                        }
                        visitMethodInsn(Opcodes.INVOKESPECIAL, superName, method.getName(), method.getDescriptor(), false);
                    }
                    injectIntoMethod(this, method, agentDelegateMethod);
                }
            };
        }
    }
}
