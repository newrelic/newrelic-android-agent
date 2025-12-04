/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent;

import com.newrelic.agent.compile.FileLogger;
import com.newrelic.agent.compile.InvocationDispatcher;
import com.newrelic.agent.compile.Logger;
import com.newrelic.agent.compile.SystemLogger;
import com.newrelic.agent.compile.transformers.ClassRewriterTransformer;
import com.newrelic.agent.compile.transformers.NewRelicClassTransformer;
import com.newrelic.agent.util.BuildId;

import java.io.File;
import java.lang.instrument.Instrumentation;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class InstrumentationAgent extends Constants {
    public static final String VERSION = "replaceme";

    public static final String LOG_INSTRUMENTATION_ENABLED = "logInstrumentationEnabled";
    public static final String DEFAULT_INTERACTION_ENABLED = "defaultInteractionsEnabled";
    public static final String WEBVIEW_INSTRUMENTATION_ENABLED = "webviewInstrumentationEnabled";

    public static org.slf4j.Logger LOGGER = new Logger() {};
    private static Map<String, String> agentOptions = new HashMap<String, String>();

    public static void setLogger(org.slf4j.Logger LOGGER) {
        InstrumentationAgent.LOGGER = LOGGER;
    }

    public static Throwable withAgentArgs(String agentArgs) {
        try {
            InstrumentationAgent.agentOptions = parseAgentArgs(agentArgs);
            if (!agentOptions.isEmpty() && LOGGER instanceof Logger) {
                InstrumentationAgent.LOGGER = new SystemLogger(agentOptions);
            }
            System.setProperty(Constants.NR_AGENT_ARGS_KEY, agentArgs);
            BuildId.invalidate();

        } catch (Throwable t) {
            return t;
        }

        return null;
    }

    /**
     * This is the main javaagent callback.  This class / method is specified in the jar manifest.
     */
    @Deprecated
    public static void premain(String agentArgs, Instrumentation instrumentation) {
        Throwable argsError = withAgentArgs(agentArgs);
        String logFileName = agentOptions.get("logfile");

        org.slf4j.Logger log = logFileName == null ? new SystemLogger(agentOptions) : new FileLogger(agentOptions, logFileName);
        if (argsError != null) {
            log.error("Agent args error: " + argsError);
        }

        final String nameOfRunningVM = ManagementFactory.getRuntimeMXBean().getName();
        final int p = nameOfRunningVM.indexOf('@');
        final String pid = nameOfRunningVM.substring(0, p);

        log.debug("Bootstrapping New Relic Android class rewriter");
        log.debug("Agent running in pid " + pid + " arguments: " + agentArgs);

        try {
            NewRelicClassTransformer classTransformer = new ClassRewriterTransformer(log);

            log.info("Using class transformer.");
            createInvocationDispatcher(log);

            instrumentation.addTransformer(classTransformer, true);

            final List<Class<?>> classes = new ArrayList<>();
            for (Class<?> clazz : instrumentation.getAllLoadedClasses()) {
                if (classTransformer.modifies(clazz)) {
                    classes.add(clazz);
                }
            }

            if (!classes.isEmpty()) {
                if (instrumentation.isRetransformClassesSupported()) {
                    instrumentation.retransformClasses(classes.toArray(new Class[classes.size()]));
                } else {
                    log.warn("Unable to retransform classes: " + classes);
                }
            }

        } catch (Throwable ex) {
            log.error("Agent startup error", ex);
            throw new RuntimeException(ex);
        }
    }

    @SuppressWarnings("deprecation")
    @Deprecated
    public static void agentmain(String agentArgs, Instrumentation instrumentation) {
        premain(agentArgs, instrumentation);
    }

    public static String getVersion() {
        return VERSION;
    }

    public static Map<String, String> getAgentOptions() {
        return agentOptions;
    }

    public static String getProxyInvocationKey(String className, String methodName) {
        return className + "." + methodName;
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
            }
        }

        return options;
    }

    /**
     * Returns the full path to the instrumentation agent jar (that contains this class).
     */
    public static String getAgentJarPath() throws URISyntaxException {
        return new File(InstrumentationAgent.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath()).getAbsolutePath();
    }

    /**
     * The classloader used to load the main dex class may not have visibility to our code, so we implement an InvocationHandler
     * and stuff it in a private static field on the Java Proxy class.
     */
    private static void createInvocationDispatcher(org.slf4j.Logger log) throws Exception {
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
            boolean logInstrumentationEnabled = agentOptions.get(LOG_INSTRUMENTATION_ENABLED) != null && agentOptions.get(LOG_INSTRUMENTATION_ENABLED).equals("true");
            boolean defaultInteractionsEnabled = agentOptions.get(DEFAULT_INTERACTION_ENABLED) != null && agentOptions.get(DEFAULT_INTERACTION_ENABLED).equals("true");
            boolean webviewInstrumentationEnabled = agentOptions.get(WEBVIEW_INSTRUMENTATION_ENABLED) != null && agentOptions.get(WEBVIEW_INSTRUMENTATION_ENABLED).equals("true");
            field.set(null, new InvocationDispatcher(log, logInstrumentationEnabled, defaultInteractionsEnabled, webviewInstrumentationEnabled));
        }
    }

}
