/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.compile.visitor;

import com.google.common.collect.ImmutableMap;
import com.newrelic.agent.compile.InstrumentationContext;
import com.newrelic.agent.compile.Log;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * AsyncTask class was deprecated in API level 30.
 * <p>
 * The AsyncTask class must be loaded on the UI thread.
 * This is done automatically as of android.os.Build.VERSION_CODES#JELLY_BEAN.
 * <p>
 * Notes:
 * The task instance must be created on the UI thread. `execute` must be invoked on the UI thread.
 * Do not call onPreExecute(), onPostExecute, doInBackground, onProgressUpdate manually.
 * The task can be executed only once (an exception will be thrown if a second execution is attempted.)
 */
public class AsyncTaskClassVisitor extends ClassVisitor {
    public static final String TARGET_CLASS = "android/os/AsyncTask";

    private final InstrumentationContext context;
    private final Log log;

    boolean instrument = false;

    /* We'll inject calls to the TraceMachine for these methods
     * Note that we hook on the generic proxies of AsyncTask.
     */
    public static final ImmutableMap<String, String> traceMethodMap = ImmutableMap.of(
            "doInBackground", "([Ljava/lang/Object;)Ljava/lang/Object;"
    );

    public static final ImmutableMap<String, String> endTraceMethodMap = ImmutableMap.of(
            "onPostExecute", "(Ljava/lang/Object;)V"
    );

    public AsyncTaskClassVisitor(ClassVisitor cv, InstrumentationContext context, Log log) {
        super(Opcodes.ASM8, cv);
        this.context = context;
        this.log = log;
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        if (superName != null && superName.equals(TARGET_CLASS)) {
            interfaces = TraceClassDecorator.addInterface(interfaces);
            log.debug("[AsyncTaskClassVisitor] Added Trace interface to class[" + context.getClassName() + "] superName[" + superName + "]");
            super.visit(version, access, name, signature, superName, interfaces);

            instrument = true;
            log.debug("[AsyncTaskClassVisitor] Rewriting [" + context.getClassName() + "]");
            context.markModified();
        } else {
            super.visit(version, access, name, signature, superName, interfaces);
        }
    }

    @Override
    public void visitEnd() {
        if (instrument) {
            TraceClassDecorator decorator = new TraceClassDecorator(this);

            decorator.addTraceField();
            decorator.addTraceInterface(Type.getObjectType(context.getClassName()));

            log.info("[AsyncTaskClassVisitor] Added Trace object and interface to [" + context.getClassName() + "]");
        }

        super.visitEnd();
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        MethodVisitor methodVisitor = super.visitMethod(access, name, desc, signature, exceptions);

        if (instrument) {
            TraceMethodVisitor traceMethodVisitor;

            if (traceMethodMap.containsKey(name) && traceMethodMap.get(name).equals(desc)) {
                traceMethodVisitor = new TraceMethodVisitor(methodVisitor, access, name, desc, context);
                traceMethodVisitor.setUnloadContext();
                return traceMethodVisitor;
            }

            if (endTraceMethodMap.containsKey(name) && endTraceMethodMap.get(name).equals(desc)) {
                // We don't unload trace context because it's the main thread,
                // may want to protect against this (last arg)
                traceMethodVisitor = new TraceMethodVisitor(methodVisitor, access, name, desc, context);
                return traceMethodVisitor;
            }
        }

        return methodVisitor;
    }
}
