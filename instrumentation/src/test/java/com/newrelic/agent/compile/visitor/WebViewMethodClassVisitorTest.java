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

public class WebViewMethodClassVisitorTest {

    private TestContext testContext;
    private InstrumentationContext instrumentationContext;

    @Before
    public void setUp() {
        testContext = new TestContext();
        instrumentationContext = testContext.instrumentationContext;
    }

    /**
     * Characterizes the limitation this visitor cannot overcome, and the reason
     * {@code NRWebViewClient} exists: bytecode can only be injected into a method that is
     * actually present in the class. A {@code WebViewClient} subclass written for URL
     * interception — the most common reason to subclass it — has no {@code onPageFinished}
     * override, so there is nothing to inject into and no page-load hook is produced.
     *
     * The existing {@code /MyWebViewClientTest.class} fixture happens to override
     * {@code onPageFinished}, which is why this gap is invisible to the other tests here.
     */
    @Test
    public void clientWithoutOnPageFinishedOverride_getsNoPageFinishedHook() {
        byte[] classBytes = clientOverridingOnlyShouldOverrideUrlLoading();

        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        instrumentationContext.setSuperClassName("android/webkit/WebViewClient");
        ClassVisitor visitor = new WebViewMethodClassVisitor(classWriter, instrumentationContext, InstrumentationAgent.LOGGER);
        new ClassReader(classBytes).accept(visitor, ClassReader.EXPAND_FRAMES);

        final MethodCallCounter counter = new MethodCallCounter(
                "com/newrelic/agent/android/webView/WebViewInstrumentationCallbacks",
                "onPageFinishedCalled"
        );
        new ClassReader(classWriter.toByteArray()).accept(counter, ClassReader.EXPAND_FRAMES);

        Assert.assertEquals(
                "No hook can be injected into a method the class does not declare — this is why the "
                        + "agent installs its own delegating WebViewClient instead of relying on the app's",
                0, counter.getCount("onPageFinishedCalled"));
    }

    /**
     * Builds the common real-world shape:
     * {@code class MyClient extends WebViewClient { boolean shouldOverrideUrlLoading(WebView, String) } }
     * — subclassed for URL interception, with no {@code onPageFinished} override.
     */
    private static byte[] clientOverridingOnlyShouldOverrideUrlLoading() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "com/example/MyClient", null,
                "android/webkit/WebViewClient", null);

        MethodVisitor ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        ctor.visitCode();
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "android/webkit/WebViewClient", "<init>", "()V", false);
        ctor.visitInsn(Opcodes.RETURN);
        ctor.visitMaxs(0, 0);
        ctor.visitEnd();

        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "shouldOverrideUrlLoading",
                "(Landroid/webkit/WebView;Ljava/lang/String;)Z", null, null);
        mv.visitCode();
        mv.visitInsn(Opcodes.ICONST_0);
        mv.visitInsn(Opcodes.IRETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }

    @Test
    public void testWebViewSubclassInstrumentation() throws IOException {
        byte[] classBytes = testContext.classBytesFromResource("/MyWebViewTest.class");

        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        // We must tell the visitor that this class is a subclass of WebView
        instrumentationContext.setSuperClassName("android/webkit/WebView");
        ClassVisitor visitor = new WebViewMethodClassVisitor(classWriter, instrumentationContext, InstrumentationAgent.LOGGER);
        ClassReader classReader = new ClassReader(classBytes);

        classReader.accept(visitor, ClassReader.EXPAND_FRAMES);

        Assert.assertTrue("Class should be modified", instrumentationContext.isClassModified());

        byte[] modifiedBytes = classWriter.toByteArray();
        final MethodCallCounter counter = new MethodCallCounter(
                "com/newrelic/agent/android/webView/WebViewInstrumentationCallbacks",
                "loadUrlCalled",
                "postUrlCalled"
        );

        new ClassReader(modifiedBytes).accept(counter, 0);

        Assert.assertEquals("Should find 2 calls to loadUrlCalled", 2, counter.getCount("loadUrlCalled"));
        Assert.assertEquals("Should find 1 call to postUrlCalled", 1, counter.getCount("postUrlCalled"));
    }

    @Test
    public void testWebViewClientSubclassInstrumentation() throws IOException {
        byte[] classBytes = testContext.classBytesFromResource("/MyWebViewClientTest.class");

        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        // We must tell the visitor that this class is a subclass of WebViewClient
        instrumentationContext.setSuperClassName("android/webkit/WebViewClient");
        ClassVisitor visitor = new WebViewMethodClassVisitor(classWriter, instrumentationContext, InstrumentationAgent.LOGGER);
        ClassReader classReader = new ClassReader(classBytes);

        classReader.accept(visitor, ClassReader.EXPAND_FRAMES);

        Assert.assertTrue("Class should be modified", instrumentationContext.isClassModified());

        byte[] modifiedBytes = classWriter.toByteArray();
        final MethodCallCounter counter = new MethodCallCounter(
                "com/newrelic/agent/android/webView/WebViewInstrumentationCallbacks",
                "onPageFinishedCalled"
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
