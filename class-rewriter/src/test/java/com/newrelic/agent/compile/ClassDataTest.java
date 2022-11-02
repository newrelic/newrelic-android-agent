/*
 * Copyright (c) 2021-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.compile;

import com.newrelic.agent.TestContext;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class ClassDataTest {

    TestContext testContext;
    ClassData classData;
    byte[] classBytes;

    @Before
    public void setUp() throws Exception {
        testContext = new TestContext();
        classBytes = testContext.classBytesFromResource("/MainActivity.class");
        classData = new ClassData(classBytes, true);
    }

    @Test
    public void getMainClassBytes() {
        Assert.assertNotNull(classData.getMainClassBytes());
        Assert.assertTrue(classData.getMainClassBytes().length > 0);
    }

    @Test
    public void getShimClassName() {
        Assert.assertNull(classData.getShimClassName());
        classData = new ClassData(classBytes, "shimmy", "shimmy".getBytes(), false);
        Assert.assertEquals("shimmy", classData.getShimClassName());
    }

    @Test
    public void getShimClassBytes() {
        Assert.assertNull(classData.getShimClassBytes());
        classData = new ClassData(classBytes, "shimmy", "shimmy".getBytes(), false);
        Assert.assertNotNull(classData.getShimClassBytes());
    }

    @Test
    public void isShimPresent() {
        Assert.assertFalse(classData.isShimPresent());
        classData = new ClassData(classBytes, "shimmy", "shimmy".getBytes(), false);
        Assert.assertTrue(classData.isShimPresent());
    }

    @Test
    public void isModified() {
        Assert.assertTrue(classData.isModified());
        classData = new ClassData(classBytes, false);
        Assert.assertFalse(classData.isModified());
    }
}