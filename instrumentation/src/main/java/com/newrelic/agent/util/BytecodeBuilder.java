package com.newrelic.agent.util;

import com.newrelic.agent.Constants;
import com.newrelic.agent.compile.InvocationDispatcher;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;

/*
 * A builder pattern for generating a lot of our bytecode.
 */
public final class BytecodeBuilder {
    private final GeneratorAdapter mv;

    public BytecodeBuilder(GeneratorAdapter adapter) {
        this.mv = adapter;
    }

    public BytecodeBuilder loadNull() {
        mv.visitInsn(Opcodes.ACONST_NULL);
        return this;
    }

    /*
     * Pushes the InvocationDispatcher instance onto the stack by fetching it from the Proxy field in which it's stashed.
     */
    public BytecodeBuilder loadInvocationDispatcher() {
        // get our InvocationHandler from the private field on the Java Proxy class
        mv.visitLdcInsn(Type.getType(InvocationDispatcher.INVOCATION_DISPATCHER_CLASS));
        mv.visitLdcInsn(InvocationDispatcher.INVOCATION_DISPATCHER_FIELD_NAME);
        mv.invokeVirtual(Type.getType(Class.class), new Method("getDeclaredField",
                "(Ljava/lang/String;)Ljava/lang/reflect/Field;"));

        mv.dup();
        mv.visitInsn(Opcodes.ICONST_1); // true
        mv.invokeVirtual(Type.getType(Field.class), new Method("setAccessible", "(Z)V"));

        // InstrumentationAgent.INVOCATION_DISPATCHER_FIELD_NAME is a static field so we can just load null for the get invocation
        mv.visitInsn(Opcodes.ACONST_NULL);

        mv.invokeVirtual(Type.getType(Field.class), new Method("get", "(Ljava/lang/Object;)Ljava/lang/Object;"));

        return this;
    }

    /*
     * Using the given method descriptor, create an Object array sized to hold all of the arguments and
     * store all of the arguments into it.
     */
    public BytecodeBuilder loadArgumentsArray(String methodDesc) {

        Method method = new Method("dummy", methodDesc);
        mv.push(method.getArgumentTypes().length);
        Type objectType = Type.getType(Object.class);
        mv.newArray(objectType);

        for (int i = 0; i < method.getArgumentTypes().length; i++) {
            mv.dup();
            mv.push(i);
            mv.loadArg(i);
            mv.arrayStore(objectType);
        }
        return this;
    }

    /*
     * Loads an array of r.length items onto the stack.  Each runnable should load one item onto the
     * stack to be stored into the array.
     */
    public BytecodeBuilder loadArray(Runnable... r) {
        mv.push(r.length);
        Type objectType = Type.getObjectType("java/lang/Object");
        mv.newArray(objectType);

        for (int i = 0; i < r.length; i++) {
            mv.dup();
            mv.push(i);
            r[i].run();
            mv.arrayStore(objectType);
        }

        return this;
    }

    public BytecodeBuilder printToInfoLogFromBytecode(final String message) {
        loadInvocationDispatcher();

        mv.visitLdcInsn(Constants.NR_PRINT_INFO_FLAG);
        mv.visitInsn(Opcodes.ACONST_NULL);

        loadArray(new Runnable() {
            public void run() {
                mv.visitLdcInsn(message);
            }
        });

        invokeDispatcher();
        return this;
    }

    /*
     * Inject the instructions to invoke the {@link InvocationDispatcher#invoke(Object, java.lang.reflect.Method, Object[])}
     * method.  The return value will be popped off the stack.
     */
    public BytecodeBuilder invokeDispatcher() {
        return invokeDispatcher(true);
    }

    /*
     * Inject the instructions to invoke the {@link InvocationDispatcher#invoke(Object, java.lang.reflect.Method, Object[])} method.
     */
    public BytecodeBuilder invokeDispatcher(boolean popReturnOffStack) {
        mv.invokeInterface(Type.getType(InvocationHandler.class), new Method("invoke", "(Ljava/lang/Object;Ljava/lang/reflect/Method;[Ljava/lang/Object;)Ljava/lang/Object;"));
        if (popReturnOffStack) {
            mv.pop();
        }
        return this;
    }

    /*
     * Loads the first two arguments for {@link InvocationDispatcher#invoke(Object, java.lang.reflect.Method, Object[])}
     * onto the stack.
     */
    public BytecodeBuilder loadInvocationDispatcherKey(String key) {
        // this is the InvocationDispatcher.invoke proxy
        mv.visitLdcInsn(key);

        // this is the InvocationDispatcher.invoke method (which we ignore)
        mv.visitInsn(Opcodes.ACONST_NULL);

        return this;
    }
}
