/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.compile;

import com.newrelic.agent.Constants;
import com.newrelic.agent.InstrumentationAgent;
import com.newrelic.agent.compile.transformers.NewRelicClassTransformer;
import com.newrelic.agent.compile.visitor.ActivityClassVisitor;
import com.newrelic.agent.compile.visitor.AnnotatingClassVisitor;
import com.newrelic.agent.compile.visitor.AsyncTaskClassVisitor;
import com.newrelic.agent.compile.visitor.ContextInitializationClassVisitor;
import com.newrelic.agent.compile.visitor.FragmentClassVisitor;
import com.newrelic.agent.compile.visitor.NewRelicClassVisitor;
import com.newrelic.agent.compile.visitor.PrefilterClassVisitor;
import com.newrelic.agent.compile.visitor.TraceAnnotationClassVisitor;
import com.newrelic.agent.compile.visitor.WrapMethodClassVisitor;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;

import java.lang.reflect.InvocationHandler;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * The bytecode we inject (often) can't directly invoke methods on our classes because of
 * classloader visibility, so this InvocationHandler is used to allow our injected code to
 * invoke a number of different handlers. The proxyargument of the invoke call is always a
 * string that indicates what handler should execute. This allows us to program at a higher
 * level instead of writing everything in bytecode.
 */
public class InvocationDispatcher implements InvocationHandler {

    public static final Class<Logger> INVOCATION_DISPATCHER_CLASS = Logger.class;
    public static final String INVOCATION_DISPATCHER_FIELD_NAME = "treeLock";

    private final Log log;
    private final InstrumentationContext instrumentationContext;
    private final Pattern androidPackagePattern = Pattern.compile(Constants.ANDROID_PACKAGE_RE);
    private final Pattern kotlinPackagePattern = Pattern.compile(Constants.ANDROID_KOTLIN_PACKAGE_RE);

    final Map<String, InvocationHandler> invocationHandlers;
    private boolean writeDisabledMessage = true;

    public InvocationDispatcher(final Log log) throws ClassNotFoundException {
        ClassRemapperConfig config = new ClassRemapperConfig(log);

        this.log = log;
        this.instrumentationContext = new InstrumentationContext(config, log);
        this.invocationHandlers = Collections.unmodifiableMap(
                new HashMap<String, InvocationHandler>() {{
                    String proxyInvocationKey = InstrumentationAgent.getProxyInvocationKey(
                            NewRelicClassTransformer.NR_CLASS_REWRITER_CLASS_NAME,
                            NewRelicClassTransformer.NR_CLASS_REWRITER_METHOD_NAME);

                    put(proxyInvocationKey, new InvocationHandler() {
                        @Override
                        public Object invoke(Object proxy,
                                             java.lang.reflect.Method method,
                                             Object[] args) {
                            String filename = (String) args[0];
                            byte[] bytes = (byte[]) args[1];

                            if (isInstrumentationDisabled()) {
                                if (writeDisabledMessage) {
                                    writeDisabledMessage = false;
                                    log.info("Instrumentation disabled, no agent present");
                                }
                                return bytes;
                            }

                            writeDisabledMessage = true;

                            synchronized (instrumentationContext) {
                                log.debug("ClassTransformer/transformClassBytes arg[0](filename)[" + filename + "] arg[1](bytes)[" + bytes.length + "]");
                                log.debug("ClassTransformer/transformClassBytes proxy[" + proxy + "]");
                                log.debug("ClassTransformer/transformClassBytes method[" + method + "]");
                                log.debug("ClassTransformer/transformClassBytes args[" + args + "]");

                                ClassData classData = visitClassBytes(bytes);
                                if (instrumentationContext.isClassModified()) {
                                    if (classData != null && classData.getClassBytes() != null) {
                                        if ((bytes.length != classData.getClassBytes().length)) {
                                            log.debug("ClassTransformer/transformClassBytes transformed bytes[" + classData.getClassBytes().length + "]");
                                        }
                                        return classData.getClassBytes();
                                    }
                                }
                            }

                            return null;
                        }

                    });
                }});
    }

    boolean isInstrumentationDisabled() {
        return System.getProperty(Constants.NR_DISABLE_INSTRUMENTATION_KEY) != null;
    }

    boolean isIncludedPackage(String packageName) {
        String lowercasePackageName = packageName.toLowerCase();

        for (String name : Constants.ANDROID_INCLUDED_PACKAGES) {
            if (lowercasePackageName.startsWith(name)) {
                return true;
            }
        }

        return false;
    }

    boolean isExcludedPackage(String packageName) {
        // Check any explicitly included package names first. This makes it easier
        // to specify "exceptions" to a general exclusion
        if (isIncludedPackage(packageName)) {
            return false;
        }

        String lowercasePackageName = packageName.toLowerCase();
        for (String name : Constants.ANDROID_EXCLUDED_PACKAGES) {
            if (lowercasePackageName.startsWith(name)) {
                return true;
            }
        }

        return false;
    }

    boolean isKotlinSDKPackage(String className) {
        return kotlinPackagePattern.matcher(className.toLowerCase()).matches();
    }

    boolean isAndroidSDKPackage(String className) {
        return (androidPackagePattern.matcher(className.toLowerCase()).matches() ||
                isKotlinSDKPackage(className));
    }

