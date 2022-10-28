/*
 * Copyright (c) 2021-2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.compile;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class ClassRemapperConfigTest {

    ClassRemapperConfig classRemapper;

    @Before
    public void setUp() throws Exception {
        classRemapper = new ClassRemapperConfig(Log.LOGGER);
    }

    @Test
    public void getMethodWrapper() {
        ClassMethod classMethod = ClassMethod.getClassMethod("java/net/URL.openConnection()Ljava/net/URLConnection;");
        Assert.assertNotNull(classRemapper.getMethodWrapper(classMethod));

        classMethod = ClassMethod.getClassMethod("java/net/okhttp/URL.openConnection()Ljava/net/URLConnection;");
        Assert.assertNull(classRemapper.getMethodWrapper(classMethod));
    }

    @Test
    public void getCallSiteReplacements() {
        Assert.assertNotNull(classRemapper.getCallSiteReplacements("",
                "execute", "([Ljava/lang/Object;)Landroid/os/AsyncTask;"));
    }
}