/*
 *
 * Copyright 2021 New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 *
 */

package com.newrelic.agent.compile.visitor;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import java.util.ArrayList;
import java.util.Arrays;

public class TraceClassDecorator {
    public static final String TRACE_FIELD_INTERFACE_CLASS = "com/newrelic/agent/android/api/v2/TraceFieldInterface";
    public static final String NR_TRACE_FIELD = "_nr_trace";
    public static final String NR_TRACE_FIELD_TYPE = "Lcom/newrelic/agent/android/tracing/Trace;";

    ClassVisitor adapter;

    public TraceClassDecorator(ClassVisitor adapter) {
        this.adapter = adapter;
    }

    public void addTraceField() {
        adapter.visitField(Opcodes.ACC_PUBLIC, NR_TRACE_FIELD, NR_TRACE_FIELD_TYPE, null, null);
    }

    public static String[] addInterface(String[] interfaces) {
        ArrayList<String> newInterfaces = new ArrayList<String>(Arrays.asList(interfaces));
        newInterfaces.remove(TRACE_FIELD_INTERFACE_CLASS);
        newInterfaces.add(TRACE_FIELD_INTERFACE_CLASS);

        return newInterfaces.toArray(new String[newInterfaces.size()]);
    }

    public void addTraceInterface(final Type ownerType) {
        Method method = new Method("_nr_setTrace", "(Lcom/newrelic/agent/android/tracing/Trace;)V");
        MethodVisitor mv = adapter.visitMethod(Opcodes.ACC_PUBLIC, method.getName(), method.getDescriptor(), null, null);

        GeneratorAdapter adapter = new GeneratorAdapter(Opcodes.ASM8, mv, Opcodes.ACC_PUBLIC, method.getName(), method.getDescriptor()) {
            @Override
            public void visitCode() {
                Label tryStart = new Label();
                Label tryEnd = new Label();
                Label tryHandler = new Label();

                super.visitCode();

                // Try catch blocks must be visited before their labels
                visitTryCatchBlock(tryStart, tryEnd, tryHandler, "java/lang/Exception");

                // There are cases where we haven't decorated the class with the trace field so we'll surround this with a try/catch.
                visitLabel(tryStart);
                /* try/catch */ {
                    loadThis();
                    loadArgs();
                    putField(ownerType, NR_TRACE_FIELD, Type.getType(NR_TRACE_FIELD_TYPE));
                    goTo(tryEnd);

                } /* catch( Exception e) */ {
                    visitLabel(tryHandler);
                    pop();      // Pop the exception
                    visitLabel(tryEnd);
                }   /* try/catch */

                visitInsn(Opcodes.RETURN);
            }
        };

        adapter.visitCode();
        adapter.endMethod();
    }
}
