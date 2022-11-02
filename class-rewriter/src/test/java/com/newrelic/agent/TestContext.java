/*
 * Copyright (c) 2021-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent;

import com.newrelic.agent.compile.ClassRemapperConfig;
import com.newrelic.agent.compile.ClassWriterSafe;
import com.newrelic.agent.compile.InstrumentationContext;
import com.newrelic.agent.compile.Log;
import com.newrelic.agent.compile.SystemErrLog;
import com.newrelic.agent.compile.visitor.PrefilterClassVisitor;

import org.junit.Assert;
import org.mockito.Mockito;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.util.Printer;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceClassVisitor;
import org.objectweb.asm.util.TraceMethodVisitor;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;

public class TestContext {

    static public ClassRemapperConfig config;

    static {
        new SystemErrLog(new HashMap<String, String>() {{
            put("loglevel", "DEBUG");
        }});

        try {
            config = Mockito.spy(new ClassRemapperConfig(Log.LOGGER));

        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            Assert.fail();
        }
    }

    public final InstrumentationContext instrumentationContext;
    public final ClassWriter classWriter;
    public ClassNode cn = new ClassNode();

    public TestContext() {
        instrumentationContext = Mockito.spy(new InstrumentationContext(config, Log.LOGGER));
        classWriter = new ClassWriterSafe(ClassWriter.COMPUTE_FRAMES);
    }

    public TestContext(InstrumentationContext instrumentationContext) {
        this.instrumentationContext = instrumentationContext;
        classWriter = new ClassWriterSafe(ClassWriter.COMPUTE_FRAMES);
    }

    public byte[] classBytesFromResource(final String resourceName) {
        try {
            ClassReader cr = new ClassReader(TestContext.class.getResource(resourceName).openStream());
            cr.accept(new PrefilterClassVisitor(instrumentationContext, Log.LOGGER),
                    ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            cn = toClassNode(cr.b);
            return cr.b;

        } catch (Exception e) {
            Assert.fail();
        }

        return "".getBytes();
    }

    public void verifyClassTrace(byte classData[], ClassVisitor cv, String targets[]) {
        try {
            String emittedClass = getClassTrace(classData, cv);
            for (String target : targets) {
                Assert.assertTrue(emittedClass.contains(target));
            }
        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }
    }

    public void verifyMethodTrace(byte classData[], MethodNode mn, MethodVisitor mv, String targets[]) {
        try {
            String emittedClass = getMethodTrace(classData, mn, mv);
            for (String target : targets) {
                Assert.assertTrue(emittedClass.contains(target));
            }
        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }
    }

    public String getClassTrace(byte classData[], ClassVisitor cv) {
        StringWriter stringWriter = new StringWriter();
        ClassReader cr = new ClassReader(classData);
        TraceClassVisitor classAdapter = new TraceClassVisitor(cv, new PrintWriter(stringWriter));

        try {
            cr.accept(classAdapter, ClassReader.EXPAND_FRAMES);
        } catch (Exception e) {
            throw new RuntimeException(stringWriter.toString());
        }

        return stringWriter.toString();
    }

    private String getMethodTrace(byte[] classData, MethodNode mn, MethodVisitor mv) {
        StringWriter stringWriter = new StringWriter();
        Printer printer = new Textifier();
        TraceMethodVisitor adapter = new TraceMethodVisitor(mv, printer);

        try {
            mn.accept(adapter);
            printer.print(new PrintWriter(stringWriter));
        } catch (Exception e) {
            throw new RuntimeException(printer.toString());
        }

        return stringWriter.toString();
    }

    public ClassNode testVisitorInjection(final String resourceName, ClassVisitor cv, final String[] targets) {
        return testVisitorInjection(classBytesFromResource(resourceName), cv, targets);
    }

    public ClassNode testVisitorInjection(byte classBytes[], ClassVisitor cv, final String[] targets) {
        ClassReader cr = new ClassReader(classBytes);
        cr.accept(cv, ClassReader.EXPAND_FRAMES);
        Assert.assertTrue(instrumentationContext.isClassModified());

        byte[] emittedBytes = transformClass(classBytes, classWriter);
        Assert.assertTrue(emittedBytes.length > 0);
        Assert.assertNotEquals(classBytes.length, emittedBytes.length);

        // verify the targets are not present in passed class
        String emittedClass = getClassTrace(classBytes, classWriter);
        for (String target : targets) {
            Assert.assertFalse(emittedClass.contains(target));
        }

        // verify the targets are present in emitted class
        verifyClassTrace(emittedBytes, cv, targets);

        return toClassNode(emittedBytes, cv);
    }

    public MethodNode testMethodInjection(byte classBytes[], MethodVisitor mv, String name, String desc, final String[] targets) {
        MethodNode mn = toMethodNode(toClassNode(classBytes), name, desc);
        Assert.assertNotNull(mn);
        mn.accept(mv);

        // verify the targets are not present in passed class
        String emittedClass = getMethodTrace(classBytes, mn, mv);
        for (String target : targets) {
            Assert.assertFalse(emittedClass.contains(target));
        }

        // verify the targets are present in emitted class
        verifyMethodTrace(classBytes, mn, mv, targets);

        return mn;
    }

    public ClassReader transformClassToReader(byte classBytes[], ClassVisitor cv) {
        return new ClassReader(transformClass(classBytes, cv));
    }

    public byte[] transformClass(byte classBytes[], ClassVisitor cv) {
        ClassReader cr = new ClassReader(classBytes);
        cr.accept(cv, ClassReader.EXPAND_FRAMES);
        return classWriter.toByteArray();
    }

    public ClassNode toClassNode(byte classBytes[]) {
        return toClassNode(classBytes, new ClassVisitor(Opcodes.ASM7, cn) {
        });
    }

    public ClassNode toClassNode(byte classBytes[], ClassVisitor cv) {
        ClassReader cr = new ClassReader(classBytes);
        cr.accept(cv, ClassReader.EXPAND_FRAMES);
        cn.accept(cv);

        return cn;
    }

    public MethodNode toMethodNode(ClassNode cn, String name, String desc) {
        for (MethodNode mn : cn.methods) {
            if (mn.name.equals(name) && mn.desc.equals(desc)) {
                return mn;
            }
        }

        return null;
    }

    public FieldNode toFieldNode(ClassNode cn, String name) {
        for (FieldNode fn : cn.fields) {
            if (fn.name.equals(name)) {
                return fn;
            }
        }

        return null;
    }
}