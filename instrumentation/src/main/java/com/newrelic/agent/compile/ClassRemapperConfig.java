/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.compile;

import org.slf4j.Logger;

import java.io.InputStream;
import java.net.URL;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;

public class ClassRemapperConfig {
    public static final String WRAP_METHOD_IDENTIFIER = "WRAP_METHOD:";
    public static final String REPLACE_CALL_SITE_IDENTIFIER = "REPLACE_CALL_SITE:";
    public static final String SHADOW_METHOD_IDENTIFIER = "SHADOW_METHOD:";

    private final Map<ClassMethod, ClassMethod> methodWrappers;
    private final Map<String, Collection<ClassMethod>> callSiteReplacements;
    private final Map<String, Collection<ClassMethod>> shadowMethods;
    private boolean logInstrumentationEnabled = true;

    public ClassRemapperConfig(final Logger log,boolean logInstrumentationEnabled) throws ClassNotFoundException {
        this.logInstrumentationEnabled = logInstrumentationEnabled;
        @SuppressWarnings("unchecked") final Map<String, String> remappings = getRemappings(log);
        // remove log mappings if log instrumentation is disabled
        removeLogCallMappings(logInstrumentationEnabled, remappings);
        methodWrappers = getMethodWrappers(remappings, log);
        callSiteReplacements = getCallSiteReplacements(remappings, log);
        shadowMethods = getShadowMethods(remappings, log);

        log.info("[ClassRemapperConfig] Initialized with logInstrumentationEnabled[{}]", logInstrumentationEnabled);
    }

    private static void removeLogCallMappings(boolean logInstrumentationEnabled, Map<String, String> remappings) {
        if (!logInstrumentationEnabled) {
            remappings.entrySet().removeIf(entry -> entry.getKey().contains("android/util/Log"));
        }
    }

    public ClassMethod getMethodWrapper(final ClassMethod method) {
        return methodWrappers.get(method);
    }

    public Collection<ClassMethod> getCallSiteReplacements(final String className, final String methodName, final String methodDesc) {
        ArrayList<ClassMethod> methods = new ArrayList<ClassMethod>();

        // There are two ways to match a method: first, using only its name and description.  This is the wider, more
        // leaky way of applying instrumentation.  The second way is to match on the class, method name, and description.
        // This is a very specific way of matching that, unfortunately, excludes super and subclass implementations of
        // the method.

        Collection<ClassMethod> matches = callSiteReplacements.get(MessageFormat.format("{0}:{1}", methodName, methodDesc));
        if (matches != null) {
            methods.addAll(matches);
        }
        matches = callSiteReplacements.get(MessageFormat.format("{0}.{1}:{2}", className, methodName, methodDesc));
        if (matches != null) {
            methods.addAll(matches);
        }

        return methods;
    }

    public Collection<ClassMethod> getShadowMethods(final String className, final String methodName, final String methodDesc) {
        ArrayList<ClassMethod> methods = new ArrayList<ClassMethod>();

        // There are two ways to match a method: first, using only its name and description.  This is the wider, more
        // leaky way of applying instrumentation.  The second way is to match on the class, method name, and description.
        // This is a very specific way of matching that, unfortunately, excludes super and subclass implementations of
        // the method.

        Collection<ClassMethod> matches = shadowMethods.get(MessageFormat.format("{0}:{1}", methodName, methodDesc));
        if (matches != null) {
            methods.addAll(matches);
        }
        matches = shadowMethods.get(MessageFormat.format("{0}.{1}:{2}", className, methodName, methodDesc));
        if (matches != null) {
            methods.addAll(matches);
        }

        return methods;
    }

    /*
     * Return a map of the method calls whose return value we want to wrap.
     * @throws ClassNotFoundException
     */
    private static Map<ClassMethod, ClassMethod> getMethodWrappers(Map<String, String> remappings, Logger log) throws ClassNotFoundException {
        HashMap<ClassMethod, ClassMethod> methodWrappers = new HashMap<ClassMethod, ClassMethod>();
        for (Entry<String, String> entry : remappings.entrySet()) {
            if (entry.getKey().startsWith(WRAP_METHOD_IDENTIFIER)) {
                String originalSig = entry.getKey().substring(WRAP_METHOD_IDENTIFIER.length());
                ClassMethod origClassMethod = ClassMethod.getClassMethod(originalSig);
                ClassMethod wrappingMethod = ClassMethod.getClassMethod(entry.getValue());

                methodWrappers.put(origClassMethod, wrappingMethod);
            }
        }
        return methodWrappers;
    }

