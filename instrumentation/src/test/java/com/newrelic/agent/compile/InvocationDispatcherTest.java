/*
 * Copyright (c) 2021-2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.compile;

import com.newrelic.agent.Constants;
import com.newrelic.agent.TestContext;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.objectweb.asm.ClassWriter;

import java.lang.reflect.InvocationHandler;
import java.util.Map;

public class InvocationDispatcherTest {

    TestContext testContext;
    InvocationDispatcher invocationDispatcher;
    byte classBytes[];

    @Before
    public void setUp() throws Exception {
        invocationDispatcher = new InvocationDispatcher(Log.LOGGER);
        testContext = new TestContext(invocationDispatcher.getInstrumentationContext());
        classBytes = testContext.classBytesFromResource("/MainActivity.class");
    }

    @Test
    public void isKotlinSDKPackage() {
        Assert.assertTrue(invocationDispatcher.isKotlinSDKPackage("kotlin/kotlin"));
        Assert.assertTrue(invocationDispatcher.isKotlinSDKPackage("kotlinx/kotlin"));
        Assert.assertFalse(invocationDispatcher.isKotlinSDKPackage("katherine/kotlin"));
        Assert.assertFalse(invocationDispatcher.isKotlinSDKPackage("kotlinxx/kotlin"));
    }

    @Test
    public void isAndroidSDKPackage() {
        Assert.assertTrue(invocationDispatcher.isAndroidSDKPackage("androidx/material"));
        Assert.assertTrue(invocationDispatcher.isAndroidSDKPackage("kotlin/kotlin"));
        Assert.assertFalse(invocationDispatcher.isAndroidSDKPackage("kotlin1/kotlin"));
    }

    @Test
    public void visitClassBytes() {
        ClassData classData = invocationDispatcher.visitClassBytes(classBytes);
        Assert.assertNotNull(classData);
        Assert.assertTrue(classData.isModified());
        Assert.assertTrue(classBytes.length < classData.getClassBytes().length);
    }

    @Test
    public void visitClassBytesWithOptions() {
        invocationDispatcher.visitClassBytesWithOptions(classBytes, 0xffff);
        Assert.assertEquals(0xffff, invocationDispatcher.getInstrumentationContext().getComputeFlags());

        invocationDispatcher.visitClassBytesWithOptions(classBytes, ClassWriter.COMPUTE_MAXS);
        Assert.assertEquals(ClassWriter.COMPUTE_MAXS, invocationDispatcher.getInstrumentationContext().getComputeFlags());
    }

    @Test
    public void getInstrumentationContext() {
        Assert.assertNotNull(invocationDispatcher.getInstrumentationContext());
        Assert.assertEquals(testContext.instrumentationContext, invocationDispatcher.getInstrumentationContext());
    }

    @Test
    public void testInvocationHandlers() {
        Map<String, InvocationHandler> handlers = invocationDispatcher.invocationHandlers;
    }

    @Test
    public void testInstrumentationDisabled() {
        Assert.assertFalse(invocationDispatcher.isInstrumentationDisabled());
        System.clearProperty(Constants.NR_DISABLE_INSTRUMENTATION_KEY);
        System.setProperty(Constants.NR_DISABLE_INSTRUMENTATION_KEY, "true");
        Assert.assertTrue(invocationDispatcher.isInstrumentationDisabled());
        System.clearProperty(Constants.NR_DISABLE_INSTRUMENTATION_KEY);
        Assert.assertFalse(invocationDispatcher.isInstrumentationDisabled());
    }

    @Test
    public void hasInstrumentedAnnotation() {
        String targets[] = {
                "@Lcom/newrelic/agent/android/instrumentation/Instrumented;()"
        };

        ClassData classData = invocationDispatcher.visitClassBytes(classBytes);
        Assert.assertTrue(invocationDispatcher.getInstrumentationContext().isClassModified());

        testContext.verifyClassTrace(classData.getClassBytes(), testContext.classWriter, targets);
    }

    @Test
    public void testIncludedPackage() {
        Assert.assertTrue(invocationDispatcher.isIncludedPackage("androidx/fragment/app/Fragment/Subfragment"));
        Assert.assertFalse(invocationDispatcher.isIncludedPackage("androidx/fragment"));
        Assert.assertTrue(invocationDispatcher.isIncludedPackage("org/json/"));
        Assert.assertFalse(invocationDispatcher.isIncludedPackage("com/newrelic"));
    }

    @Test
    public void testExcludedPackage() {
        Assert.assertTrue(invocationDispatcher.isExcludedPackage("com/newrelic/mobile"));
        Assert.assertFalse(invocationDispatcher.isExcludedPackage("com/newrelic/agent"));
    }
}