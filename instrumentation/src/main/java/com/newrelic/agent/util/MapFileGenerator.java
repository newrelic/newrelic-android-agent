/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.util;

import com.newrelic.agent.android.instrumentation.ReplaceCallSite;
import com.newrelic.agent.android.instrumentation.ShadowMethod;
import com.newrelic.agent.android.instrumentation.WrapReturn;
import com.newrelic.agent.compile.ClassRemapperConfig;

import org.objectweb.asm.Type;
import org.reflections.util.ClasspathHelper;

import java.io.FileOutputStream;
import java.net.URL;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

public class MapFileGenerator {

    private static final int KNOWN_MAP_SIZE = 101;

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage:   MapFileGenerator class_dir");
            System.exit(1);
        }
        try {
            Class.forName("com.newrelic.agent.android.Agent");
        } catch (Exception ex) {
            System.err.println("Unable to load agent classes");
            System.exit(1);
        }
        Map<String, String> remapperProperties = getRemapperProperties();
        if (remapperProperties.size() == 0) {
            System.err.println("No class mappings were found");
            System.exit(1);
        }
        for (Map.Entry<String, String> entry : remapperProperties.entrySet()) {
            System.out.println(entry.getKey() + " = " + entry.getValue());
        }
        Properties props = new Properties();
        props.putAll(remapperProperties);
        try {
            Set<URL> urls = ClasspathHelper.forPackage("com.newrelic.agent");
            if (props.size() != KNOWN_MAP_SIZE) {
                System.err.println("Classpath: " + urls);
                for (Map.Entry<Object, Object> entry : props.entrySet()) {
                    System.err.println(entry.getKey() + "[" + entry.getValue() + "]");
                }
                Exception e = new RuntimeException("Generated " + props.size() + "  of " + KNOWN_MAP_SIZE + " expected elements from classpath [" + urls + "]");
                System.err.println(e);
                throw e;
            }
            System.out.println("Storing mapping data to " + args[0]);
            FileOutputStream out = new FileOutputStream(args[0]);
            props.store(out, "");
            out.close();
        } catch (Exception ex) {
            ex.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Return a map of original class name to new class name by finding all of the classes with
     * the {@link ReplaceCallSite}, {@link WrapReturn} and {@link ShadowMethod} annotations.
     *
     * @return
     */
    static Map<String, String> getRemapperProperties() {
        Map<String, String> classMap = new HashMap<String, String>();

        Set<URL> urls = ClasspathHelper.forPackage("com.newrelic.agent");

        System.out.println("Classpath URLS: " + urls);

        Collection<MethodAnnotation> wrapReturnAnnotations = Annotations.getMethodAnnotations(WrapReturn.class, "com/newrelic/agent", urls);
        for (MethodAnnotation annotation : wrapReturnAnnotations) {
            String originalClassName = (String) annotation.getAttributes().get("className");
            String originalMethodName = (String) annotation.getAttributes().get("methodName");
            String originalMethodDesc = (String) annotation.getAttributes().get("methodDesc");

            String newClassName = annotation.getClassName();
            String newMethodName = annotation.getMethodName();

            classMap.put(ClassRemapperConfig.WRAP_METHOD_IDENTIFIER + originalClassName.replace('.', '/') + '.' + originalMethodName + originalMethodDesc,
                    newClassName + '.' + newMethodName + annotation.getMethodDesc());
        }

        Collection<MethodAnnotation> callSiteAnnotations = Annotations.getMethodAnnotations(ReplaceCallSite.class, "com/newrelic/agent", urls);
        for (MethodAnnotation annotation : callSiteAnnotations) {
            Boolean isStatic = (Boolean) annotation.getAttributes().get("isStatic");
            String scope = (String) annotation.getAttributes().get("scope");

            if (isStatic == null) isStatic = Boolean.FALSE;

            String originalMethodName = annotation.getMethodName();
            String originalMethodDesc = annotation.getMethodDesc();

            //
            // Strip out `this` argument unless we're instrumenting a static method.
            //
            if (!isStatic) {
                final Type[] argTypes = Type.getArgumentTypes(originalMethodDesc);
                final Type[] newArgTypes = new Type[argTypes.length - 1];
                for (int i = 0; i < newArgTypes.length; i++) {
                    newArgTypes[i] = argTypes[i + 1];
                }
                final Type returnType = Type.getReturnType(originalMethodDesc);
                originalMethodDesc = Type.getMethodDescriptor(returnType, newArgTypes);
            }

            String newClassName = annotation.getClassName();
            String newMethodName = annotation.getMethodName();

            // Since we're not able to determine the full class hierarchy at compile time, we don't include the class name
            // when indicating we want to instrument a method.  This works well when methods have unique names and signatures.
            // However, some methods have very generic names and signatures (like object.toString()) and instrumentation of
            // such methods would result in instrumentation leak.  Thus, the @ReplaceCallSite annotation supports a scope
            // argument.  The purpose of scope is to limit instrumentation to a specific class at the cost of not
            // instrumenting sub or super classes.
            if (scope == null) {
                classMap.put(ClassRemapperConfig.REPLACE_CALL_SITE_IDENTIFIER + originalMethodName + originalMethodDesc,
                        newClassName + '.' + newMethodName + annotation.getMethodDesc());
            } else {
                classMap.put(ClassRemapperConfig.REPLACE_CALL_SITE_IDENTIFIER + scope.replace('.', '/') + "." + originalMethodName + originalMethodDesc,
                        newClassName + '.' + newMethodName + annotation.getMethodDesc());
            }
        }

        Collection<MethodAnnotation> shadowAnnotation = Annotations.getMethodAnnotations(ShadowMethod.class, "com/newrelic/agent", urls);
        for (MethodAnnotation annotation : shadowAnnotation) {
            Boolean isStatic = (Boolean) annotation.getAttributes().get("isStatic");
            String scope = (String) annotation.getAttributes().get("scope");

            if (isStatic == null) isStatic = Boolean.FALSE;

            String originalMethodName = annotation.getMethodName();
            String originalMethodDesc = annotation.getMethodDesc();

            //
            // Strip out `this` argument unless we're instrumenting a static method.
            //
            if (!isStatic) {
                final Type[] argTypes = Type.getArgumentTypes(originalMethodDesc);
                final Type[] newArgTypes = new Type[argTypes.length - 1];
                for (int i = 0; i < newArgTypes.length; i++) {
                    newArgTypes[i] = argTypes[i + 1];
                }
                final Type returnType = Type.getReturnType(originalMethodDesc);
                originalMethodDesc = Type.getMethodDescriptor(returnType, newArgTypes);
            }

            String newClassName = annotation.getClassName();
            String newMethodName = annotation.getMethodName();

            // Since we're not able to determine the full class hierarchy at compile time, we don't include the class name
            // when indicating we want to instrument a method.  This works well when methods have unique names and signatures.
            // However, some methods have very generic names and signatures (like object.toString()) and instrumentation of
            // such methods would result in instrumentation leak.  Thus, the @ReplaceCallSite annotation supports a scope
            // argument.  The purpose of scope is to limit instrumentation to a specific class at the cost of not
            // instrumenting sub or super classes.
            if (scope == null) {
                classMap.put(ClassRemapperConfig.SHADOW_METHOD_IDENTIFIER + originalMethodName + originalMethodDesc,
                        newClassName + '.' + newMethodName + annotation.getMethodDesc());
            } else {
                classMap.put(ClassRemapperConfig.SHADOW_METHOD_IDENTIFIER + scope.replace('.', '/') + "." + originalMethodName + originalMethodDesc,
                        newClassName + '.' + newMethodName + annotation.getMethodDesc());
            }
        }


        /* MOBILE-6254 @TraceConstructor applied to JSONObject/JSONArray crashes during Dexing
        Collection<MethodAnnotation> constructorAnnotations = Annotations.getMethodAnnotations(TraceConstructor.class, "com/newrelic/agent", urls);
        for (MethodAnnotation annotation : constructorAnnotations) {
            // The TraceConstructor annotation functions similarly to ReplaceCallSite.  However, we simply replace the
            // method name with <init> to match properly later.  Note that these methods cannot contain any code as it's
            // currently not possible to actually replace the constructor with a shim.
            final int typeStart = annotation.getMethodDesc().indexOf(")L");
            final int typeEnd = annotation.getMethodDesc().lastIndexOf(";");
            System.out.print("Start: " + typeStart + " end: " + typeEnd + " for " + annotation.getMethodDesc());
            String originalClassName = annotation.getMethodDesc().substring(typeStart + 2, typeEnd);

            String originalMethodDesc = annotation.getMethodDesc().substring(0, typeStart + 1) + "V";
            String newClassName = annotation.getClassName();
            String newMethodName = annotation.getMethodName();

            classMap.put(ClassRemapperConfig.REPLACE_CALL_SITE_IDENTIFIER + originalClassName.replace('.', '/') + "." + "<init>" + originalMethodDesc,
                    newClassName + '.' + newMethodName + annotation.getMethodDesc());
        }
        */

        return classMap;
    }
}
