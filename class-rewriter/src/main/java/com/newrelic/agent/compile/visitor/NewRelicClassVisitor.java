/*
 *
 * Copyright 2021 New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 *
 */

package com.newrelic.agent.compile.visitor;

import com.newrelic.agent.compile.InstrumentationContext;
import com.newrelic.agent.compile.Log;
import com.newrelic.agent.compile.RewriterAgent;
import com.newrelic.agent.util.BuildId;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.GeneratorAdapter;

public class NewRelicClassVisitor extends ClassVisitor {
    private final InstrumentationContext context;
    private final Log log;

    public NewRelicClassVisitor(ClassVisitor cv, final InstrumentationContext context, final Log log) {
        super(Opcodes.ASM8, cv);
        this.context = context;
        this.log = log;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        // Ensure we're only instrumenting isInstrumented() within the NewRelic class.
        if (context.getClassName().equals("com/newrelic/agent/android/NewRelic") && name.equals("isInstrumented")) {
            return new NewRelicMethodVisitor(super.visitMethod(access, name, desc, signature, exceptions), access, name, desc);
        }

        if (context.getClassName().equals("com/newrelic/agent/android/crash/Crash") && name.equals("getBuildId")) {
            return new BuildIdMethodVisitor(super.visitMethod(access, name, desc, signature, exceptions), access, name, desc);
        }

        return super.visitMethod(access, name, desc, signature, exceptions);
    }

    @Override
    public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
        if (context.getClassName().equals("com/newrelic/agent/android/Agent") && name.equals("VERSION")) {
            if (!value.equals(RewriterAgent.getVersion())) {
                log.warning("New Relic Error: Your agent and class rewriter versions do not match: "
                        + "agent[" + value + "] class rewriter[" + RewriterAgent.getVersion() + "]. "
                        + "You may need to update one of these components, or simply invalidate your AndroidStudio cache.  "
                        + "If you're using gradle and just updated, run gradle -stop to restart the daemon.");
            }
        }

        return super.visitField(access, name, desc, signature, value);
    }

    // This sets the build identifier. For now we just generate it at random. However, we'll want to link this to
    // the proguard file in the future.
    private final class BuildIdMethodVisitor extends GeneratorAdapter {
        public BuildIdMethodVisitor(MethodVisitor mv, final int access, final String name, final String desc) {
            super(Opcodes.ASM8, mv, access, name, desc);
        }

        @Override
        public void visitCode() {
            String buildId = BuildId.getBuildId(context.getVariantName());

            super.visitLdcInsn(buildId);
            super.visitInsn(Opcodes.ARETURN);

            log.info("[NewRelicMethodVisitor] Setting build identifier to [" + buildId + "]");
            context.markModified();
        }
    }

    /*
     * This method visitor is used to flip a boolean in the NewRelic agent
     * class. During boot, the agent will call instrumented(). If the method
     * returns false (what it does if not instrumented), the agent will warn the
     * developer, suggest contacting support and prevent the agent from booting.
     */
    private final class NewRelicMethodVisitor extends GeneratorAdapter {
        public NewRelicMethodVisitor(MethodVisitor mv, final int access, final String name, final String desc) {
            super(Opcodes.ASM8, mv, access, name, desc);
        }

        @Override
        public void visitCode() {
            super.visitInsn(Opcodes.ICONST_1);  // true
            super.visitInsn(Opcodes.IRETURN);   // return

            log.info("[NewRelicMethodVisitor] Marking NewRelic agent as instrumented");
            context.markModified();
        }
    }
}
