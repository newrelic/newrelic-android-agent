package com.newrelic.agent.compile.visitor;

import com.newrelic.agent.compile.RewriterAgent;
import org.objectweb.asm.MethodVisitor;

/**
 * This method visitor invokes the InvocationDispatcher at the end of the method and sets the disabled
 * instrumentation flag to false.  The code injected at method enter should set the instrument flag to
 * either true or false based on some check.
 *
 */
public abstract class SafeInstrumentationMethodVisitor extends BaseMethodVisitor {
    protected SafeInstrumentationMethodVisitor(MethodVisitor mv, int access, String methodName, String desc) {
        super(mv, access, methodName, desc);
    }

    @Override
    protected final void onMethodExit(int opcode) {
        builder.
                loadInvocationDispatcher().
                loadInvocationDispatcherKey(RewriterAgent.SET_INSTRUMENTATION_DISABLED_FLAG).
                loadNull().
                invokeDispatcher();

        super.onMethodExit(opcode);
    }
}


