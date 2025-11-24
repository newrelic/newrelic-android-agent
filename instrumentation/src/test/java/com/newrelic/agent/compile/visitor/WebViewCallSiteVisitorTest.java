package com.newrelic.agent.compile.visitor;

import com.newrelic.agent.InstrumentationAgent;
import com.newrelic.agent.TestContext;
import com.newrelic.agent.compile.InstrumentationContext;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class WebViewCallSiteVisitorTest {

    private TestContext testContext;
    private InstrumentationContext instrumentationContext;

    @Before
    public void setUp() {
        testContext = new TestContext();
        instrumentationContext = testContext.instrumentationContext;
    }

    @Test
    public void testWebViewInstrumentation() throws IOException {
        byte[] classBytes = testContext.classBytesFromResource("/WebviewTest.class");

        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        ClassVisitor visitor = new WebViewCallSiteVisitor(testContext.classWriter, instrumentationContext, InstrumentationAgent.LOGGER);
        ClassReader classReader = new ClassReader(classBytes);

        classReader.accept(visitor, ClassReader.EXPAND_FRAMES);

        Assert.assertTrue("Class should be modified by the visitor", instrumentationContext.isClassModified());

        byte[] modifiedBytes = classWriter.toByteArray();
        final MethodCallCounter counter = new MethodCallCounter(
                "com/newrelic/agent/android/webView/WebViewInstrumentationCallbacks",
                "loadUrlCalled",
                "postUrlCalled"
        );
    }
    private static class MethodCallCounter extends ClassVisitor {
        private final String owner;
        private final Map<String, Integer> counts = new HashMap<>();

        MethodCallCounter(String owner, String... methodNames) {
            super(Opcodes.ASM9);
            this.owner = owner;
            for (String name : methodNames) {
                counts.put(name, 0);
            }
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            return new MethodVisitor(Opcodes.ASM9, super.visitMethod(access, name, descriptor, signature, exceptions)) {
                @Override
                public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                    if (MethodCallCounter.this.owner.equals(owner) && counts.containsKey(name)) {
                        counts.put(name, counts.get(name) + 1);
                    }
                    super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                }
            };
        }

        int getCount(String methodName) {
            return counts.getOrDefault(methodName, 0);
        }
    }
}