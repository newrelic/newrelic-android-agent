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
import org.objectweb.asm.Opcodes;
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
public class WebViewMethodClassVisitor extends AgentDelegateClassVisitor {

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


    public WebViewMethodClassVisitor(ClassVisitor cv, InstrumentationContext context, Logger log) {
        super(cv, context, log, ACTIVITY_CLASSES, methodDelegateMap, methodAccessMap);
        this.access = 0;
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        this.access = access;

        instrument = isInstrumentable(name, superName);
        if (instrument) {
            interfaces = TraceClassDecorator.addInterface(interfaces);
            log.info("[ActivityClassVisitor] Added Trace interface to class[" + context.getClassName() + "] superName[" + superName + "]");

            // The TraceFieldInterface has been added, so now the class needs the trace flag
            // and TraceFieldInterface implementation if abstract. Added during visitEnd()
        }

        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    protected void injectIntoMethod(GeneratorAdapter generatorAdapter, Method method, Method agentDelegateMethod) {
     if (method.getName().equalsIgnoreCase("onClick")) {
         log.debug("[onClick] Injecting call to agent delegate method: " + agentDelegateMethod.getName());
        }
    }

    @Override
    public MethodVisitor visitMethod(int access, String methodName, String desc, String signature, String[] exceptions) {
        MethodVisitor mv = super.visitMethod(access, methodName, desc, signature, exceptions);

        if (methodName.equals("onPageFinished")) {
            return new MethodVisitor(ASM9, mv) {
                @Override
                public void visitCode() {
                    super.visitCode();
                    mv.visitVarInsn(Opcodes.ALOAD, 0); // Load the WebViewClient parameter (assuming it's at index 0)
                    mv.visitVarInsn(Opcodes.ALOAD, 1); // Load the WebView parameter (assuming it's at index 1)
                    mv.visitVarInsn(Opcodes.ALOAD, 2); // Load the String url parameter (assuming it's at index 2)
                    mv.visitMethodInsn(
                            Opcodes.INVOKESTATIC,
                            "com/newrelic/agent/android/webView/WebViewInstrumentationCallbacks",
                            "onPageFinishedCalled",
                            "(Landroid/webkit/WebViewClient;Landroid/webkit/WebView;Ljava/lang/String;)V",
                            false
                    );
                }
            };
        }
        return mv;
    }

    @Override
    public void visitEnd() {
        super.visitEnd();
    }

}