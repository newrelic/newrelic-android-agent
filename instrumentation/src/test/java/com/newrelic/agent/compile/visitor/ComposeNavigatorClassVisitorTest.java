/*
 * Copyright (c) 2021-2023. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.compile.visitor;

import static org.mockito.Mockito.times;

import com.newrelic.agent.Constants;
import com.newrelic.agent.InstrumentationAgent;
import com.newrelic.agent.TestContext;
import com.newrelic.agent.compile.visitor.ActivityClassVisitor;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.Map;


public class ComposeNavigatorClassVisitorTest {

    TestContext testContext;
    ActivityClassVisitor cv;
    byte[] classBytes;
    Method method = new Method("onCreate", "(Landroid/os/Bundle;)V");

    @Before
    public void setUp() throws Exception {
        testContext = new TestContext();
        cv = new ActivityClassVisitor(testContext.classWriter, testContext.instrumentationContext, InstrumentationAgent.LOGGER);
        cv = Mockito.spy(cv);
        classBytes = testContext.classBytesFromResource("/ComposeNavigation.class");
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
                (Opcodes.ACC_PUBLIC | Opcodes.ACC_OPEN),
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
        testContext.transformClass(classBytes, cv);
        Mockito.verify(cv, times(1)).visitMethod(
                Opcodes.ACC_PROTECTED,
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
    public void injectCodeIntoMethod() {
        Map<Method, Method> map = ActivityClassVisitor.methodDelegateMap;
        ArrayList<String> targets = new ArrayList<>();
        ClassNode cn = testContext.toClassNode(classBytes);

        for (Method m : ActivityClassVisitor.methodDelegateMap.keySet()) {
            MethodNode mn = testContext.toMethodNode(cn, m.getName(), m.getDescriptor());
            if (mn != null) {
                Method delegate = map.get(m);
                targets.add("INVOKEVIRTUAL " + ActivityClassVisitor.agentDelegateClassType.getClassName().replace('.', '/')
                        + "." + delegate.getName() + " " + delegate.getDescriptor());
            }
        }

        String targetsArray[] = new String[targets.size()];
        testContext.testVisitorInjection(classBytes, cv, targets.toArray(targetsArray));
    }
}