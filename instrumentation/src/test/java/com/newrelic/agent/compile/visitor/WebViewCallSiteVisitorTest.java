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
        byte[] classBytes = testContext.classBytesFromResource("/WebViewTest.class");
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        ClassVisitor visitor = new WebViewCallSiteVisitor(classWriter, instrumentationContext, InstrumentationAgent.LOGGER);
        ClassReader classReader = new ClassReader(classBytes);
        classReader.accept(visitor, ClassReader.EXPAND_FRAMES);
        Assert.assertTrue("Class should be modified by the visitor", instrumentationContext.isClassModified());

        byte[] modifiedBytes = classWriter.toByteArray();
        final MethodCallCounter counter = new MethodCallCounter(
                "com/newrelic/agent/android/webView/WebViewInstrumentationCallbacks",
                "postUrlCalled"
        );

        final ClassReader modifiedClassReader = new ClassReader(modifiedBytes);
        modifiedClassReader.accept(counter, ClassReader.EXPAND_FRAMES);

        Assert.assertEquals("Should have instrumented one call to postUrl", 1, counter.getCount("postUrlCalled"));
    }

    @Test
    public void testSetWebViewClientIsWrapped() {
        byte[] classBytes = fixtureCallingSetWebViewClient();
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        ClassVisitor visitor = new WebViewCallSiteVisitor(classWriter, instrumentationContext, InstrumentationAgent.LOGGER);
        new ClassReader(classBytes).accept(visitor, ClassReader.EXPAND_FRAMES);
        Assert.assertTrue("Class should be modified by the visitor", instrumentationContext.isClassModified());

        byte[] modifiedBytes = classWriter.toByteArray();

        final MethodCallCounter callbackCounter = new MethodCallCounter(
                "com/newrelic/agent/android/webView/WebViewInstrumentationCallbacks",
                "setWebViewClientCalled"
        );
        new ClassReader(modifiedBytes).accept(callbackCounter, ClassReader.EXPAND_FRAMES);
        Assert.assertEquals("Should have wrapped one setWebViewClient call",
                1, callbackCounter.getCount("setWebViewClientCalled"));

        final MethodCallCounter originalCounter = new MethodCallCounter(
                "android/webkit/WebView",
                "setWebViewClient"
        );
        new ClassReader(modifiedBytes).accept(originalCounter, ClassReader.EXPAND_FRAMES);
        Assert.assertEquals("The original setWebViewClient call must be preserved",
                1, originalCounter.getCount("setWebViewClient"));
    }

    /**
     * Builds a class equivalent to:
     * {@code static void install(WebView v, WebViewClient c) { v.setWebViewClient(c); } }
     */
    private static byte[] fixtureCallingSetWebViewClient() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "com/example/SetClientFixture",
                null, "java/lang/Object", null);

        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "install",
                "(Landroid/webkit/WebView;Landroid/webkit/WebViewClient;)V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "android/webkit/WebView", "setWebViewClient",
                "(Landroid/webkit/WebViewClient;)V", false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
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