/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.compile.visitor;

import static org.objectweb.asm.Opcodes.ASM9;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.newrelic.agent.Constants;
import com.newrelic.agent.compile.InstrumentationContext;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * This class visitor instruments WebView subclasses by injecting tracking code at the START
 * of overridden WebView methods (loadUrl, postUrl, etc.).
 *
 * <h2>Purpose</h2>
 * This visitor works in tandem with {@link WebViewCallSiteVisitor} to provide comprehensive
 * WebView instrumentation:
 * <ul>
 *   <li><b>WebViewCallSiteVisitor</b>: Instruments call sites where webView.loadUrl() is invoked
 *       (tracks direct calls to WebView methods)</li>
 *   <li><b>WebViewMethodClassVisitor</b>: Instruments method implementations when WebView methods
 *       are overridden in subclasses (tracks custom WebView implementations)</li>
 * </ul>
 *
 * <h2>How It Works</h2>
 * <p>This visitor extends {@link AgentDelegateClassVisitor} and overrides {@link #visitMethod}
 * to intercept method declarations in classes that extend android.webkit.WebView. When it detects
 * an overridden method (loadUrl, postUrl, etc.), it injects a call to our tracking callback
 * at the very beginning of the method, before any other code executes.</p>
 *
 * <h2>Instrumented Methods</h2>
 * <ul>
 *   <li>loadUrl(String url) - Line 114</li>
 *   <li>loadUrl(String url, Map&lt;String, String&gt; headers) - Line 135</li>
 *   <li>postUrl(String url, byte[] postData) - Line 156</li>
 * </ul>
 *
 * <h2>Example</h2>
 * Given a custom WebView subclass:
 * <pre>{@code
 * public class MyWebView extends WebView {
 *     @Override
 *     public void loadUrl(String url) {
 *         // Custom logic here
 *         super.loadUrl(url);
 *     }
 * }
 * }</pre>
 *
 * After instrumentation, it becomes:
 * <pre>{@code
 * public class MyWebView extends WebView {
 *     @Override
 *     public void loadUrl(String url) {
 *         WebViewInstrumentationCallbacks.loadUrlCalled(this); // INJECTED
 *         // Custom logic here
 *         super.loadUrl(url);
 *     }
 * }
 * }</pre>
 *
 * <h2>Why Not Use Method Replacement?</h2>
 * <p>An earlier approach using @ReplaceCallSite caused StackOverflowErrors because replacing
 * the method implementation led to infinite recursion when subclasses override the method.
 * This approach avoids that by only injecting tracking code at the start of overridden methods,
 * never replacing the actual implementation.</p>
 *
 * <h2>Class Hierarchy Matching</h2>
 * <p>The visitor uses {@link #WEBVIEW_CLASSES} patterns to determine which classes to instrument.
 * It matches classes that:
 * <ul>
 *   <li>Directly extend android.webkit.WebView</li>
 *   <li>Extend any subclass of android.webkit.WebView</li>
 * </ul>
 * The {@link #isInstrumentable} method (inherited from parent) checks if the superclass matches
 * these patterns before applying instrumentation.</p>
 *
 * @see WebViewCallSiteVisitor
 * @see com.newrelic.agent.android.webView.WebViewInstrumentationCallbacks
 */
public class WebViewMethodClassVisitor extends AgentDelegateClassVisitor {

    static final Type agentDelegateClassType = Type.getObjectType(Constants.ASM_CLASS_NAME);

    /**
     * Regex patterns used to determine which classes should be instrumented.
     * <p>
     * The parent class {@link AgentDelegateClassVisitor#isInstrumentable} uses these patterns
     * to check if a class's superclass matches. This ensures we only instrument classes that
     * are part of the WebView hierarchy.
     * <p>
     * Pattern matching logic:
     * <ul>
     *   <li>"^android/webkit/WebView$" - Matches classes that directly extend WebView
     *       ($ ensures exact match, no further subclasses)</li>
     *   <li>"^android/webkit/WebView" - Matches WebView and all its transitive subclasses
     *       (no $ allows matching subclass hierarchies like WebView/Chrome/CustomWebView)</li>
     * </ul>
     * <p>
     * Note: The previous pattern "^java/lang/Object" has been removed as it caused
     * over-instrumentation of non-WebView classes that happen to have loadUrl() methods,
     * resulting in VerifyError due to type mismatches.
     */
    static final ImmutableSet<String> WEBVIEW_CLASSES = ImmutableSet.of(
            "^android/webkit/WebView$",        // Direct WebView subclass
            "^android/webkit/WebView",         // WebView and its subclasses
            "^android/webkit/WebViewClient$"   // Direct WebViewClient subclass (for onPageFinished)
    );

    /**
     * Map of methods to augment with agent delegate methods.
     * <p>
     * Empty for WebView instrumentation because we handle method interception manually
     * in {@link #visitMethod} rather than using the delegate mechanism. This gives us
     * more control over the bytecode injection.
     */
    public static final Map<Method, Method> methodDelegateMap = ImmutableMap.of(

    );

    /**
     * Access modifiers for methods that would be injected/augmented.
     * <p>
     * Empty because we're not using the automatic method injection mechanism from
     * the parent class. All instrumentation is done manually in {@link #visitMethod}.
     */
    public static final ImmutableMap<String, Integer> methodAccessMap = ImmutableMap.of(

    );

    /**
     * Constructs a new WebViewMethodClassVisitor.
     *
     * @param cv The ClassVisitor to delegate to (chaining pattern)
     * @param context Instrumentation context containing class metadata and configuration
     * @param log Logger for debug/info output during instrumentation
     */
    public WebViewMethodClassVisitor(ClassVisitor cv, InstrumentationContext context, Logger log) {
        super(cv, context, log, WEBVIEW_CLASSES, methodDelegateMap, methodAccessMap);
        this.access = 0;
    }

    /**
     * Internal name of the direct superclass, needed to emit the {@code super.xxx()} call inside a
     * synthesized override. Captured here because {@link #visitEnd()} has no other access to it.
     */
    private String superName;

    /** Internal name of the class being visited, for logging. */
    private String visitedClassName;

    /**
     * {@code name + desc} of every target method the class actually declares.
     *
     * {@link #visitEnd()} synthesizes the ones that are absent. Tracking what was declared is what
     * keeps that from emitting a duplicate method, which would make the class fail to load.
     */
    private final Set<String> declaredTargets = new HashSet<>();

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        this.access = access;
        this.superName = superName;
        this.visitedClassName = name;
        // Don't mark as modified here - only mark when we actually instrument a method
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    protected void injectIntoMethod(GeneratorAdapter generatorAdapter, Method method, Method agentDelegateMethod) {
    }

    /**
     * Visits each method in the class being instrumented.
     * <p>
     * This method checks if the current method is one of the WebView methods we want to track
     * (loadUrl, postUrl, onPageFinished). If so, it returns a custom MethodVisitor that injects
     * tracking code at the START of the method implementation.
     * <p>
     * <b>Important:</b> This only instruments methods that are OVERRIDDEN in subclasses.
     * For direct calls to WebView methods, see {@link WebViewCallSiteVisitor}.
     *
     * @param access Method access flags (public, private, static, etc.)
     * @param methodName Name of the method being visited
     * @param desc Method descriptor (parameter and return types in JVM format)
     * @param signature Generic signature (null if not generic)
     * @param exceptions Exceptions declared by the method
     * @return A MethodVisitor that may inject tracking code, or the original visitor if no instrumentation needed
     */
    @Override
    public MethodVisitor visitMethod(int access, String methodName, String desc, String signature, String[] exceptions) {
        MethodVisitor mv = super.visitMethod(access, methodName, desc, signature, exceptions);

        // Only instrument if this class extends WebView (checked in visit() method)
        if (!instrument) {
            return mv;
        }

        // Remember that the class declares this one, so visitEnd() does not synthesize a duplicate.
        declaredTargets.add(methodName + desc);

        // ========================================================================
        // Instrument WebViewClient.onPageFinished(WebView view, String url)
        // ========================================================================
        // This tracks when a page finishes loading in the WebView.
        // Signature: void onPageFinished(WebView view, String url)
        // Descriptor: (Landroid/webkit/WebView;Ljava/lang/String;)V
        //
        // Note: This expects the method to be in a WebViewClient subclass, not WebView itself.
        // Local variable indices for instance methods:
        //   0 = 'this' (the WebViewClient instance)
        //   1 = first parameter (WebView view)
        //   2 = second parameter (String url)
        //
        // IMPORTANT: Check both name AND descriptor to avoid instrumenting custom methods
        // in WebView subclasses that happen to have the same name but different signatures.
        if (methodName.equals("onPageFinished") && desc.equals("(Landroid/webkit/WebView;Ljava/lang/String;)V")) {
            context.markModified(); // Mark class as modified since we're instrumenting this method
            return new MethodVisitor(ASM9, mv) {
                @Override
                public void visitCode() {
                    super.visitCode();
                    // Load method parameters from local variable slots
                    mv.visitVarInsn(Opcodes.ALOAD, 0); // Load 'this' (WebViewClient instance)
                    mv.visitVarInsn(Opcodes.ALOAD, 1); // Load WebView parameter
                    mv.visitVarInsn(Opcodes.ALOAD, 2); // Load String url parameter

                    // Call our static tracking method
                    // WebViewInstrumentationCallbacks.onPageFinishedCalled(this, view, url)
                    mv.visitMethodInsn(
                            Opcodes.INVOKESTATIC,
                            "com/newrelic/agent/android/webView/WebViewInstrumentationCallbacks",
                            "onPageFinishedCalled",
                            "(Landroid/webkit/WebViewClient;Landroid/webkit/WebView;Ljava/lang/String;)V",
                            false
                    );
                }
            };
        }

        // ========================================================================
        // Instrument WebView.loadUrl(String url)
        // ========================================================================
        // This tracks when a WebView subclass overrides loadUrl(String) and calls it.
        // Signature: void loadUrl(String url)
        // Descriptor: (Ljava/lang/String;)V
        //
        // Injected bytecode:
        //   ALOAD 0                    // Load 'this' (the WebView instance)
        //   INVOKESTATIC loadUrlCalled // Call tracking method
        //   ... original method code ...
        //
        // Local variable indices:
        //   0 = 'this' (the WebView instance)
        //   1 = url parameter (String)
        if (methodName.equals("loadUrl") && desc.equals("(Ljava/lang/String;)V")) {
            context.markModified(); // Mark class as modified since we're instrumenting this method
            return new MethodVisitor(ASM9, mv) {
                @Override
                public void visitCode() {
                    super.visitCode();
                    // Inject tracking at the START of the overridden method
                    mv.visitVarInsn(Opcodes.ALOAD, 0); // Load 'this' (the WebView instance)

                    // Call our static tracking method: WebViewInstrumentationCallbacks.loadUrlCalled(this)
                    mv.visitMethodInsn(
                            Opcodes.INVOKESTATIC,
                            "com/newrelic/agent/android/webView/WebViewInstrumentationCallbacks",
                            "loadUrlCalled",
                            "(Landroid/webkit/WebView;)V",
                            false
                    );
                    // After tracking, the original method code executes normally
                }
            };
        }

        // ========================================================================
        // Instrument WebView.loadUrl(String url, Map<String, String> headers)
        // ========================================================================
        // This tracks when a WebView subclass overrides the two-parameter loadUrl variant.
        // Signature: void loadUrl(String url, Map<String, String> additionalHttpHeaders)
        // Descriptor: (Ljava/lang/String;Ljava/util/Map;)V
        //
        // Local variable indices:
        //   0 = 'this' (the WebView instance)
        //   1 = url parameter (String)
        //   2 = additionalHttpHeaders parameter (Map)
        if (methodName.equals("loadUrl") && desc.equals("(Ljava/lang/String;Ljava/util/Map;)V")) {
            context.markModified(); // Mark class as modified since we're instrumenting this method
            return new MethodVisitor(ASM9, mv) {
                @Override
                public void visitCode() {
                    super.visitCode();
                    // Inject tracking at the START of the overridden method
                    mv.visitVarInsn(Opcodes.ALOAD, 0); // Load 'this' (the WebView instance)

                    // Call our static tracking method: WebViewInstrumentationCallbacks.loadUrlCalled(this)
                    // Note: We only pass the WebView instance, not the URL or headers
                    mv.visitMethodInsn(
                            Opcodes.INVOKESTATIC,
                            "com/newrelic/agent/android/webView/WebViewInstrumentationCallbacks",
                            "loadUrlCalled",
                            "(Landroid/webkit/WebView;)V",
                            false
                    );
                    // After tracking, the original method code executes normally
                }
            };
        }

        // ========================================================================
        // Instrument WebView.postUrl(String url, byte[] postData)
        // ========================================================================
        // This tracks when a WebView subclass overrides postUrl to POST data to a URL.
        // Signature: void postUrl(String url, byte[] postData)
        // Descriptor: (Ljava/lang/String;[B)V
        //
        // Local variable indices:
        //   0 = 'this' (the WebView instance)
        //   1 = url parameter (String)
        //   2 = postData parameter (byte[])
        if (methodName.equals("postUrl") && desc.equals("(Ljava/lang/String;[B)V")) {
            context.markModified(); // Mark class as modified since we're instrumenting this method
            return new MethodVisitor(ASM9, mv) {
                @Override
                public void visitCode() {
                    super.visitCode();
                    // Inject tracking at the START of the overridden method
                    mv.visitVarInsn(Opcodes.ALOAD, 0); // Load 'this' (the WebView instance)

                    // Call our static tracking method: WebViewInstrumentationCallbacks.postUrlCalled(this)
                    mv.visitMethodInsn(
                            Opcodes.INVOKESTATIC,
                            "com/newrelic/agent/android/webView/WebViewInstrumentationCallbacks",
                            "postUrlCalled",
                            "(Landroid/webkit/WebView;)V",
                            false
                    );
                    // After tracking, the original method code executes normally
                }
            };
        }

        // No instrumentation needed for this method, return the original visitor
        return mv;
    }

    /**
     * Synthesizes the WebView entry points this class inherits but does not declare.
     *
     * <h2>Why this is needed</h2>
     * Neither of the two existing mechanisms covers a WebView subclass that simply does not override
     * the method:
     * <ul>
     *   <li>{@link WebViewCallSiteVisitor} keys on the call site's owner being exactly
     *       {@code android/webkit/WebView}. Bytecode records the receiver's <em>declared</em> type, so
     *       {@code SystemWebView wv = ...; wv.loadUrl(url)} emits owner
     *       {@code org/apache/cordova/engine/SystemWebView} and is skipped.</li>
     *   <li>This visitor instruments the method only where the subclass overrides it.</li>
     * </ul>
     * Cordova falls in the gap: {@code SystemWebView extends WebView} directly and declares no
     * {@code loadUrl}, {@code postUrl} or {@code setWebViewClient}. Nothing was instrumented, so
     * {@code prepare()} never ran, the {@code NRWebViewBridge} JS interface was never added, and the
     * injected detection and injection scripts had no bridge object to call — silent, total failure.
     * The same hole applies to any app that reaches {@code loadUrl} through a subclass reference.
     *
     * <h2>Why synthesize an override rather than widen the call-site matcher</h2>
     * Widening the matcher would need the class hierarchy of an <em>arbitrary</em> owner, which is not
     * available here: {@link InstrumentationContext} carries only the class currently being visited,
     * and there is no classpath to resolve against. Matching on method name and descriptor alone
     * would instrument any unrelated {@code loadUrl(String)} — the false positive the call-site
     * visitor's own documentation warns about.
     *
     * <p>A synthesized override needs nothing but this class's own superclass name, which is already
     * in hand and already the basis for {@code isInstrumentable}. It is also strictly more effective:
     * virtual dispatch routes <em>every</em> call site to it regardless of the receiver's declared
     * type, so one generated method covers call sites this visitor never sees.</p>
     *
     * <h2>Bounds</h2>
     * Only direct subclasses are reached, because {@code isInstrumentable} matches the superclass
     * name with {@code Matcher#matches()} — a full match, which makes the unanchored
     * {@code "^android/webkit/WebView"} pattern behave exactly like the anchored one. A class extending
     * {@code SystemWebView} is therefore not instrumented. That bound is load-bearing here rather than
     * merely tolerated: it is what prevents a generated override from calling {@code super} into
     * another generated override and reporting the same navigation twice.
     */
    @Override
    public void visitEnd() {
        if (instrument && superName != null) {
            if (Constants.ANDROID_WEBKIT_WEBVIEW_CLASS.equals(superName)) {
                // void loadUrl(String)
                synthesize("loadUrl", "(Ljava/lang/String;)V", "loadUrlCalled", 1);
                // void loadUrl(String, Map)
                synthesize("loadUrl", "(Ljava/lang/String;Ljava/util/Map;)V", "loadUrlCalled", 2);
                // void postUrl(String, byte[])
                synthesize("postUrl", "(Ljava/lang/String;[B)V", "postUrlCalled", 2);
                // void setWebViewClient(WebViewClient) — wraps rather than pre-notifies, so it has
                // its own emitter below.
                synthesizeSetWebViewClient();
            } else if (Constants.ANDROID_WEBKIT_WEBVIEWCLIENT_CLASS.equals(superName)) {
                synthesizeOnPageFinished();
            }
        }
        super.visitEnd();
    }

    /**
     * Emits {@code public void <name>(args) { Callbacks.<callback>(this); super.<name>(args); } }.
     *
     * @param argCount number of reference arguments to forward to {@code super}
     */
    private void synthesize(String name, String desc, String callback, int argCount) {
        if (declaredTargets.contains(name + desc)) {
            return;             // the class overrides it; visitMethod already instrumented that
        }
        MethodVisitor mv = cv.visitMethod(Opcodes.ACC_PUBLIC, name, desc, null, null);
        if (mv == null) {
            return;
        }
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, WEBVIEW_CALLBACKS, callback,
                "(Landroid/webkit/WebView;)V", false);
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        for (int i = 1; i <= argCount; i++) {
            mv.visitVarInsn(Opcodes.ALOAD, i);
        }
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, superName, name, desc, false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(1 + argCount, 1 + argCount);
        mv.visitEnd();
        onSynthesized(name, desc);
    }

    /**
     * Emits {@code public void setWebViewClient(WebViewClient c) {
     * super.setWebViewClient(Callbacks.setWebViewClientCalled(this, c)); } }.
     *
     * The callback returns the client to install — it wraps the app's client in an
     * {@code NRWebViewClient} — so unlike the others its result is consumed rather than discarded.
     * It returns early when handed a client that is already ours, which is what keeps the agent's own
     * {@code ensureNRClientInstalled} call from recursing back through this override.
     */
    private void synthesizeSetWebViewClient() {
        final String name = "setWebViewClient";
        final String desc = "(Landroid/webkit/WebViewClient;)V";
        if (declaredTargets.contains(name + desc)) {
            return;
        }
        MethodVisitor mv = cv.visitMethod(Opcodes.ACC_PUBLIC, name, desc, null, null);
        if (mv == null) {
            return;
        }
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);      // receiver for the super call
        mv.visitVarInsn(Opcodes.ALOAD, 0);      // arg 1: this
        mv.visitVarInsn(Opcodes.ALOAD, 1);      // arg 2: the app's client
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, WEBVIEW_CALLBACKS, "setWebViewClientCalled",
                "(Landroid/webkit/WebView;Landroid/webkit/WebViewClient;)Landroid/webkit/WebViewClient;",
                false);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, superName, name, desc, false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(3, 2);
        mv.visitEnd();
        onSynthesized(name, desc);
    }

    /**
     * Emits {@code public void onPageFinished(WebView v, String url) {
     * Callbacks.onPageFinishedCalled(this, v, url); super.onPageFinished(v, url); } } for a
     * {@code WebViewClient} subclass that does not override it.
     *
     * Cordova's own client does override it, so this is not what unblocks Cordova — it closes the
     * same gap on the client side, where a subclass that never overrides {@code onPageFinished}
     * would otherwise report no page loads at all.
     */
    private void synthesizeOnPageFinished() {
        final String name = "onPageFinished";
        final String desc = "(Landroid/webkit/WebView;Ljava/lang/String;)V";
        if (declaredTargets.contains(name + desc)) {
            return;
        }
        MethodVisitor mv = cv.visitMethod(Opcodes.ACC_PUBLIC, name, desc, null, null);
        if (mv == null) {
            return;
        }
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitVarInsn(Opcodes.ALOAD, 2);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, WEBVIEW_CALLBACKS, "onPageFinishedCalled",
                "(Landroid/webkit/WebViewClient;Landroid/webkit/WebView;Ljava/lang/String;)V", false);
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitVarInsn(Opcodes.ALOAD, 2);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, superName, name, desc, false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(3, 3);
        mv.visitEnd();
        onSynthesized(name, desc);
    }

    private void onSynthesized(String name, String desc) {
        context.markModified();
        log.debug("[WebViewMethodClassVisitor] Synthesized inherited " + name + desc
                + " override in " + visitedClassName + " (extends " + superName + ")");
    }

    private static final String WEBVIEW_CALLBACKS =
            "com/newrelic/agent/android/webView/WebViewInstrumentationCallbacks";

}