/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.compile.visitor;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.newrelic.agent.Constants;
import com.newrelic.agent.compile.InstrumentationContext;
import com.newrelic.agent.compile.Log;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import java.util.Map;

/**
 * This class visitor adds calls to agent delegate methods from classes which extend the
 * Android Activity classes.  It either modifies existing Activity class methods (onStart,
 * onStop, etc.) if overridden, or injects them (with a call to the super implementation).
 */
public class ActivityClassVisitor extends ActivityClassAdapter {

    static final Type applicationStateMonitorType = Type.getObjectType(Constants.ASM_CLASS_NAME);

    /**
     * This set should include the names of all Android SDK classes that would be base classes
     * of an instrumented client class
     */
    static final ImmutableSet<String> ACTIVITY_CLASSES = ImmutableSet.of(
            "^(android\\/.*\\/)(.*Activity)",                       // Activity-derived
            "^(android\\/app\\/)(ActivityGroup)",                   // ActivityGroup-derived
            "^(android\\/.*\\/)(.*Activity)([DGH].*)",              // Activity-derived (BaseFragmentActivityDonut, BaseFragmentActivityGingerbread)

            // androidx replacement classes
            "^(androidx\\/.*\\/)(.*Activity)",                      // Activity-derived
            "^(androidx\\/)(ActivityCompat)"                        // ActivityGroup-derived
    );

    // Return the access level for these methods
    public static final ImmutableMap<String, Integer> methodAccessMap = ImmutableMap.of(
            "onStart", Opcodes.ACC_PROTECTED,
            "onStop", Opcodes.ACC_PROTECTED
    );

    // The set of methods we'd like to augment (or implement) with our delegates
    public static final Map<Method, Method> methodDelegateMap = ImmutableMap.of(
            new Method("onStart", "()V"), new Method("activityStarted", "()V"),
            new Method("onStop", "()V"), new Method("activityStopped", "()V")
    );


    public ActivityClassVisitor(ClassVisitor cv, InstrumentationContext context, Log log) {
        super(cv, context, log, ACTIVITY_CLASSES, methodDelegateMap);
        this.access = 0;
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        this.access = access;

        instrument = shouldInstrumentClass(name, superName);

        if (instrument) {
            interfaces = TraceClassDecorator.addInterface(interfaces);
            log.info("[ActivityClassVisitor] Added Trace interface to class[" + context.getClassName() + "] superName[" + superName + "]");

            // The TraceFieldInterface has been added, so now the class needs the trace flag
            // and TraceFieldInterface implementation if abstract. Added during visitEnd()
        }

        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    protected void injectCodeIntoMethod(GeneratorAdapter generatorAdapter, Method method, Method monitorMethod) {
        if (method.getName().equalsIgnoreCase("onStart")) {
            generatorAdapter.invokeStatic(applicationStateMonitorType, new Method("getInstance", applicationStateMonitorType, new Type[0]));
            generatorAdapter.invokeVirtual(applicationStateMonitorType, monitorMethod);

            log.debug("[ActivityClassVisitor] injecting onStart method");

        } else if (method.getName().equalsIgnoreCase("onStop")) {
            generatorAdapter.invokeStatic(applicationStateMonitorType, new Method("getInstance", applicationStateMonitorType, new Type[0]));
            generatorAdapter.invokeVirtual(applicationStateMonitorType, monitorMethod);

            log.debug("[ActivityClassVisitor] injecting onStop method");

        } else if (method.getName().equalsIgnoreCase("onBackPressed")) {
            log.debug("[ActivityClassVisitor] injecting onBackPressed method");
        }
    }

    @Override
    public MethodVisitor visitMethod(int access, String methodName, String desc, String signature, String[] exceptions) {
        if (context.isSkippedMethod(methodName, desc)) {
            log.debug("[ActivityClassVisitor] @SkipTrace applied to method [" + methodName + ", " + desc + "]");

        } else if (instrument) {
            if (traceMethodMap.containsKey(methodName) && traceMethodMap.get(methodName).equals(desc)) {
                log.info("[ActivityClassVisitor] Tracing method [" + methodName + "]");
                MethodVisitor methodVisitor = super.visitMethod(access, methodName, desc, signature, exceptions);
                TraceMethodVisitor traceMethodVisitor = new TraceMethodVisitor(methodVisitor, access, methodName, desc, context);

                if (startTracingOn.contains(methodName)) {
                    log.debug("[ActivityClassVisitor] Start new trace for [" + methodName + "]");
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
            log.debug("[ActivityClassVisitor] Added Trace object to " + context.getClassName());

            // Abstract base classes that derive from Android SDK need a TraceFieldInterface implementation
            if ((access & Opcodes.ACC_ABSTRACT) != 0) {
                log.info("[ActivityClassVisitor] Abstract base class: adding TraceFieldInterface impl to [" + context.getClassName() + "]");
                decorator.addTraceInterface(Type.getObjectType(context.getClassName()));
            }
        }

        this.access = 0;

        super.visitEnd();
    }

    @Override
    protected int provideAccessForMethod(final String methodName) {
        Integer v = methodAccessMap.get(methodName);
        return (v != null) ? v.intValue() : Opcodes.ACC_PROTECTED;
    }
}