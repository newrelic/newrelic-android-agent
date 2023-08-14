/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.compile.visitor;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.newrelic.agent.compile.InstrumentationContext;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Set;

/**
 * This class visitor adds calls to agent delegate methods from classes which extend the
 * Android Fragment classes.  It either modifies existing Fragment class methods (onStart,
 * onStop, etc.) if overridden, or injects them (with a call to the super implementation).
 */
public class FragmentClassVisitor extends AgentDelegateClassVisitor {

    /**
     * This set should include the names of all Android SDK classes that would be base classes
     * of an instrumented client class
     */
    static final ImmutableSet<String> FRAGMENT_CLASSES = ImmutableSet.of(
            "^(android\\/.*\\/)(.*Fragment)",                       // Fragment-derived
            "^(android\\/support\\/v%d\\/.*\\/)(.*FragmentCompat)", // FragmentCompat-derived

            // androidx replacement classes
            "^(androidx\\/.*\\/)(Fragment)",                        // AndroidX Fragment-derived
            "^(androidx\\/.*\\/)(FragmentCompat)",                  // AndroidX FragmentCompat-derived
            "^(androidx\\/.*\\/)(.*Fragment)"                       // AndroidX Fragment-derived
    );

    // The set of methods we'd like to augment (or implement) with our delegates
    public static final Map<Method, Method> methodDelegateMap = ImmutableMap.of(
            new Method("onStart", "()V"), new Method("onActivityStarted", "(Ljava/lang/Object;)V"),
            new Method("onStop", "()V"), new Method("onActivityStopped", "(Ljava/lang/Object;)V")
    );

    // Return the access level for these methods
    public static final Map<String, Integer> methodAccessMap = ImmutableMap.of(
            "onStart", Opcodes.ACC_PROTECTED,
            "onStop", Opcodes.ACC_PROTECTED
    );

    // Inject trace interface on entry to these methods
    public static final Map<String, String> tracedMethodMap = ImmutableMap.of(
            "onCreate", "(Landroid/os/Bundle;)V",
            "onCreateView", "(Landroid/view/LayoutInflater;Landroid/view/ViewGroup;Landroid/os/Bundle;)Landroid/view/View;"
    );

    // Start a new trace on entry to these methods
    public static final Set<String> startTracingOn = ImmutableSet.of(
            "onCreate"
    );

    public FragmentClassVisitor(ClassVisitor cv, InstrumentationContext context, Logger log) {
        super(cv, context, log, FRAGMENT_CLASSES, methodDelegateMap, methodAccessMap);
        this.access = 0;
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        this.access = access;

        instrument = isInstrumentable(name, superName);

        if (instrument) {
            interfaces = TraceClassDecorator.addInterface(interfaces);
            log.info("[FragmentClassVisitor] Added Trace interface to class[" + context.getClassName() + "] superName[" + superName + "]");

            // The TraceFieldInterface has been added, so now the class needs the trace flag
            // and TraceFieldInterface implementation if abstract. Added during visitEnd()
        }

        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    protected void injectIntoMethod(GeneratorAdapter generatorAdapter, Method method, Method agentDelegateMethod) {
        if (method.getName().equalsIgnoreCase("onStart") || method.getName().equalsIgnoreCase("onStop")) {
            log.debug("[FragmentClassVisitor] inject [" + method.getName() + "]");
        }
    }

    @Override
    public MethodVisitor visitMethod(int access, String methodName, String desc, String signature, String[] exceptions) {
        if (context.isSkippedMethod(methodName, desc)) {
            log.debug("[FragmentClassVisitor] @SkipTrace applied to method [" + methodName + ", " + desc + "]");

        } else if (instrument) {
            if (tracedMethodMap.containsKey(methodName) && tracedMethodMap.get(methodName).equals(desc)) {
                log.debug("[FragmentClassVisitor] Tracing method [" + methodName + "]");
                MethodVisitor methodVisitor = super.visitMethod(access, methodName, desc, signature, exceptions);
                TraceMethodVisitor traceMethodVisitor = new TraceMethodVisitor(methodVisitor, access, methodName, desc, context);

                if (startTracingOn.contains(methodName)) {
                    log.debug("[FragmentClassVisitor] Start new trace for [" + methodName + "]");
                    traceMethodVisitor.setStartTracing();
                }

                return traceMethodVisitor;
            }
        }

        return super.visitMethod(access, methodName, desc, signature, exceptions);
    }

    @Override
    public void visitEnd() {
        if (instrument) {
            TraceClassDecorator decorator = new TraceClassDecorator(this);
            decorator.addTraceField();
            log.debug("[FragmentClassVisitor] Added Trace object to " + context.getClassName());

            // Abstract base classes that derive from Android SDK need a TraceFieldInterface implementation
            if ((access & Opcodes.ACC_ABSTRACT) != 0) {
                log.info("[FragmentClassVisitor] Abstract base class: adding TraceFieldInterface impl to [" + context.getClassName() + "]");
                decorator.addTraceInterface(Type.getObjectType(context.getClassName()));
            }
        }

        this.access = 0;

        super.visitEnd();
    }

}