    @Override
    public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) {
        InvocationHandler handler = invocationHandlers.get(proxy);
        if (handler == null) {
            log.error("Unknown invocation type: " + proxy + ".  Arguments: " + Arrays.asList(args));
        } else {
            try {
                return handler.invoke(proxy, method, args);
            } catch (Throwable t) {
                log.error("Error:" + t.getMessage(), t);
            }
        }

        return null;
    }

    /**
     * Process the given class bytes, modifying them if necessary.
     */
    public ClassData visitClassBytes(byte[] bytes) {
        return visitClassBytesWithOptions(bytes, ClassWriter.COMPUTE_FRAMES);
    }

    ClassData visitClassBytesWithOptions(byte[] bytes, int classWriterFlags) {
        String className = "unknown";

        if (isInstrumentationDisabled()) {
            // TODO needs work
            return new ClassData(bytes, false);
        }

        try {
            ClassReader cr = new ClassReader(bytes);
            ClassWriter cw = new ClassWriterSafe(cr, classWriterFlags);

            instrumentationContext.reset();
            instrumentationContext.setComputeFlags(classWriterFlags);

            //
            // First pass to sniff out any tags on the class.
            //
            cr.accept(new PrefilterClassVisitor(instrumentationContext, log),
                    ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

            className = instrumentationContext.getClassName();

            if (!instrumentationContext.hasTag(Constants.INSTRUMENTED_CLASS_NAME)) {
                //
                // Second pass to actually do the work
                //
                ClassVisitor cv = cw;
                log.debug("[InvocationDispatcher] class [" + className + "]");

                // Exclude both the agent code and our dependant libraries
                if (className.startsWith(Constants.NR_PACKAGE_NAME)) {
                    cv = new NewRelicClassVisitor(cv, instrumentationContext, log);
                } else if (isAndroidSDKPackage(className)) {
                    // In this case, instrument activity/fragment related classes only.
                    // Don't instrument everything in the support lib (like JSON methods, etc).
                    cv = new ActivityClassVisitor(cv, instrumentationContext, log);
                } else if (isExcludedPackage(className)) {
                    log.debug("[InvocationDispatcher] Excluding class [" + className + "]");
                    return null;
                } else {
                    cv = new AnnotatingClassVisitor(cv, instrumentationContext, log);
                    cv = new ActivityClassVisitor(cv, instrumentationContext, log);
                    cv = new FragmentClassVisitor(cv, instrumentationContext, log);
                    cv = new AsyncTaskClassVisitor(cv, instrumentationContext, log);
                    cv = new TraceAnnotationClassVisitor(cv, instrumentationContext, log);
                    cv = new WrapMethodClassVisitor(cv, instrumentationContext, log);
                }
                cv = new ContextInitializationClassVisitor(cv, instrumentationContext);

                try {
                    /* If this flag is set, stack map frames are always visited in expanded
                     * format (this option adds a decompression/compression step in ClassReader
                     * and ClassWriter which degrades performance quite a lot).
                     */
                    int parsingOptions = ClassReader.EXPAND_FRAMES;
                    if ((instrumentationContext.getComputeFlags() & ClassWriter.COMPUTE_FRAMES) == 0) {
                        // If ASM can't compute frame skip them and inject them ourselves
                        parsingOptions = ClassReader.SKIP_FRAMES;
                    }
                    cr.accept(cv, parsingOptions);
                } catch (Exception e) {
                    if (classWriterFlags != ClassWriter.COMPUTE_MAXS) {
                        log.debug("[InvocationDispatcher] [" + className + "] " + e);
                        log.debug("[InvocationDispatcher] Retry with ClassWriter.COMPUTE_MAXS");
                        return visitClassBytesWithOptions(bytes, ClassWriter.COMPUTE_MAXS);
                    }
                    log.warning("[InvocationDispatcher] [" + className + "] instrumentation failed: " + e.getLocalizedMessage());
                    return new ClassData(bytes, false);
                }

                if (instrumentationContext.isClassModified() && (bytes.length != cw.toByteArray().length)) {
                    log.debug("[InvocationDispatcher] class[" + className + "] bytes[" + bytes.length + "] transformed[" + cw.toByteArray().length + "]");
                }

            } else {
                log.debug(MessageFormat.format("[{0}] class is already instrumented! skipping ...", instrumentationContext.getFriendlyClassName()));
            }

            return instrumentationContext.newClassData(cw.toByteArray());

        } catch (SkipException e) {
            log.debug("[InvocationDispatcher] " + e);
            return new ClassData(bytes, false);

        } catch (IllegalArgumentException e) {
            log.warning("[InvocationDispatcher] Class[" + className + "] ignored: JDK not supported");
            return new ClassData(bytes, false);

        } catch (HaltBuildException e) {
            log.debug("[InvocationDispatcher] " + e);
            throw new RuntimeException(e);

        } catch (NoClassDefFoundError e) {
            log.warning("Unfortunately, an error has occurred while processing class [" + className + "].\n"
                    + "The file is unable to be parsed. Please copy your build logs and the jar containing "
                    + "this class and visit http://support.newrelic.com, thanks!\n" + e.getLocalizedMessage());
            return new ClassData(bytes, false);

        } catch (Throwable t) {
            // Catching exceptions here is generally a good idea as ASM will crash occasionally while parsing weird jars.
            // However, this will mask bugs from the user and skip instrumentation of the class.  We'll warn the user in
            // hopes they'll report the issue to us.
            log.error("Unfortunately, an error has occurred while processing class [" + className + "].\n" +
                    "Please copy your build logs and the jar containing this class and " +
                    "visit http://support.newrelic.com, thanks!\n" + t.getMessage(), t);
            log.debug("Throwable type[" + t + "].\n");

            if (t.getMessage() != null) {
                log.debug("  message[" + t.getMessage() + "].\n");
            }

            if (t.getCause() != null) {
                log.debug("  cause[" + t.getCause() + "].\n");
            }

            return new ClassData(bytes, false);
        }
    }

    public InstrumentationContext getInstrumentationContext() {
        return instrumentationContext;
    }

}
