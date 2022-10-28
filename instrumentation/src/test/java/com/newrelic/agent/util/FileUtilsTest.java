/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.util;

import org.junit.Assert;
import org.junit.Test;

import java.io.File;

public class FileUtilsTest {

    @Test
    public void isSupportJar() {
        Assert.assertTrue(FileUtils.isSupportJar(new File("jdk/jre/lib/rt.jar")));
        Assert.assertFalse(FileUtils.isSupportJar(new File("jre/rt.jar")));
        Assert.assertFalse(FileUtils.isSupportJar(new File("jre-rt.jar")));
        Assert.assertFalse(FileUtils.isSupportJar(new File("gradle.lib")));
    }

    @Test
    public void isArchive() {
        Assert.assertTrue(FileUtils.isArchive(new File("jre.jar")));
        Assert.assertTrue(FileUtils.isArchive(new File("jre.aar")));
        Assert.assertFalse(FileUtils.isArchive(new File("jre.xyz")));
    }

    @Test
    public void isClass() {
        Assert.assertTrue(FileUtils.isClass(new File("jre.class")));
        Assert.assertFalse(FileUtils.isClass(new File("jre.clazz")));
        Assert.assertFalse(FileUtils.isClass(new File("module-info.class")));
        Assert.assertFalse(FileUtils.isClass(new File("module_info.class")));
    }

    @Test
    public void isKotlinModule() {
        Assert.assertTrue(FileUtils.isKotlinModule("kotlin_module.kotlin_module"));
        Assert.assertFalse(FileUtils.isKotlinModule("kotlin_module.class"));
        Assert.assertFalse(FileUtils.isKotlinModule("kotlin_module_class"));
    }

    @Test
    public void kotlinModuleInfoClass() {
        Assert.assertTrue(FileUtils.isKotlinModuleInfoClass("kotlin_module-info.class"));
        Assert.assertTrue(FileUtils.isKotlinModuleInfoClass("kotlin/module_info.class"));
        Assert.assertFalse(FileUtils.isKotlinModuleInfoClass("kotlin_module.kotlin_module"));
    }
}