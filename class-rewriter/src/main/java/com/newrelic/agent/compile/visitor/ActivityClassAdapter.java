/*
 *
 * Copyright 2021 New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 *
 */

package com.newrelic.agent.compile.visitor;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.newrelic.agent.compile.InstrumentationContext;
import com.newrelic.agent.compile.Log;
import com.newrelic.agent.compile.transformers.NewRelicClassTransformer;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class ActivityClassAdapter extends ClassVisitor {

    // Auto-instrument these methods
    public static final ImmutableMap<String, String> traceMethodMap = ImmutableMap.of(
            "onCreate", "(Landroid/os/Bundle;)V",
            "onCreateView", "(Landroid/view/LayoutInflater;Landroid/view/ViewGroup;Landroid/os/Bundle;)Landroid/view/View;"
    );

    // Start a new trace for these methods
    public static final ImmutableSet<String> startTracingOn = ImmutableSet.of(
            "onCreate"
    );

    // A map of base classes as compiled regex patterns
    protected final Map<String, Pattern> baseClassPatterns;
    private final Map<Method, MethodVisitorFactory> methodVisitors;
    protected final InstrumentationContext context;
    protected final Log log;
    protected String superName;
    protected boolean instrument = false;
    protected int access = 0;
    private Pattern androidPackagePattern = Pattern.compile(NewRelicClassTransformer.ANDROID_PACKAGE_RE);


    public ActivityClassAdapter(ClassVisitor cv, InstrumentationContext context, Log log, Set<String> baseClasses, Map<Method, Method> methodMappings) {
        super(Opcodes.ASM8, cv);
        this.context = context;
        this.log = log;

        methodVisitors = new HashMap<>();
        for (Entry<Method, Method> entry : methodMappings.entrySet()) {
            methodVisitors.put(entry.getKey(), new MethodVisitorFactory(entry.getValue()));
        }

        baseClassPatterns = new HashMap<>();
        for (String pattern : baseClasses) {
            baseClassPatterns.put(pattern, Pattern.compile(pattern));
        }
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        super.visit(version, access, name, signature, superName, interfaces);
        this.superName = superName;
        this.access = access;
        //
        // Don't instrument anything from Android SDK, only classes that derive from them
        //
        this.instrument = shouldInstrumentClass(name, superName);
    }


    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
        if (!instrument) {
            return mv;
        }

        Method method = new Method(name, desc);
        MethodVisitorFactory v = methodVisitors.get(method);
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

        for (Entry<Method, MethodVisitorFactory> entry : methodVisitors.entrySet()) {
            String className = entry.getKey().getName();
            String classDescr = entry.getKey().getDescriptor();
            int access = provideAccessForMethod(className);
            MethodVisitor mv = super.visitMethod(access, className, classDescr, null, null);

            mv = entry.getValue().createMethodVisitor(access, entry.getKey(), mv, true);
            mv.visitCode();

            Type methodReturn = entry.getValue().monitorMethod.getReturnType();

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

    protected abstract void injectCodeIntoMethod(GeneratorAdapter generatorAdapter, Method method, Method monitorMethod);

    protected abstract int provideAccessForMethod(final String methodName);

    /**
     * Determine if this class is something we'd like to instrument, which
     * is to say, derived from an Android SDK Activity class, excluding
     * Android SDK classes themselves, and not an abstract definition.
     *
     * @param className
     * @param superName
     * @return Return true if this class should be instrumented
     */
    protected boolean shouldInstrumentClass(String className, String superName) {
        if (!androidPackagePattern.matcher(className.toLowerCase()).matches()) {
            for (Pattern baseClassPattern : baseClassPatterns.values()) {
                Matcher matcher = baseClassPattern.matcher(superName);
                if (matcher.matches()) {
                    return true;
                }
            }
        }

        return false;
    }

    protected class MethodVisitorFactory {
        /**
         * The method on the targetType that will be invoked.
         */
        final Method monitorMethod;

        public MethodVisitorFactory(Method monitorMethod) {
            super();
            this.monitorMethod = monitorMethod;
        }

        public MethodVisitor createMethodVisitor(int access, final Method method, MethodVisitor mv, final boolean callSuper) {
            return new GeneratorAdapter(Opcodes.ASM8, mv, access, method.getName(), method.getDescriptor()) {
                @Override
                public void visitMaxs(int maxStack, int maxLocals) {
                    super.visitMaxs(Math.max(maxStack, 8), Math.max(maxLocals, 8));
                }

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
                    injectCodeIntoMethod(this, method, monitorMethod);
                }

            };
        }

    }
}
