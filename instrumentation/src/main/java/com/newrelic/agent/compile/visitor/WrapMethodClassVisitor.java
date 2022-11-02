/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.compile.visitor;

import com.newrelic.agent.compile.ClassMethod;
import com.newrelic.agent.compile.InstrumentationContext;
import com.newrelic.agent.compile.Log;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import java.text.MessageFormat;
import java.util.Collection;

public class WrapMethodClassVisitor extends ClassVisitor {
    private final InstrumentationContext context;
    private final Log log;

    public WrapMethodClassVisitor(ClassVisitor cv, final InstrumentationContext context, final Log log) {
        super(Opcodes.ASM8, cv);
        this.context = context;
        this.log = log;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] exceptions) {
        if (context.isSkippedMethod(name, desc)) {
            return super.visitMethod(access, name, desc, sig, exceptions);
        }

        return new MethodWrapMethodVisitor(super.visitMethod(access, name, desc, sig, exceptions), access, name, desc, context, log);
    }

    private static final class MethodWrapMethodVisitor extends GeneratorAdapter {
        private final String name;
        private final String desc;
        private final InstrumentationContext context;
        private final Log log;
        private boolean newInstructionFound = false;
        private boolean dupInstructionFound = false;

        public MethodWrapMethodVisitor(MethodVisitor mv, final int access, final String name, final String desc, final InstrumentationContext context, final Log log) {
            super(Opcodes.ASM8, mv, access, name, desc);
            this.name = name;
            this.desc = desc;
            this.context = context;
            this.log = log;
        }

        @SuppressWarnings("deprecation")
        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc) {
            this.visitMethodInsn(opcode, owner, name, desc, (opcode == Opcodes.INVOKEINTERFACE));
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean isInterface) {
            //
            // Unlikely that we care about INVOKEDYNAMIC right now
            //
            if (opcode == Opcodes.INVOKEDYNAMIC) {
                log.warning(MessageFormat.format("[{0}] INVOKEDYNAMIC instruction cannot be instrumented", context.getClassName().replaceAll("/", ".")));
                super.visitMethodInsn(opcode, owner, name, desc, isInterface);
                return;
            }

            if (!tryReplaceCallSite(opcode, owner, name, desc)) {
                if (!tryWrapReturnValue(opcode, owner, name, desc)) {
                    super.visitMethodInsn(opcode, owner, name, desc, isInterface);
                }
            }
        }

        // These two callbacks are used to detect the presence of the DUP instruction after a NEW.  If the resulting
        // object is never used, the compiler will exclude the DUP call.  This creates a problem below where we pop twice
        // to move up past the object on the stack.  Thus, in this case, we only pop once.
        @Override
        public void visitTypeInsn(int opcode, String type) {
            if (opcode == Opcodes.NEW) {
                newInstructionFound = true;
                dupInstructionFound = false;
            }

            super.visitTypeInsn(opcode, type);
        }

        @Override
        public void visitInsn(int opcode) {
            if (opcode == Opcodes.DUP) {
                dupInstructionFound = true;
            }

            super.visitInsn(opcode);
        }

        private boolean tryWrapReturnValue(final int opcode, final String owner, final String name, final String desc) {
            final ClassMethod method = new ClassMethod(owner, name, desc);

            final ClassMethod wrappingMethod = context.getMethodWrapper(method);
            if (wrappingMethod != null) {
                log.debug(MessageFormat.format("[{0}] wrapping call to {1} with {2}", context.getClassName().replaceAll("/", "."), method.toString(), wrappingMethod.toString()));
                super.visitMethodInsn(opcode, owner, name, desc, (opcode == Opcodes.INVOKEINTERFACE));
                super.visitMethodInsn(Opcodes.INVOKESTATIC, wrappingMethod.getClassName(), wrappingMethod.getMethodName(), wrappingMethod.getMethodDesc(), false);
                context.markModified();
                return true;
            }

            return false;
        }

        // NOTE: There may be some buggy arg handling in here since longs and doubles occupy two stack positions (or
        // registers in dalvik).  At some point we should research if this matters by testing what happens when we pass
        // long or double arguments.
        private boolean tryReplaceCallSite(final int opcode, final String owner, final String name, final String desc) {
            final Collection<ClassMethod> replacementMethods = context.getCallSiteReplacements(owner, name, desc);

            if (replacementMethods.isEmpty()) {
                return false;
            }

            ClassMethod method = new ClassMethod(owner, name, desc);

            for (final ClassMethod replacementMethod : replacementMethods) {
                //
                // INVOKESPECIAL is used to invoke a method directly on a super class (e.g.
                // when you do something like `super.execute(request)` in the derived class.).
                //
                // If we're dealing with an overridden method from the class we're trying to
                // instrument, we don't want to instrument these `super` calls or they'll
                // get instrumented twice (super call + caller).
                //
                // Note that INVOKESPECIAL is also used to call private methods, so we look
                // at the owner class name to determine if this is a `super` call or a private
                // call. (In practice, the name & desc comparison is probably enough.)
                //
                boolean isSuperCallInOverride =
                        opcode == Opcodes.INVOKESPECIAL &&
                                !owner.equals(context.getClassName()) &&
                                this.name.equals(name) &&
                                this.desc.equals(desc);

                if (isSuperCallInOverride) {
                    log.debug(MessageFormat.format(
                            "[{0}] skipping call site replacement for super call in overriden method: {1}:{2}",
                            context.getClassName().replaceAll("/", "."), this.name, this.desc));
                    return false;
                }

                // Swizzling a constructor takes a bit of hackery.  Here is what the stack looks like for a single arg constructor:
                // 64: new           #21                 // class org/json/JSONObject
                // 67: dup
                // 68: aload_2
                // 69: invokespecial #22                 // Method org/json/JSONObject."<init>":(Ljava/lang/String;)V
                // We'll just discard the original object and make a static all to our replacement.  The replacement will
                // be responsible for creating the object.  To discard the original object, we first store all the args
                // in locals.  Next, we pop the two objects off the stack (note the dup call) and then reload the args.
                // Finally, we'll call the static replacement method and its return value will be on top of the stack.
                if (opcode == Opcodes.INVOKESPECIAL && name.equals("<init>")) {
                    Method originalMethod = new Method(name, desc);

                    // At the moment, we don't want to instrument constructors that extend classes we instrument
                    if (context.getSuperClassName() != null && context.getSuperClassName().equals(owner)) {
                        log.debug(MessageFormat.format("[{0}] skipping call site replacement for class extending {1}", context.getFriendlyClassName(), context.getFriendlySuperClassName()));
                        return false;
                    }

                    log.debug(MessageFormat.format("[{0}] tracing constructor call to {1} - {2}", context.getFriendlyClassName(), method.toString(), owner));

                    // Store all the arguments in local variables
                    int[] locals = new int[originalMethod.getArgumentTypes().length];
                    for (int i = locals.length - 1; i >= 0; i--) {
                        locals[i] = newLocal(originalMethod.getArgumentTypes()[i]);
                        storeLocal(locals[i]);
                    }

                    // Pop back up the stack past the new object
                    visitInsn(Opcodes.POP);

                    // As mentioned above, there are some cases where there is no DUP instruction after a NEW.  Detect
                    // this case here and only pop once if necessary.
                    if (newInstructionFound && dupInstructionFound) {
                        visitInsn(Opcodes.POP);
                    }

                    // Reload the variables now that we've repositioned ourselves.
                    for (int local : locals) {
                        loadLocal(local);
                    }

                    // Finally, call the replacement method
                    super.visitMethodInsn(Opcodes.INVOKESTATIC, replacementMethod.getClassName(), replacementMethod.getMethodName(), replacementMethod.getMethodDesc(), false);

                    // If no dup was found, we know something shady is abound.  In most cases this is proguard removing
                    // the dup and we need a pop after our INVOKESTATIC to get rid of the object we leave on the stack.
                    if (newInstructionFound && !dupInstructionFound) {
                        visitInsn(Opcodes.POP);
                    }
                } else if (opcode == Opcodes.INVOKESTATIC) {
                    log.debug(MessageFormat.format("[{0}] replacing static call to {1} with {2}",
                            context.getClassName().replaceAll("/", "."), method.toString(), replacementMethod.toString()));

                    super.visitMethodInsn(Opcodes.INVOKESTATIC, replacementMethod.getClassName(), replacementMethod.getMethodName(), replacementMethod.getMethodDesc(), false);
                } else {
                    Method newMethod = new Method(replacementMethod.getMethodName(), replacementMethod.getMethodDesc());

                    // Always do the instance check because it covers the invocation target being null

                    log.debug(MessageFormat.format("[{0}] replacing call to {1} with {2} (with instance check)",
                            context.getClassName().replaceAll("/", "."), method.toString(), replacementMethod.toString()));
                    // We assume that if a method signature exactly matches the one we're interested in then the invocation
                    // target is of the correct type, but that may not be the case, so the code we inject does a runtime
                    // instanceof type check and only wraps the appropriate types.

                    // ugh.  Unfortunately, to do a type check we have to pop all the arguments off of the stack
                    Method originalMethod = new Method(name, desc);

                    // store all the arguments in local variables
                    int[] locals = new int[originalMethod.getArgumentTypes().length];
                    for (int i = locals.length - 1; i >= 0; i--) {
                        locals[i] = newLocal(originalMethod.getArgumentTypes()[i]);
                        storeLocal(locals[i]);
                    }

                    // dup the invocation target on the stack for the instance check
                    dup();

                    // check if the invocation target is the correct instance type
                    instanceOf(newMethod.getArgumentTypes()[0]);
                    Label isInstanceOfLabel = new Label();
                    visitJumpInsn(Opcodes.IFNE, isInstanceOfLabel);


                    // if not the correct instance, reload the args and invoke the original method
                    for (int local : locals) {
                        loadLocal(local);
                    }
                    super.visitMethodInsn(opcode, owner, name, desc, (opcode == Opcodes.INVOKEINTERFACE));

                    Label end = new Label();
                    visitJumpInsn(Opcodes.GOTO, end);
                    visitLabel(isInstanceOfLabel);

                    // This checkcast call is here to satisfy the Dalvik VM verifier.
                    checkCast(newMethod.getArgumentTypes()[0]);

                    // if the correct type, reload the args and invoke our wrapper
                    for (int local : locals) {
                        loadLocal(local);
                    }
                    super.visitMethodInsn(Opcodes.INVOKESTATIC,
                            replacementMethod.getClassName(), replacementMethod.getMethodName(), replacementMethod.getMethodDesc(), false);

                    visitLabel(end);
                }

                context.markModified();
                return true;
            }

            return false;
        }

    }

}
