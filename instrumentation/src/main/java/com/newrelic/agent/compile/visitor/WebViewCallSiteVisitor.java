/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.compile.visitor;

import com.newrelic.agent.compile.InstrumentationContext;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.slf4j.Logger;

/**
 * This visitor instruments WebView method calls by injecting tracking code
 * BEFORE the actual call, avoiding StackOverflow issues that occur when
 * replacing methods that are overridden in subclasses.
 *
 * <h2>Purpose</h2>
 * This visitor intercepts INVOKEVIRTUAL calls to android.webkit.WebView methods
 * and injects tracking calls before the original method executes. It works in
 * tandem with {@link WebViewMethodClassVisitor} to provide comprehensive coverage:
 * <ul>
 *   <li><b>WebViewCallSiteVisitor</b>: Instruments call sites where the static type
 *       is android.webkit.WebView (e.g., {@code WebView wv = ...; wv.loadUrl(...)})</li>
 *   <li><b>WebViewMethodClassVisitor</b>: Instruments overridden methods in WebView
 *       subclasses (e.g., {@code class MyWebView extends WebView { void loadUrl(...) }})</li>
 * </ul>
 *
 * <h2>Why Only Instrument android.webkit.WebView Owner?</h2>
 * <p>The check {@code owner.equals("android/webkit/WebView")} is intentionally strict:</p>
 * <ul>
 *   <li>JVM bytecode uses the <b>static (declared) type</b> for INVOKEVIRTUAL owner,
 *       so polymorphic calls like {@code WebView wv = new ChromeWebView(); wv.loadUrl()}
 *       will have owner = "android/webkit/WebView" and are correctly instrumented</li>
 *   <li>Direct subclass calls like {@code MyWebView wv = new MyWebView(); wv.loadUrl()}
 *       have owner = "com/example/MyWebView" and are handled by WebViewMethodClassVisitor
 *       when the subclass overrides the method</li>
 *   <li>This avoids false positives from non-WebView classes that happen to have
 *       similarly named methods (e.g., {@code class Foo { void loadUrl(String s) }})</li>
 * </ul>
 *
 * @see WebViewMethodClassVisitor
 */
public class WebViewCallSiteVisitor extends ClassVisitor {
    private final InstrumentationContext context;
    private final Logger log;
    private String className;

    public WebViewCallSiteVisitor(ClassVisitor cv, InstrumentationContext context, Logger log) {
        super(Opcodes.ASM9, cv);
        this.context = context;
        this.log = log;
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        this.className = name;
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
        return new WebViewMethodVisitor(mv, className, log, context);
    }

    private static class WebViewMethodVisitor extends MethodVisitor {
        private final String className;
        private final Logger log;
        private final InstrumentationContext context;

        public WebViewMethodVisitor(MethodVisitor mv, String className, Logger log, InstrumentationContext context) {
            super(Opcodes.ASM9, mv);
            this.className = className;
            this.log = log;
            this.context = context;
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            // Only instrument virtual calls to WebView (INVOKEVIRTUAL)
            // Skip static calls, constructors, and super calls
            if (opcode != Opcodes.INVOKEVIRTUAL) {
                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                return;
            }

            // Check if this is a WebView method we want to instrument
            if (owner.equals("android/webkit/WebView")) {
                if (name.equals("loadUrl") && descriptor.equals("(Ljava/lang/String;)V")) {
                    // Instrument loadUrl(String url)
                    instrumentLoadUrl(owner, name, descriptor, isInterface);
                    return;
                } else if (name.equals("loadUrl") && descriptor.equals("(Ljava/lang/String;Ljava/util/Map;)V")) {
                    // Instrument loadUrl(String url, Map<String,String> headers)
                    instrumentLoadUrlWithHeaders(owner, name, descriptor, isInterface);
                    return;
                } else if (name.equals("postUrl") && descriptor.equals("(Ljava/lang/String;[B)V")) {
                    // Instrument postUrl(String url, byte[] postData)
                    instrumentPostUrl(owner, name, descriptor, isInterface);
                    return;
                }
            }

            // For all other methods, just pass through
            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
        }

