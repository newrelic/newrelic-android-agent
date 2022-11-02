package com.newrelic.agent.compile;

import com.newrelic.agent.compile.transformers.ClassRewriterTransformer;
import com.newrelic.agent.compile.transformers.DexClassTransformer;
import com.newrelic.agent.compile.transformers.NewRelicClassTransformer;
import com.newrelic.agent.compile.transformers.NoOpClassTransformer;
import com.newrelic.agent.util.Streams;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.ClassDefinition;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class RewriterAgent {
    public static final String VERSION = "replaceme";
    public static final String DISABLE_INSTRUMENTATION_SYSTEM_PROPERTY = "newrelic.instrumentation.disabled";
    public static final String SET_INSTRUMENTATION_DISABLED_FLAG = "SET_INSTRUMENTATION_DISABLED_FLAG";
    public static final String PRINT_TO_INFO_LOG = "PRINT_TO_INFO_LOG";

    private static String agentArgs;
    private static Map<String, String> agentOptions = Collections.emptyMap();

    /**
     * This is the main javaagent callback.  This class / method is specified in the jar manifest.
     *
     * @param agentArgs
     * @param instrumentation
     */
    public static void premain(String agentArgs, Instrumentation instrumentation) {
        Throwable argsError = null;

        RewriterAgent.agentArgs = agentArgs;

        try {
            agentOptions = parseAgentArgs(agentArgs);
        } catch (Throwable t) {
            argsError = t;
        }

        String logFileName = agentOptions.get("logfile");

        Log log = logFileName == null ? new SystemErrLog(agentOptions) : new FileLogImpl(agentOptions, logFileName);
        if (argsError != null) {
            log.error("Agent args error: " + agentArgs, argsError);
        }

        final String nameOfRunningVM = ManagementFactory.getRuntimeMXBean().getName();
        final int p = nameOfRunningVM.indexOf('@');
        final String pid = nameOfRunningVM.substring(0, p);

        log.debug("Bootstrapping New Relic Android class rewriter");
        log.debug("Agent args[" + agentArgs + "]");
        log.debug("Agent running in pid " + pid + " arguments: " + agentArgs);

        try {
            NewRelicClassTransformer classTransformer;

            if (agentOptions.containsKey("deinstrument")) {
                log.info("Deinstrumenting...");
                classTransformer = new NoOpClassTransformer();
            } else {
                if (agentOptions.containsKey("classTransformer")) {
                    log.info("Using class transformer.");
                    classTransformer = new ClassRewriterTransformer(log);
                } else {
                    log.info("Using DEX transformer.");
                    classTransformer = new DexClassTransformer(log);
                }
                createInvocationDispatcher(log);
            }

            instrumentation.addTransformer(classTransformer, true);

            final List<Class<?>> classes = new ArrayList<Class<?>>();
            for (Class<?> clazz : instrumentation.getAllLoadedClasses()) {
                if (classTransformer.modifies(clazz)) {
                    classes.add(clazz);
                }
            }

            if (!classes.isEmpty()) {
                if (instrumentation.isRetransformClassesSupported()) {
                    instrumentation.retransformClasses(classes.toArray(new Class[classes.size()]));
                } else {
                    log.warning("Unable to retransform classes: " + classes);
                }
            }

			/*
             * We redefine this class separately since, in some cases, it's not
			 * available for retransformation. Class redefinition is dangerous
			 * as it isn't really compatible with the chained nature of
			 * instrumentation agents. So far, we've observed this issue with
			 * the Gradle plugin.
			 */
            if (!agentOptions.containsKey("deinstrument")) {
                redefineClass(instrumentation, classTransformer, ProcessBuilder.class);
            }
        } catch (Throwable ex) {
            log.error("Agent startup error", ex);
            throw new RuntimeException(ex);
        }
    }

    public static void agentmain(String agentArgs, Instrumentation instrumentation) {
        premain(agentArgs, instrumentation);
    }

    public static String getVersion() {
        return VERSION;
    }

    public static Map<String, String> getAgentOptions() {
        return agentOptions;
    }

    public static String getAgentArgs() {
        return agentArgs;
    }

    public static String getProxyInvocationKey(String className, String methodName) {
        return className + "." + methodName;
    }

    private static void redefineClass(Instrumentation instrumentation, ClassFileTransformer classTransformer, Class<?> klass)
            throws IOException, IllegalClassFormatException, ClassNotFoundException,
            UnmodifiableClassException {
        String internalClassName = klass.getName().replace('.', '/');
        String classPath = internalClassName + ".class";

        ClassLoader cl = klass.getClassLoader() == null ? RewriterAgent.class.getClassLoader() : klass.getClassLoader();
        InputStream stream = cl.getResourceAsStream(classPath);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Streams.copy(stream, output);

        stream.close();

        byte[] newBytes = classTransformer.transform(klass.getClassLoader(), internalClassName, klass, null, output.toByteArray());

        ClassDefinition def = new ClassDefinition(klass, newBytes);
        instrumentation.redefineClasses(def);
    }

    public static Map<String, String> parseAgentArgs(String agentArgs) {
        if (agentArgs == null) {
            return Collections.emptyMap();
        }
        Map<String, String> options = new HashMap<String, String>();
        for (String arg : agentArgs.split(";")) {
            String[] keyValue = arg.split("=");
            if (keyValue.length == 2) {
                options.put(keyValue[0], keyValue[1]);
            } else {
                throw new IllegalArgumentException("Invalid argument: " + arg);
            }
        }

        return options;
    }

    /**
     * Returns the full path to the instrumentation agent jar (that contains this class).
     */
    public static String getAgentJarPath() throws URISyntaxException {
        return new File(RewriterAgent.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath()).getAbsolutePath();
    }

    /**
     * The classloader used to load the main dex class may not have visibility to our code, so we implement an InvocationHandler
     * and stuff it in a private static field on the Java Proxy class.
     */
    private static void createInvocationDispatcher(Log log) throws Exception {
        Field field = InvocationDispatcher.INVOCATION_DISPATCHER_CLASS.getDeclaredField(InvocationDispatcher.INVOCATION_DISPATCHER_FIELD_NAME);
        field.setAccessible(true);

        // In some versions of java this field is final, so let's just fix that...
        Field modifiersField = Field.class.getDeclaredField("modifiers");
        modifiersField.setAccessible(true);
        modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);

        // Since the Gradle daemon caches things, it's possible we've been here before.  Thus, we'll only do this if needed.
        if (field.get(null) instanceof InvocationDispatcher) {
            log.info("Detected cached instrumentation.");
        } else {
            field.set(null, new InvocationDispatcher(log));
        }
    }

}
