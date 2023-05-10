/*
 * Copyright (c) 2021-2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.compile.visitor;

import com.newrelic.agent.Constants;
import com.newrelic.agent.InstrumentationAgent;
import com.newrelic.agent.TestContext;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class AsyncTaskClassVisitorTest {

    TestContext testContext;
    AsyncTaskClassVisitor cv;

    @Before
    public void setUp() throws Exception {
        testContext = new TestContext();
        cv = new AsyncTaskClassVisitor(testContext.classWriter,
                testContext.instrumentationContext, InstrumentationAgent.LOGGER);
    }

    @Test
    public void testVisitMethod() {
        cv.instrument = true;

        MethodVisitor mv = cv.visitMethod(Opcodes.ACC_PUBLIC, "onPostExecute", "(Ljava/lang/Object;)V", null, null);
        Assert.assertTrue(mv instanceof TraceMethodVisitor);

        mv = cv.visitMethod(Opcodes.ACC_PUBLIC, "doInBackground", "([Ljava/lang/Object;)Ljava/lang/Object;", null, null);
        Assert.assertTrue(mv instanceof TraceMethodVisitor);
    }

    @Test
    public void testTraceObjectAndInterface() {
        byte[] classBytes = testContext.classBytesFromResource("/visitor/AsyncTask.class");
        String targets[] = {
                "TRYCATCHBLOCK L0 L1 L2 java/lang/NoSuchFieldError",
                "PUTFIELD com/newrelic/agent/android/tracetestharness/fragments/AsyncRunner/LollyGagAsyncTask._nr_trace : Lcom/newrelic/agent/android/tracing/Trace;",
                "GETFIELD com/newrelic/agent/android/tracetestharness/fragments/AsyncRunner/LollyGagAsyncTask._nr_trace : Lcom/newrelic/agent/android/tracing/Trace;",
                "public _nr_setTrace(Lcom/newrelic/agent/android/tracing/Trace;)V"
        };
        testContext.testVisitorInjection(classBytes, cv, targets);

        // re-read compiled code
        ClassReader cr = testContext.transformClassToReader(classBytes, cv);
        String[] intfs = cr.getInterfaces();
        Assert.assertEquals(Constants.TRACE_FIELD_INTERFACE_CLASS_NAME, intfs[0]);
    }

    @Test
    public void testInstrumentFlag() {
        Assert.assertFalse(cv.instrument);
        cv.visit(Opcodes.V1_7, Opcodes.ACC_SUPER | Opcodes.ACC_PUBLIC,
                "com/newrelic/agent/android/tracetestharness/fragments/AsyncRunner/LollyGagAsyncTask",
                "Landroid/os/AsyncTask<Ljava/lang/Long;Ljava/lang/Void;Ljava/lang/String;>;",
                "android/os/AsyncTask", new String[0]);
        Assert.assertTrue(cv.instrument);
    }

    @Test
    public void testDoInBackgroundWrapper() {
        byte[] classBytes = testContext.classBytesFromResource("/visitor/AsyncTask.class");
        String targets[] = {
                "INVOKESTATIC com/newrelic/agent/android/tracing/TraceMachine.enterMethod (Lcom/newrelic/agent/android/tracing/Trace;Ljava/lang/String;Ljava/util/ArrayList;)V",
                "INVOKESTATIC com/newrelic/agent/android/tracing/TraceMachine.exitMethod ()V"
        };
        testContext.testVisitorInjection(classBytes, cv, targets);
    }

    @Test
    public void testOnPostExecute() {
        byte[] classBytes = testContext.classBytesFromResource("/visitor/AsyncTask.class");
        String targets[] = {
                "INVOKESTATIC com/newrelic/agent/android/tracing/TraceMachine.enterMethod (Lcom/newrelic/agent/android/tracing/Trace;Ljava/lang/String;Ljava/util/ArrayList;)V",
                "INVOKESTATIC com/newrelic/agent/android/tracing/TraceMachine.exitMethod ()V"
        };
        testContext.testVisitorInjection(classBytes, cv, targets);
    }
}