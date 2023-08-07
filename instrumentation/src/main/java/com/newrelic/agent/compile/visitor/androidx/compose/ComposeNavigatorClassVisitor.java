/*
 * Copyright (c) 2022-2023. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.compile.visitor.androidx.compose;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.newrelic.agent.Constants;
import com.newrelic.agent.compile.InstrumentationContext;
import com.newrelic.agent.compile.visitor.DelegateClassAdapter;
import com.newrelic.agent.compile.visitor.TraceClassDecorator;
import com.newrelic.agent.compile.visitor.TraceMethodVisitor;

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
 * This class visitor adds calls to agent delegate methods from classes which extend Jetpack Compose
 * classes.
 */
public class ComposeNavigatorClassVisitor extends DelegateClassAdapter {

    /**
     * import androidx.navigation.NavBackStackEntry;
     * import androidx.navigation.NavController;
     * import androidx.navigation.NavHostController;
     * import androidx.navigation.Navigator;
     */
    static final Type delegateControllerClass = Type.getObjectType(Constants.COMPOSE_NAVIGATION_CLASS_NAME);

    /**
     * This set should include the names of all Compose SDK classes that would be base classes
     * of an instrumented client class:
     * * androidx.navigation.compose.NavHostControllerKt
     */
    static final Set<String> NAVIGATION_CLASSES = ImmutableSet.of(
            // "^(androidx\\/navigation-\\.*\\/)(.*)"      // Navigation-derived
            "^(androidx\\/navigation\\/compose\\/)(ComposeNavigator(.*))"
    );

    static final Method NAVIGATE_METHOD = new Method("navigate", "(Ljava/util/List;Landroidx/navigation/NavOptions;Landroidx/navigation/Navigator$Extras;)V");
    static final Method NAVIGATE_METHOD_DELEGATE = new Method("navigate", "(Landroidx/navigation/compose/ComposeNavigator;Ljava/util/List;Landroidx/navigation/NavOptions;Landroidx/navigation/Navigator$Extras;)V");

    // The set of methods we'd like to augment (or implement) with our delegates
    public static final Map<Method, Method> methodDelegateMap = ImmutableMap.of(
            NAVIGATE_METHOD, NAVIGATE_METHOD_DELEGATE,
            new Method("createDestination", "()Landroidx/navigation/compose/ComposeNavigator$Destination;"), new Method("createDestination", "(Landroidx/navigation/compose/ComposeNavigator;)Landroidx/navigation/compose/ComposeNavigator$Destination;"),
            new Method("popBackStack", "(Landroidx/navigation/NavBackStackEntry;Z)V"), new Method("popBackStack", "(Landroidx/navigation/compose/ComposeNavigator;Landroidx/navigation/NavBackStackEntry;Ljava/lang/Boolean;)V")
    );

    // Return the access level for these methods
    public static final Map<String, Integer> methodAccessMap = ImmutableMap.of(
            NAVIGATE_METHOD.getName(), Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
            "createDestination", Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
            "popBackStack", Opcodes.ACC_STATIC | Opcodes.ACC_FINAL
    );

    // Inject trace interface on entry to these methods
    public static final Map<String, String> tracedMethodsMap = ImmutableMap.of(
            NAVIGATE_METHOD.getName(), NAVIGATE_METHOD.getDescriptor()
    );

    // Start a new trace for these methods
    public static final ImmutableSet<String> startTracingOn = ImmutableSet.of(
            "navigate"
    );


    public ComposeNavigatorClassVisitor(ClassVisitor cv, InstrumentationContext context, Logger log) {
        super(cv, context, log, NAVIGATION_CLASSES, methodDelegateMap, methodAccessMap);
        this.access = 0;
    }

    /**
     * Determine if this class is something we'd like to instrument
     *
     * @param className
     * @param superName
     * @return Return true if this class should be instrumented
     */
    protected boolean isInstrumentable(String className, String superName) {
        return className.startsWith("androidx/navigation/compose/ComposeNavigator");
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        this.access = access;
        instrument = isInstrumentable(name, superName);
        if (instrument) {
            interfaces = TraceClassDecorator.addInterface(interfaces);
            log.info("[ComposeClassVisitor] Added Trace interface to class[" + context.getClassName() + "] superName[" + superName + "]");

            // The TraceFieldInterface has been added, so now the class needs the trace flag
            // and TraceFieldInterface implementation if abstract. Added during visitEnd()
        }

        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    protected void injectCodeIntoMethod(GeneratorAdapter generatorAdapter, Method method, Method monitorMethod) {
        if (isInstrumentable(method)) {
            generatorAdapter.invokeStatic(delegateControllerClass, new Method("getInstance", delegateControllerClass, new Type[0]));
            generatorAdapter.invokeVirtual(delegateControllerClass, monitorMethod);
            log.debug("[ComposeClassVisitor] injecting method [" + method.getName() + "]");
        }
    }

    @Override
    public MethodVisitor visitMethod(int access, String methodName, String desc, String signature, String[] exceptions) {
        if (context.isSkippedMethod(methodName, desc)) {
            log.debug("[ComposeClassVisitor] @SkipTrace applied to method [" + methodName + ", " + desc + "]");

        } else if (instrument) {
            if (tracedMethodsMap.containsKey(methodName) && tracedMethodsMap.get(methodName).equals(desc)) {
                log.info("[ComposeClassVisitor] Tracing method [" + methodName + "]");
                MethodVisitor methodVisitor = super.visitMethod(access, methodName, desc, signature, exceptions);
                TraceMethodVisitor traceMethodVisitor = new TraceMethodVisitor(methodVisitor, access, methodName, desc, context);

                if (startTracingOn.contains(methodName)) {
                    log.debug("[ComposeClassVisitor] Start new trace for [" + methodName + "]");
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
            log.debug("[ComposeClassVisitor] Added Trace object to " + context.getClassName());

            // Abstract base classes that derive from Android SDK need a TraceFieldInterface implementation
            if ((access & Opcodes.ACC_ABSTRACT) != 0) {
                log.info("[ComposeClassVisitor] Abstract base class: adding TraceFieldInterface impl to [" + context.getClassName() + "]");
                decorator.addTraceInterface(Type.getObjectType(context.getClassName()));
            }
        }

        this.access = 0;

        super.visitEnd();
    }
}