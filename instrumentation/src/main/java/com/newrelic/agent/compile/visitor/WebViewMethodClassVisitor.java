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

import java.util.Map;

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
            "^android/webkit/WebView"          // WebView and its subclasses
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

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        this.access = access;
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

    @Override
    public void visitEnd() {
        super.visitEnd();
    }

}