        /**
         * Instruments loadUrl(String url) by duplicating the WebView instance
         * on the stack and calling our tracking method BEFORE the actual loadUrl call.
         *
         * Stack before: [webView, url]
         * Stack after injection: [webView, url]
         *
         * Injected bytecode:
         * DUP2             // [webView, url, webView, url]
         * POP              // [webView, url, webView]
         * INVOKESTATIC     // Call tracking: [webView, url]
         * INVOKEVIRTUAL    // Call original: []
         */
        private void instrumentLoadUrl(String owner, String name, String descriptor, boolean isInterface) {
            log.debug("[WebViewCallSiteVisitor] Instrumenting loadUrl(String) call in class: " + className);

            // Stack: [webView, url]
            // Duplicate both values
            mv.visitInsn(Opcodes.DUP2);  // Stack: [webView, url, webView, url]

            // Remove the duplicate url
            mv.visitInsn(Opcodes.POP);   // Stack: [webView, url, webView]

            // Call our tracking method: WebViewInstrumentationCallbacks.loadUrlCalled(WebView)
            mv.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    "com/newrelic/agent/android/webView/WebViewInstrumentationCallbacks",
                    "loadUrlCalled",
                    "(Landroid/webkit/WebView;)V",
                    false
            );
            // Stack: [webView, url]

            // Now the stack is back to [webView, url], proceed with original call
            super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, owner, name, descriptor, isInterface);

            context.markModified();
        }

        /**
         * Instruments loadUrl(String url, Map headers)
         *
         * Stack before: [webView, url, headers]
         * Strategy: Store params in locals, call tracking, restore params, call original
         */
        private void instrumentLoadUrlWithHeaders(String owner, String name, String descriptor, boolean isInterface) {
            log.debug("[WebViewCallSiteVisitor] Instrumenting loadUrl(String, Map) call in class: " + className);

            // Stack: [webView, url, headers]

            // Store headers in a local variable (we'll use a temporary slot)
            mv.visitVarInsn(Opcodes.ASTORE, 100);  // Stack: [webView, url]

            // Store url in a local variable
            mv.visitVarInsn(Opcodes.ASTORE, 101);  // Stack: [webView]

            // Duplicate webView for tracking
            mv.visitInsn(Opcodes.DUP);  // Stack: [webView, webView]

            // Call tracking
            mv.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    "com/newrelic/agent/android/webView/WebViewInstrumentationCallbacks",
                    "loadUrlCalled",
                    "(Landroid/webkit/WebView;)V",
                    false
            );
            // Stack: [webView]

            // Restore parameters for original call
            mv.visitVarInsn(Opcodes.ALOAD, 101);   // Stack: [webView, url]
            mv.visitVarInsn(Opcodes.ALOAD, 100);   // Stack: [webView, url, headers]

            // Call original method
            super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, owner, name, descriptor, isInterface);

            context.markModified();
        }

        /**
         * Instruments postUrl(String url, byte[] postData)
         *
         * Stack before: [webView, url, postData]
         * Strategy: Store params in locals, call tracking, restore params, call original
         */
        private void instrumentPostUrl(String owner, String name, String descriptor, boolean isInterface) {
            log.debug("[WebViewCallSiteVisitor] Instrumenting postUrl(String, byte[]) call in class: " + className);

            // Stack: [webView, url, postData]

            // Store postData in a local variable
            mv.visitVarInsn(Opcodes.ASTORE, 100);  // Stack: [webView, url]

            // Store url in a local variable
            mv.visitVarInsn(Opcodes.ASTORE, 101);  // Stack: [webView]

            // Duplicate webView for tracking
            mv.visitInsn(Opcodes.DUP);  // Stack: [webView, webView]

            // Call tracking
            mv.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    "com/newrelic/agent/android/webView/WebViewInstrumentationCallbacks",
                    "postUrlCalled",
                    "(Landroid/webkit/WebView;)V",
                    false
            );
            // Stack: [webView]

            // Restore parameters for original call
            mv.visitVarInsn(Opcodes.ALOAD, 101);   // Stack: [webView, url]
            mv.visitVarInsn(Opcodes.ALOAD, 100);   // Stack: [webView, url, postData]

            // Call original method
            super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, owner, name, descriptor, isInterface);

            context.markModified();
        }
    }
}