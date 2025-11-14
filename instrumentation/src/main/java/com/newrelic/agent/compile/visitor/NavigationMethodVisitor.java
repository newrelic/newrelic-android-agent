package com.newrelic.agent.compile.visitor;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.AdviceAdapter;
import org.slf4j.Logger;

public class NavigationMethodVisitor extends AdviceAdapter {
    Logger log;

    public NavigationMethodVisitor(
            int apiVersion,
            MethodVisitor originalVisitor,
            int access,
            String name,
            String descriptor,
            Logger log
    ) {
        super(apiVersion, originalVisitor, access, name, descriptor);
        this.log = log;

    }

    @Override
    public void onMethodExit(int opcode) {
        log.info("NavigationMethodVisitor");
        // loadArg(0) -> not needed as NavHostController is already on the stack.
        loadArg(1);
        loadArg(2);


        // Call the static method withNewRelicNavigationListener
        visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "com/newrelic/agent/android/instrumentation/ComposeNavigationInstrumentationKt",
                "withNewRelicNavigationListener",
                "(Landroidx/navigation/NavHostController;Landroidx/compose/runtime/Composer;I)Landroidx/navigation/NavHostController;",
                false
        );
        super.onMethodExit(opcode);
    }
}