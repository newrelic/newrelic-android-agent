package com.newrelic.agent.compile;

import com.newrelic.agent.compile.transformers.NewRelicClassTransformer;
import com.newrelic.agent.compile.visitor.ActivityClassVisitor;
import com.newrelic.agent.compile.visitor.AnnotatingClassVisitor;
import com.newrelic.agent.compile.visitor.Annotations;
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

import java.io.File;
import java.lang.reflect.InvocationHandler;
import java.net.URISyntaxException;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * The bytecode we inject (often) can't directly invoke methods on our classes because of
 * classloader visibility, so this InvocationHandler is used to allow our injected code to
 * invoke a number of different handlers. The proxy
 * argument of the invoke call is always a string that indicates what handler should execute.  This
 * allows us to program at a higher level instead of writing everything in bytecode.
 *
 */
public class InvocationDispatcher implements InvocationHandler {

    public static final Class INVOCATION_DISPATCHER_CLASS = Logger.class;
    public static final String INVOCATION_DISPATCHER_FIELD_NAME = "treeLock";

    /**
     * Variants of the dexer script name.
     */
    public static final Set<String> DX_COMMAND_NAMES = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList("dx", "dx.bat")));

    /**
     * Variants of the java executable name.
     */
    public static final Set<String> JAVA_NAMES = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList("java", "java.exe")));

    /**
     * The New Relic android agent jar names.
     */
    private static final Set<String> AGENT_JAR_NAMES = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList("newrelic.android.fat.jar", "newrelic.android.jar", "obfuscated.jar")));

    /**
     * This includes those package names we *expressly* should not instrument
     */
    public static final HashSet<String> EXCLUDED_PACKAGES = new HashSet<String>() {{
        // agent exclusions
        add("com/newrelic/agent/android");
        add("com/newrelic/mobile");
        add("com/newrelic/com");

        // 3rd party exclusions
        add("com/google/firebase/perf/network");    // Firebase network instrumentation
        add("com/here/sdk/hacwrapper");             // HERE sdk
    }};

    /**
     * This includes those package names we are very interested in
     */
    public static final HashSet<String> INCLUDED_PACKAGES = new HashSet<String>() {{
        add("androidx/appcompat/app/AppCompatActivity");
        add("androidx/core/app/ActivityCompat");
        add("androidx/fragment/app/");
        add("androidx/fragment/app/Fragment");
        add("androidx/fragment/app/FragmentActivity");
        add("androidx/leanback/app/Fragment");
        add("androidx/legacy/app/ActivityCompat");
        add("androidx/legacy/app/FragmentCompat");
        add("androidx/preference/Fragment");
        add("androidx/sqlite/");
        add("com/google/gson/");
        add("org/json/");
    }};

    private final Log log;
    private final ClassRemapperConfig config;
    private final InstrumentationContext instrumentationContext;
    final Map<String, InvocationHandler> invocationHandlers;
    private boolean writeDisabledMessage = true;
    private final String agentJarPath;
    private boolean disableInstrumentation = false;
    private Pattern androidPackagePattern = Pattern.compile(NewRelicClassTransformer.ANDROID_PACKAGE_RE);
    private Pattern kotlinPackagePattern = Pattern.compile(NewRelicClassTransformer.ANDROID_KOTLIN_PACKAGE_RE);

    @SuppressWarnings("serial")
    public InvocationDispatcher(final Log log) throws ClassNotFoundException, URISyntaxException {

        this.log = log;
        this.config = new ClassRemapperConfig(log);
        this.instrumentationContext = new InstrumentationContext(config, log);
        this.agentJarPath = RewriterAgent.getAgentJarPath();

        invocationHandlers = Collections.unmodifiableMap(
                new HashMap<String, InvocationHandler>() {
                    {

                        /**
                         * Dexer main/processClass invocation
                         */
                        String proxyInvocationKey = RewriterAgent.getProxyInvocationKey(
                                NewRelicClassTransformer.DEXER_CLASS_NAME,
                                NewRelicClassTransformer.DEXER_METHOD_NAME);

                        put(proxyInvocationKey, new InvocationHandler() {
                            /**
                             * Method signature: processClass(String name, byte[] bytes)
                             * args[0] is the file name.  args[1] is the byte array
                             */
                            @Override
                            public Object invoke(Object proxy,
                                                 java.lang.reflect.Method method,
                                                 Object[] args) throws Throwable {
                                String filename = (String) args[0];
                                byte[] bytes = (byte[]) args[1];

                                log.debug("dexer/main/processClass arg[0](filename)[" + filename + "] arg[1](bytes)[" + bytes.length + "]");
                                /*
                                log.debug("dexer/main/processClass proxy[" + proxy + "]");
                                log.debug("dexer/main/processClass method[" + method + "]");
                                log.debug("dexer/main/processClass args[" + args + "]");
                                /**/

                                if (isInstrumentationDisabled()) {
                                    if (writeDisabledMessage) {
                                        writeDisabledMessage = false;
                                        log.info("Instrumentation disabled, no agent present");
                                    }
                                    return bytes;
                                }

                                writeDisabledMessage = true;

                                synchronized (instrumentationContext) {
                                    ClassData classData = visitClassBytes(bytes);
                                    if (classData != null && classData.getMainClassBytes() != null && classData.isModified()) {
                                        log.debug("dexer/main/processClass transformed bytes[" + bytes.length + "]");
                                        return classData.getMainClassBytes();
                                    }
                                }

                                return bytes;
                            }

                        });

                        proxyInvocationKey = RewriterAgent.getProxyInvocationKey(
                                NewRelicClassTransformer.ANT_DEX_CLASS_NAME,
                                NewRelicClassTransformer.ANT_DEX_METHOD_NAME);

                        put(proxyInvocationKey, new InvocationHandler() {
                            @Override
                            public Object invoke(Object proxy,
                                                 java.lang.reflect.Method method,
                                                 Object[] args) throws Throwable {
                                @SuppressWarnings("unchecked")
                                List<File> files = (List<File>) args[0];

                                for (File file : files) {
                                    if (AGENT_JAR_NAMES.contains(file.getName().toLowerCase())) {
                                        log.info("Detected the New Relic Android agent in an Ant build (" + file.getPath() + ")");
                                        return file;
                                    }
                                }
                                log.debug("Ant " + NewRelicClassTransformer.ANT_DEX_METHOD_NAME + ": " + files);
                                log.info("No New Relic agent detected in Ant build");
                                return null;
                            }

                        });

                        /**
                         * This is a funny one.  We call this with null args to clear the disabled flag (set it to false).
                         * We also call it with an args array of one item, and if that one item is null we set the
                         * disabled flag to true.
                         */
                        put(RewriterAgent.SET_INSTRUMENTATION_DISABLED_FLAG, new InvocationHandler() {
                            @Override
                            public Object invoke(Object proxy,
                                                 java.lang.reflect.Method method,
                                                 Object[] args) throws Throwable {
                                disableInstrumentation = args != null && args[0] == null;

                                log.debug("DisableInstrumentation: " + disableInstrumentation + " (" + args + ")");
                                return null;
                            }
                        });

                        put(RewriterAgent.PRINT_TO_INFO_LOG, new InvocationHandler() {
                            @Override
                            public Object invoke(Object proxy,
                                                 java.lang.reflect.Method method, Object[] args)
                                    throws Throwable {
                                log.info(args[0].toString());
                                return null;
                            }
                        });

                        /**
                         * java ProcessBuilder class invocation
                         * Signature: Process start()
                         */
                        proxyInvocationKey = RewriterAgent.getProxyInvocationKey(
                                NewRelicClassTransformer.PROCESS_BUILDER_CLASS_NAME,
                                NewRelicClassTransformer.PROCESS_BUILDER_METHOD_NAME);

                        put(proxyInvocationKey, new InvocationHandler() {
                            /**
                             * Method signature: start()  ()Ljava/lang/Process;
                             * args[0] is List<Object> of strings containing full command
                             */
                            @Override
                            public Object invoke(Object proxy,
                                                 java.lang.reflect.Method method,
                                                 Object[] args) throws Throwable {

                                @SuppressWarnings("unchecked")
                                List<Object> list = (List<Object>) args[0];
                                String command = (String) list.get(0);
                                File commandFile = new File(command);

                                /*
                                log.debug("processBuilder/start command[" + command + "]");
                                log.debug("processBuilder/start proxy[" + proxy + "]");
                                log.debug("processBuilder/start method[" + method + "]");
                                log.debug("processBuilder/start arg[0][" + args[0] + "]");
                                /**/

                                if (isInstrumentationDisabled()) {
                                    log.info("Instrumentation disabled, no agent present.  Command: " + commandFile.getName());
                                    log.debug("Execute: " + list.toString());
                                    return null;
                                }

                                String javaagentString = null;
                                if (DX_COMMAND_NAMES.contains(commandFile.getName().toLowerCase())) {
                                    javaagentString = "-Jjavaagent:" + agentJarPath;
                                } else if (JAVA_NAMES.contains(commandFile.getName().toLowerCase())) {
                                    javaagentString = "-javaagent:" + agentJarPath;
                                }

                                if (javaagentString != null) {
                                    String agentArgs = RewriterAgent.getAgentArgs();
                                    if (agentArgs != null) {
                                        javaagentString += "=" + agentArgs;
                                    }
                                    list.add(1, quoteProperty(javaagentString));
                                }

                                log.debug("processBuilder/start Execute[" + list.toString() + "]");

                                return null;
                            }


                            private String quoteProperty(String string) {
                                if ((System.getProperty("os.name").toLowerCase().contains("win"))) {
                                    return "\"" + string + "\"";
                                }
                                return string;
                            }
                        });


                        proxyInvocationKey = RewriterAgent.getProxyInvocationKey(
                                NewRelicClassTransformer.NR_CLASS_REWRITER_CLASS_NAME,
                                NewRelicClassTransformer.NR_CLASS_REWRITER_METHOD_NAME);

                        put(proxyInvocationKey, new InvocationHandler() {
                            @Override
                            public Object invoke(Object proxy,
                                                 java.lang.reflect.Method method,
                                                 Object[] args) throws Throwable {
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
                                    /*
                                    log.debug("ClassTransformer/transformClassBytes arg[0](filename)[" + filename + "] arg[1](bytes)[" + bytes.length + "]");
                                    log.debug("ClassTransformer/transformClassBytes proxy[" + proxy + "]");
                                    log.debug("ClassTransformer/transformClassBytes method[" + method + "]");
                                    log.debug("ClassTransformer/transformClassBytes args[" + args + "]");
                                    /**/

                                    ClassData classData = visitClassBytes(bytes);
                                    if (instrumentationContext.isClassModified()) {
                                        if (classData != null && classData.getMainClassBytes() != null) {
                                            if ((bytes.length != classData.getMainClassBytes().length)) {
                                                log.debug("ClassTransformer/transformClassBytes transformed bytes[" + classData.getMainClassBytes().length + "]");
                                            }
                                            return classData.getMainClassBytes();
                                        }
                                    }
                                }

                                return null;
                            }

                        });

                    }
                });
    }

    boolean isInstrumentationDisabled() {
        return disableInstrumentation ||
                System.getProperty(RewriterAgent.DISABLE_INSTRUMENTATION_SYSTEM_PROPERTY) != null;
    }

    boolean isIncludedPackage(String packageName) {
        String lowercasePackageName = packageName.toLowerCase();

        for (String name : INCLUDED_PACKAGES) {
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
        for (String name : EXCLUDED_PACKAGES) {
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
    public Object invoke(Object proxy, java.lang.reflect.Method method,
                         Object[] args) throws Throwable {
        InvocationHandler handler = invocationHandlers.get(proxy);
        if (handler == null) {
            log.error("Unknown invocation type: " + proxy + ".  Arguments: " + Arrays.asList(args));
            return null;
        } else {
            try {
                return handler.invoke(proxy, method, args);
            } catch (Throwable t) {
                log.error("Error:" + t.getMessage(), t);
                return null;
            }
        }
    }

    /**
     * Process the given class bytes, modifying them if necessary.
     *
     * @param bytes
     * @return
     */
    public ClassData visitClassBytes(byte[] bytes) {
        return visitClassBytesWithOptions(bytes, ClassWriter.COMPUTE_FRAMES);
    }

    ClassData visitClassBytesWithOptions(byte[] bytes, int classWriterFlags) {
        String className = "unknown";

        if (isInstrumentationDisabled()) {
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

            if (!instrumentationContext.hasTag(Annotations.INSTRUMENTED)) {
                //
                // Second pass to actually do the work
                //
                ClassVisitor cv = cw;
                log.debug("[InvocationDispatcher] class [" + className + "]");

                // Exclude both the agent code and our dependant libraries
                if (className.startsWith(NewRelicClassTransformer.NR_PACKAGE_NAME)) {
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
