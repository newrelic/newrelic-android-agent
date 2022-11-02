/*
 * Copyright (c) 2021-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.compile;

import com.newrelic.agent.TestContext;
import com.newrelic.agent.util.BuildId;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

public class InstrumentationContextTest {

    private InstrumentationContext instrumentation;

    @Before
    public void setUp() throws Exception {
        instrumentation = new InstrumentationContext(TestContext.config, Log.LOGGER);
        instrumentation.setClassName(getClass().getSimpleName());
        instrumentation.setSuperClassName(getClass().getSuperclass().getName());
    }

    @Test
    public void getLog() {
        Assert.assertEquals(Log.LOGGER, instrumentation.getLog());
        Log newLog = new SystemErrLog(Collections.emptyMap());
        instrumentation = new InstrumentationContext(TestContext.config, newLog);
        Assert.assertEquals(newLog, instrumentation.getLog());
    }

    @Test
    public void reset() {
        instrumentation.markModified();
        instrumentation.setVariantName("qa");
        instrumentation.setComputeFlags(0xdeadbeef);
        instrumentation.addTag("badf00d");

        Assert.assertTrue(instrumentation.isClassModified());
        Assert.assertEquals("qa", instrumentation.getVariantName());
        Assert.assertEquals(0xdeadbeef, instrumentation.getComputeFlags());
        Assert.assertTrue(instrumentation.hasTag("badf00d"));

        instrumentation.reset();
        Assert.assertFalse(instrumentation.isClassModified());
        Assert.assertEquals("qa", instrumentation.getVariantName());
        Assert.assertEquals(0, instrumentation.getComputeFlags());
        Assert.assertFalse(instrumentation.hasTag("badf00d"));
    }

    @Test
    public void markModified() {
        Assert.assertFalse(instrumentation.isClassModified());
        instrumentation.markModified();
        Assert.assertTrue(instrumentation.isClassModified());
    }

    @Test
    public void addTag() {
        String tag = "tagyoureit";
        instrumentation.addTag(tag);
        instrumentation.addTag(tag);
        instrumentation.addTag(tag);
        instrumentation.addTag(tag);
        Assert.assertTrue(instrumentation.hasTag(tag));
        Assert.assertEquals(4, instrumentation.getTags().size());
    }

    @Test
    public void addUniqueTag() {
        String tag = "notagyoureit";
        instrumentation.addUniqueTag(tag);
        instrumentation.addUniqueTag(tag);
        instrumentation.addUniqueTag(tag);
        instrumentation.addUniqueTag(tag);
        Assert.assertTrue(instrumentation.hasTag(tag));
        Assert.assertEquals(1, instrumentation.getTags().size());
    }

    @Test
    public void addTracedMethod() {
        instrumentation.addTracedMethod("method", "desc");
        Assert.assertTrue(instrumentation.isTracedMethod("method", "desc"));
    }

    @Test
    public void isTracedMethod() {
        instrumentation.addTracedMethod("method", "desc");
        Assert.assertFalse(instrumentation.isTracedMethod("method2", "method"));
    }

    @Test
    public void addSkippedMethod() {
        instrumentation.addSkippedMethod("method", "desc");
        Assert.assertTrue(instrumentation.isSkippedMethod("method", "desc"));
    }

    @Test
    public void isSkippedMethod() {
        instrumentation.addSkippedMethod("method", "desc");
        Assert.assertFalse(instrumentation.isSkippedMethod("method2", "desc"));
    }

    @Test
    public void addTracedMethodParameter() {
        instrumentation.addTracedMethodParameter("method", "param1", "Class1", "1");
        ArrayList<String> params = instrumentation.getTracedMethodParameters("method");
        Assert.assertTrue(params.contains("param1") && params.contains("Class1") && params.contains("1"));
    }

    @Test
    public void getTags() {
        String tag = "tagyoureit";
        instrumentation.addTag(tag);
        instrumentation.addTag(tag);
        instrumentation.addTag(tag);
        instrumentation.addTag(tag);
        Assert.assertEquals(4, instrumentation.getTags().size());
    }

    @Test
    public void hasTag() {
        String tag = "tag you're it";
        instrumentation.addTag(tag);
        instrumentation.addTag(tag);
        Assert.assertTrue(instrumentation.hasTag(tag));
    }

    @Test
    public void setClassName() {
        instrumentation.setClassName("newclassname");
        Assert.assertEquals("newclassname", instrumentation.getClassName());
    }

    @Test
    public void getClassName() {
        Assert.assertEquals(getClass().getSimpleName(), instrumentation.getClassName());
    }

    @Test
    public void getFriendlyClassName() {
        Assert.assertEquals(getClass().getSimpleName(), instrumentation.getFriendlyClassName());
    }

    @Test
    public void getFriendlySuperClassName() {
        Assert.assertEquals(getClass().getSuperclass().getName(), instrumentation.getFriendlySuperClassName());
    }

    @Test
    public void getSimpleClassName() {
        Assert.assertEquals(getClass().getSimpleName(), instrumentation.getSimpleClassName());
    }

    @Test
    public void setSuperClassName() {
        instrumentation.setSuperClassName("superclass!");
        Assert.assertEquals("superclass!", instrumentation.getSuperClassName());
    }

    @Test
    public void getSuperClassName() {
        instrumentation.setSuperClassName("superclass!");
        Assert.assertEquals("superclass!", instrumentation.getSuperClassName());
    }

    @Test
    public void newClassData() {
        instrumentation.markModified();
        ClassData classData = instrumentation.newClassData("classData".getBytes());
        Assert.assertTrue(classData.isModified());
        Assert.assertEquals("classData".length(), classData.getMainClassBytes().length);
        Assert.assertFalse(classData.isShimPresent());
        Assert.assertNull(classData.getShimClassBytes());
    }

    @Test
    public void getMethodWrapper() {
        ClassMethod classMethod = ClassMethod.getClassMethod("java/net/URL.openConnection()Ljava/net/URLConnection;");
        classMethod = instrumentation.getMethodWrapper(classMethod);
        Assert.assertEquals("com/newrelic/agent/android/instrumentation/URLConnectionInstrumentation", classMethod.getClassName());

        classMethod = ClassMethod.getClassMethod("java/net/okhttp/URL.openConnection()Ljava/net/URLConnection;");
        classMethod = instrumentation.getMethodWrapper(classMethod);
        Assert.assertNull(classMethod);
    }

    @Test
    public void getCallSiteReplacements() {
        Collection<ClassMethod> r;

        r = instrumentation.getCallSiteReplacements("", "execute", "([Ljava/lang/Object;)Landroid/os/AsyncTask;");
        Assert.assertFalse(r.isEmpty());

        r = instrumentation.getCallSiteReplacements("clazz", "executed", "([Ljava/lang/Object;)Landroid/os/AsyncTask;");
        Assert.assertTrue(r.isEmpty());
    }

    @Test
    public void getVariantName() {
        Assert.assertEquals(BuildId.DEFAULT_VARIANT, instrumentation.getVariantName());
        instrumentation.setVariantName("dev");
        Assert.assertEquals("dev", instrumentation.getVariantName());
    }

    @Test
    public void setVariantName() {
        instrumentation.setVariantName("dev");
        Assert.assertEquals("dev", instrumentation.getVariantName());
    }

    @Test
    public void setComputeFlags() {
        instrumentation.setComputeFlags(0xdeadbeef);
        Assert.assertEquals(0xdeadbeef, instrumentation.getComputeFlags());
    }

    @Test
    public void getComputeFlags() {
        Assert.assertEquals(0, instrumentation.getComputeFlags());
    }
}