    private static Map<String, Collection<ClassMethod>> getCallSiteReplacements(Map<String, String> remappings, Logger log) throws ClassNotFoundException {
        final HashMap<String, Set<ClassMethod>> temp = new HashMap<String, Set<ClassMethod>>();
        for (Entry<String, String> entry : remappings.entrySet()) {
            if (entry.getKey().startsWith(REPLACE_CALL_SITE_IDENTIFIER)) {
                String originalSig = entry.getKey().substring(REPLACE_CALL_SITE_IDENTIFIER.length());
                // If the signature contains a period, we know the instrumentation was scoped to a specific class.  Thus,
                // when constructing the key for this hash entry, we'll include the class name.  Otherwise, the key only
                // contains the method name and description.
                if (originalSig.contains(".")) {
                    ClassMethod origClassMethod = ClassMethod.getClassMethod(originalSig);
                    ClassMethod replacement = ClassMethod.getClassMethod(entry.getValue());

                    final String key = MessageFormat.format("{0}.{1}:{2}", origClassMethod.getClassName(), origClassMethod.getMethodName(), origClassMethod.getMethodDesc());

                    Set<ClassMethod> replacements = temp.get(key);
                    if (replacements == null) {
                        replacements = new HashSet<ClassMethod>();
                        temp.put(key, replacements);
                    }
                    replacements.add(replacement);
                } else {
                    final String[] nameDesc = originalSig.split(":");

                    final int paren = originalSig.indexOf("(");
                    final String methodName = originalSig.substring(0, paren);
                    final String methodDesc = originalSig.substring(paren);

                    final String key = MessageFormat.format("{0}:{1}", methodName, methodDesc);
                    ClassMethod replacement = ClassMethod.getClassMethod(entry.getValue());

                    Set<ClassMethod> replacements = temp.get(key);
                    if (replacements == null) {
                        replacements = new HashSet<ClassMethod>();
                        temp.put(key, replacements);
                    }
                    replacements.add(replacement);
                }
            }
        }

        final HashMap<String, Collection<ClassMethod>> callSiteReplacements = new HashMap<String, Collection<ClassMethod>>();

        for (Map.Entry<String, Set<ClassMethod>> entry : temp.entrySet()) {
            callSiteReplacements.put(entry.getKey(), entry.getValue());
        }
        return callSiteReplacements;
    }

    /*
     * Return a map of the call sites that we wish to replace.
     * @throws ClassNotFoundException
     */
    private static Map<String, Collection<ClassMethod>> parseCallerRepl(Map<String, String> remappings, Logger log) throws ClassNotFoundException {
        final HashMap<String, Set<ClassMethod>> temp = new HashMap<String, Set<ClassMethod>>();
        for (Entry<String, String> entry : remappings.entrySet()) {
            if (entry.getKey().startsWith(REPLACE_CALL_SITE_IDENTIFIER)) {
                String originalSig = entry.getKey().substring(REPLACE_CALL_SITE_IDENTIFIER.length());
                // If the signature contains a period, we know the instrumentation was scoped to a specific class.  Thus,
                // when constructing the key for this hash entry, we'll include the class name.  Otherwise, the key only
                // contains the method name and description.
                if (originalSig.contains(".")) {
                    ClassMethod origClassMethod = ClassMethod.getClassMethod(originalSig);
                    ClassMethod replacement = ClassMethod.getClassMethod(entry.getValue());

                    final String key = MessageFormat.format("{0}.{1}:{2}", origClassMethod.getClassName(), origClassMethod.getMethodName(), origClassMethod.getMethodDesc());

                    Set<ClassMethod> replacements = temp.get(key);
                    if (replacements == null) {
                        replacements = new HashSet<ClassMethod>();
                        temp.put(key, replacements);
                    }
                    replacements.add(replacement);
                } else {
                    final String[] nameDesc = originalSig.split(":");

                    final int paren = originalSig.indexOf("(");
                    final String methodName = originalSig.substring(0, paren);
                    final String methodDesc = originalSig.substring(paren);

                    final String key = MessageFormat.format("{0}:{1}", methodName, methodDesc);
                    ClassMethod replacement = ClassMethod.getClassMethod(entry.getValue());

                    Set<ClassMethod> replacements = temp.get(key);
                    if (replacements == null) {
                        replacements = new HashSet<ClassMethod>();
                        temp.put(key, replacements);
                    }
                    replacements.add(replacement);
                }
            }
        }

        final HashMap<String, Collection<ClassMethod>> callSiteReplacements = new HashMap<String, Collection<ClassMethod>>();

        for (Map.Entry<String, Set<ClassMethod>> entry : temp.entrySet()) {
            callSiteReplacements.put(entry.getKey(), entry.getValue());
        }
        return callSiteReplacements;
    }

