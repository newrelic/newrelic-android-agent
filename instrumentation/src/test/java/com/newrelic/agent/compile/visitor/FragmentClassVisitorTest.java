/*
 * Copyright (c) 2021-2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.compile.visitor;

import static org.mockito.Mockito.times;

import com.newrelic.agent.Constants;
import com.newrelic.agent.TestContext;
import com.newrelic.agent.compile.Log;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.tree.ClassNode;


public class FragmentClassVisitorTest {

    TestContext testContext;
    FragmentClassVisitor cv;
    byte[] classBytes;
    Method method = new Method("onCreate", "(Landroid/os/Bundle;)V");

    @Before
    public void setUp() throws Exception {
        testContext = new TestContext();
        cv = new FragmentClassVisitor(testContext.classWriter, testContext.instrumentationContext, Log.LOGGER);
        cv = Mockito.spy(cv);
        classBytes = testContext.classBytesFromResource("/BaseUIFragment.class");
    }

    @Test
    public void visit() {
        ClassNode cn = testContext.toClassNode(classBytes);
        Assert.assertFalse(cn.interfaces.contains(Constants.TRACE_FIELD_INTERFACE_CLASS_NAME));

        Mockito.reset(cv);
        byte[] emittedBytes = testContext.transformClass(classBytes, cv);

        String intfArry[] = new String[cn.interfaces.size()];
        Mockito.verify(cv, times(1)).visit(
                Opcodes.V11,
                (Opcodes.ACC_ABSTRACT | Opcodes.ACC_SUPER | Opcodes.ACC_PUBLIC),
                testContext.instrumentationContext.getClassName(),
                null,
                testContext.instrumentationContext.getSuperClassName(),
                cn.interfaces.toArray(intfArry));
        Mockito.verify(cv, times(1)).visitEnd();

        cn = testContext.toClassNode(emittedBytes);
        Assert.assertTrue(cn.interfaces.contains(Constants.TRACE_FIELD_INTERFACE_CLASS_NAME));
    }

    @Test
    public void visitMethod() {
        cv.instrument = true;
        MethodVisitor mv = cv.visitMethod(Opcodes.ACC_PUBLIC, method.getName(), method.getDescriptor(), null, new String[0]);
        Assert.assertTrue(mv instanceof TraceMethodVisitor);
        Assert.assertTrue(((TraceMethodVisitor) mv).startTracing);

        cv.instrument = false;
        mv = cv.visitMethod(Opcodes.ACC_PUBLIC, method.getName(), method.getDescriptor(), null, new String[0]);
        Assert.assertTrue(Type.getType(mv.getClass()).getClassName().equals("org.objectweb.asm.MethodWriter"));

        Mockito.reset(cv);
        cv.instrument = true;
        testContext.transformClass(classBytes, cv);
        Mockito.verify(cv, times(1)).visitMethod(
                Opcodes.ACC_PUBLIC,
                method.getName(),
                method.getDescriptor(),
                null,
                null);
        Assert.assertTrue(cv.instrument);
    }

    @Test
    public void visitEnd() {
        cv.instrument = true;
        byte[] emittedBytes = testContext.transformClass(classBytes, cv);
        Mockito.verify(cv, times(1)).visitEnd();
        Assert.assertTrue(cv.context.isClassModified());
        Assert.assertEquals(0, cv.access);
        ClassNode cn = testContext.toClassNode(emittedBytes);
        Assert.assertTrue(cn.interfaces.contains(Constants.TRACE_FIELD_INTERFACE_CLASS_NAME));
    }

    @Test
    public void provideAccessForMethod() {
        Assert.assertEquals(Opcodes.ACC_PROTECTED, cv.provideAccessForMethod("onStart"));
        Assert.assertEquals(Opcodes.ACC_PROTECTED, cv.provideAccessForMethod("onStop"));
        Assert.assertEquals(Opcodes.ACC_PROTECTED, cv.provideAccessForMethod("onCreate"));
    }

    @Test
    public void testSkippedMethods() {
        // cv.context.addSkippedMethod(method.getName(), method.getDescriptor());
        cv.instrument = true;
        byte[] emittedBytes = testContext.transformClass(classBytes, cv);
        Mockito.verify(cv, times(1)).visitMethod(
                Opcodes.ACC_PUBLIC,
                method.getName(),
                method.getDescriptor(),
                null,
                null);
        Assert.assertTrue(cv.instrument);

        String r = testContext.getClassTrace(emittedBytes, testContext.classWriter);
        String targets[] = {
                "TRYCATCHBLOCK L0 L1 L2 java/lang/NoSuchFieldError",
                "INVOKESTATIC com/newrelic/agent/android/tracing/TraceMachine.startTracing (Ljava/lang/String;)V",
                "GETFIELD com/newrelic/android/test/squaretools/BaseUIFragment._nr_trace : Lcom/newrelic/agent/android/tracing/Trace;",
                "INVOKESTATIC com/newrelic/agent/android/tracing/TraceMachine.enterMethod (Lcom/newrelic/agent/android/tracing/Trace;Ljava/lang/String;Ljava/util/ArrayList;)V"
        };

        ClassNode cn = testContext.toClassNode(emittedBytes);
        Assert.assertTrue(cn.interfaces.contains(Constants.TRACE_FIELD_INTERFACE_CLASS_NAME));
    }
}