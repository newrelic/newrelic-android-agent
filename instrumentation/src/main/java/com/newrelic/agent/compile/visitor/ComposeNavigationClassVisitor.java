/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.compile.visitor;

import static org.objectweb.asm.Opcodes.ASM9;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.newrelic.agent.Constants;
import com.newrelic.agent.compile.InstrumentationContext;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;
import org.slf4j.Logger;

import java.util.Map;

/**
 * This class visitor adds calls to agent delegate methods from classes which extend the
 * Android Activity classes.  It either modifies existing Activity class methods (onStart,
 * onStop, etc.) if overridden, or injects them (with a call to the super implementation).
 */
public class ComposeNavigationClassVisitor extends AgentDelegateClassVisitor {

    static final Type agentDelegateClassType = Type.getObjectType(Constants.ASM_CLASS_NAME);

    /**
     * This set should include the names of all Android SDK classes that would be base classes
     * of an instrumented client class
     */
    static final ImmutableSet<String> ACTIVITY_CLASSES = ImmutableSet.of(
            "^java/lang/Object"// ActivityGroup-derived
    );

    // The set of methods we'd like to augment (or implement) from delegateClassType with our delegates

    public static final Map<Method, Method> methodDelegateMap = ImmutableMap.of(

    );

    // Return the access level for these methods
    public static final ImmutableMap<String, Integer> methodAccessMap = ImmutableMap.of(

    );

    // Inject trace interface on entry to these methods

    // Start a new trace on entry to these methods


    public ComposeNavigationClassVisitor(ClassVisitor cv, InstrumentationContext context, Logger log) {
        super(cv, context, log, ACTIVITY_CLASSES, methodDelegateMap, methodAccessMap);
        this.access = 0;
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        context.markModified();
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    protected void injectIntoMethod(GeneratorAdapter generatorAdapter, Method method, Method agentDelegateMethod) {
    }

    @Override
    public MethodVisitor visitMethod(int access, String methodName, String desc, String signature, String[] exceptions) {
        MethodVisitor mv = super.visitMethod(access, methodName, desc, signature, exceptions);

        if (methodName.equals("rememberNavController")) {


            log.info("[ComposNavigationClassVisitor] Instrumenting rememberNavController in class[" + context.getClassName() + "] methodName[" + methodName + "] descriptor[" + desc + "]");
            return new NavigationMethodVisitor(ASM9, mv, access, methodName, desc, log);
        } else {
            return mv;
        }
    }

    @Override


    public void visitEnd() {
        super.visitEnd();
    }

}