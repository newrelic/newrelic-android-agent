/*
 * Copyright (c) 2021-2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.compile.visitor;

import static org.objectweb.asm.Type.VOID_TYPE;

import com.newrelic.agent.Constants;
import com.newrelic.agent.TestContext;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.Arrays;
import java.util.List;

public class TraceClassDecoratorTest {

    TestContext testContext;
    TraceClassDecorator tcd;
    byte[] classBytes;
    Method method = new Method("onStart", "()V");

    @Before
    public void setUp() throws Exception {
        testContext = new TestContext();
        tcd = new TraceClassDecorator(testContext.classWriter);
        testContext.classWriter.visit(Opcodes.ASM9, Opcodes.ACC_PROTECTED, method.getName(), method.getDescriptor(), null, null);
        classBytes = testContext.classBytesFromResource("/MainActivity.class");
    }

    @Test
    public void addTraceField() {
        tcd.addTraceField();

        byte[] emittedBytes = testContext.transformClass(classBytes, tcd.adapter);
        ClassNode cn = testContext.toClassNode(emittedBytes);
        FieldNode fn = testContext.toFieldNode(cn, Constants.TRACE_FIELD);
        Assert.assertNotNull(fn);

        String targets[] = {
                "public Lcom/newrelic/agent/android/tracing/Trace; _nr_trace"
        };
        testContext.verifyClassTrace(emittedBytes, testContext.classWriter, targets);
    }

    @Test
    public void addInterface() {
        String intfs[] = {Constants.INTF_SIGNATURE};
        List<String> list = Arrays.asList(TraceClassDecorator.addInterface(intfs));
        Assert.assertFalse(list.isEmpty());
        Assert.assertEquals(2, list.size());
        Assert.assertTrue(list.contains(Constants.TRACE_FIELD_INTERFACE_CLASS_NAME));

        String duplicateIntfs[] = {Constants.TRACE_FIELD_INTERFACE_CLASS_NAME};
        list = Arrays.asList(TraceClassDecorator.addInterface(duplicateIntfs));
        Assert.assertEquals(1, list.size());
        Assert.assertTrue(list.contains(Constants.TRACE_FIELD_INTERFACE_CLASS_NAME));
    }

    @Test
    public void addTraceInterface() {
        tcd.addTraceInterface(VOID_TYPE);

        byte[] emittedBytes = testContext.transformClass(classBytes, tcd.adapter);
        ClassNode cn = testContext.toClassNode(emittedBytes);
        Method traceMethod = new Method(Constants.TRACE_SETTRACE_METHOD_NAME, Constants.TRACE_SIGNATURE);
        MethodNode mn = testContext.toMethodNode(cn, traceMethod.getName(), traceMethod.getDescriptor());
        Assert.assertNotNull(mn);

        String targets[] = {
                "public _nr_setTrace(Lcom/newrelic/agent/android/tracing/Trace;)V\n    TRYCATCHBLOCK L0 L1 L2 java/lang/Exception"
        };
        testContext.verifyClassTrace(emittedBytes, testContext.classWriter, targets);
    }
}