/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.compile.visitor;

import com.newrelic.agent.Constants;

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
    ClassVisitor adapter;

    public TraceClassDecorator(ClassVisitor adapter) {
        this.adapter = adapter;
    }

    public void addTraceField() {
        adapter.visitField(Opcodes.ACC_PUBLIC, Constants.TRACE_FIELD, Constants.TRACE_CLASS_NAME, null, null);
    }

    public static String[] addInterface(String[] interfaces) {
        ArrayList<String> newInterfaces = new ArrayList<String>(Arrays.asList(interfaces));
        newInterfaces.remove(Constants.TRACE_FIELD_INTERFACE_CLASS_NAME);
        newInterfaces.add(Constants.TRACE_FIELD_INTERFACE_CLASS_NAME);

        return newInterfaces.toArray(new String[newInterfaces.size()]);
    }

    public void addTraceInterface(final Type ownerType) {
        Method method = new Method(Constants.TRACE_SETTRACE_METHOD_NAME, Constants.TRACE_SIGNATURE);
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
                /* try/catch */
                {
                    loadThis();
                    loadArgs();
                    putField(ownerType, Constants.TRACE_FIELD, Type.getType(Constants.TRACE_CLASS_NAME));
                    goTo(tryEnd);

                } /* catch( Exception e) */
                {
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