    /*
     * Return a map of the call sites that we wish to replace.
     * @throws ClassNotFoundException
     */
    private static Map<String, Collection<ClassMethod>> getShadowMethods(Map<String, String> remappings, Logger log) throws ClassNotFoundException {
        final HashMap<String, Set<ClassMethod>> temp = new HashMap<String, Set<ClassMethod>>();
        for (Entry<String, String> entry : remappings.entrySet()) {
            if (entry.getKey().startsWith(SHADOW_METHOD_IDENTIFIER)) {
                String originalSig = entry.getKey().substring(SHADOW_METHOD_IDENTIFIER.length());
                // If the signature contains a period, we know the instrumentation was scoped to a specific class.  Thus,
                // when constructing the key for this hash entry, we'll include the class name.  Otherwise, the key only
                // contains the method name and description.
                if (originalSig.contains(".")) {
                    ClassMethod origClassMethod = ClassMethod.getClassMethod(originalSig);
                    ClassMethod replacement = ClassMethod.getClassMethod(entry.getValue());

                    final String key = MessageFormat.format("{0}.{1}:{2}", origClassMethod.getClassName(), origClassMethod.getMethodName(), origClassMethod.getMethodDesc());

                    Set<ClassMethod> replacements = temp.get(key);
                    if (replacements == null) {
                        replacements = new HashSet<ClassMethod>();
                        temp.put(key, replacements);
                    }
                    replacements.add(replacement);
                } else {
                    final String[] nameDesc = originalSig.split(":");

                    final int paren = originalSig.indexOf("(");
                    final String methodName = originalSig.substring(0, paren);
                    final String methodDesc = originalSig.substring(paren);

                    final String key = MessageFormat.format("{0}:{1}", methodName, methodDesc);
                    ClassMethod replacement = ClassMethod.getClassMethod(entry.getValue());

                    Set<ClassMethod> replacements = temp.get(key);
                    if (replacements == null) {
                        replacements = new HashSet<ClassMethod>();
                        temp.put(key, replacements);
                    }
                    replacements.add(replacement);
                }
            }
        }

        final HashMap<String, Collection<ClassMethod>> callSiteReplacements = new HashMap<String, Collection<ClassMethod>>();

        for (Map.Entry<String, Set<ClassMethod>> entry : temp.entrySet()) {
            callSiteReplacements.put(entry.getKey(), entry.getValue());
        }
        return callSiteReplacements;
    }

    /*
     * Return the map of class/method modifications from the type_map.properties file.
     */
    @SuppressWarnings("rawtypes")
    private static Map getRemappings(final Logger log) {
        Properties props = new Properties();
        URL resource = ClassRemapperConfig.class.getResource("/type_map.properties");

        if (resource == null) {
            log.error("Unable to find the type map");
            System.exit(1);
        }

        try (InputStream in = resource.openStream()) {
            props.load(in);

        } catch (Throwable ex) {
            log.error("Unable to read the type map", ex);
            System.exit(1);
        }

        return props;
    }

}
