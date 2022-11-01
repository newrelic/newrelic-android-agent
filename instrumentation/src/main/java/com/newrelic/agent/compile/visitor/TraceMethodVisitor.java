/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.compile.visitor;

import com.newrelic.agent.Constants;
import com.newrelic.agent.compile.InstrumentationContext;
import com.newrelic.agent.compile.Log;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AdviceAdapter;
import org.objectweb.asm.commons.Method;

import java.util.ArrayList;

public class TraceMethodVisitor extends AdviceAdapter {
    protected final InstrumentationContext context;
    protected final Log log;
    private final String name;
    protected Boolean unloadContext = false;
    protected Boolean startTracing = false;
    private final int access;

    public TraceMethodVisitor(MethodVisitor mv, final int access, final String name, final String desc, final InstrumentationContext context) {
        super(Opcodes.ASM8, mv, access, name, desc);
        this.access = access;
        this.context = context;
        this.log = context.getLog();
        this.name = name;
    }

    public void setUnloadContext() {
        unloadContext = true;
    }

    public void setStartTracing() {
        startTracing = true;
    }

    @Override
    protected void onMethodEnter() {
        Type targetType = Type.getObjectType(Constants.TRACEMACHINE_CLASS_NAME);

        if (startTracing) {
            // This will load the class name onto the stack to pass as an argument to the trace machine
            super.visitLdcInsn(context.getSimpleClassName());

            log.debug("[Tracing] Start tracing [" + context.getSimpleClassName() + "]");
            super.invokeStatic(targetType, new Method("startTracing", "(Ljava/lang/String;)V"));
        }

        // public static void TraceMachine.enterMethod(String name, ArrayList<String> annotationParams)
        final Method traceMachineEnterMethod = new Method(Constants.TRACEMACHINE_ENTER_METHOD_NAME, Constants.TRACEMACHINE_ENTER_METHOD_SIGNATURE);

        // It probably make sense to separate responsibilities at some point and have
        // two entry methods for trace machine: One that handles context jumps and one that
        // handles synchronous method call chains.

        if ((access & Opcodes.ACC_STATIC) != 0) {
            // Static methods won't be decorated with the trace object because they lack
            // object context (aka 'this') so we'll just pass null through.

            log.debug("[Tracing] Static method [" + context.getClassName() + "#" + name + "]");

            super.visitInsn(ACONST_NULL);
            super.visitLdcInsn(context.getSimpleClassName() + "#" + name);
            emitAnnotationParamsList(name);
            super.invokeStatic(targetType, traceMachineEnterMethod);

        } else {
            log.debug("[Tracing] Instrumenting method [" + context.getClassName() + "#" + name + "]");

            /*
             * Injects call to TraceMachine.enterMethod() using the private trace field
             * interface (if it exists), within try/catch to protect client class:
             *
             *   try {
             *     TraceMachine.enterMethod(this._nr_trace, "<class>#<method>", ArrayList<String> annotationParams);
             *   } catch (NoSuchFieldError noSuchFieldError) {
             *     TraceMachine.enterMethod(null, "<class>#<method>", ArrayList<String> annotationParams);
             *   }
             */

            // There is a chance this method exists in a class we haven't decorated so
            // we'll attempt to fetch the field and continue with null if it doesn't exist.
            Label tryStart = new Label();
            Label tryEnd = new Label();
            Label tryHandler = new Label();

            log.debug("[Tracing] [" + name + "] calls enterMethod()");

            // Try catch blocks must be visited before their labels
            super.visitTryCatchBlock(tryStart, tryEnd, tryHandler, "java/lang/NoSuchFieldError");

            /* try */
            super.visitLabel(tryStart);
            {
                // public static void enterMethod(Trace trace, String name, ArrayList<String> annotationParams) {}
                super.loadThis();

                // this field must have been created  by TraceClassDecorator in the [context.className] class's instrumentation
                super.getField(Type.getObjectType(context.getClassName()), Constants.TRACE_FIELD, Type.getType(Constants.TRACE_CLASS_NAME));

                // This will load the method signature onto the stack to pass as an argument to the trace machine
                super.visitLdcInsn(context.getSimpleClassName() + "#" + name);

                // also pass the annotation params in an ArrayList
                emitAnnotationParamsList(name);

                super.invokeStatic(targetType, traceMachineEnterMethod);
                super.goTo(tryEnd);

            } /* catch(NoSuchFieldError e) */
            super.visitLabel(tryHandler);
            {
                super.pop();                    // pop the exception off the stack since we don't need it
                super.visitInsn(ACONST_NULL);   // trace = null

                // This will load the method signature onto the stack to pass as an argument to the trace machine
                super.visitLdcInsn(context.getSimpleClassName() + "#" + name);

                // also pass the annotation params in a ArrayList
                emitAnnotationParamsList(name);

                super.invokeStatic(targetType, traceMachineEnterMethod);
            }   /* end try/catch */
            super.visitLabel(tryEnd);
        }
    }
    /*
     * Push annotation args for call to TraceMachine.enterMethod(String name, ArrayList<String> annotationParams)
     */
    private void emitAnnotationParamsList(String name) {
        ArrayList<String> annotationParameters = context.getTracedMethodParameters(name);

        if (annotationParameters == null || annotationParameters.size() == 0) {
            super.visitInsn(ACONST_NULL);
            return;
        }

        Method constructor = Method.getMethod("void <init> ()");
        Method add = Method.getMethod("boolean add(java.lang.Object)");
        Type arrayListType = Type.getObjectType("java/util/ArrayList");

        super.newInstance(arrayListType);
        super.dup();
        super.invokeConstructor(arrayListType, constructor);

        for (String parameterEntry : annotationParameters) {
            super.dup();
            super.visitLdcInsn(parameterEntry);
            super.invokeVirtual(arrayListType, add);
            super.pop();
        }
    }

    @Override
    protected void onMethodExit(int opcode) {
        Type targetType = Type.getObjectType(Constants.TRACEMACHINE_CLASS_NAME);
        super.invokeStatic(targetType, new Method("exitMethod", "()V"));

        log.debug("[Tracing] [" + name + "] calls exitMethod()");
    }
}
