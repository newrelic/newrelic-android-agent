/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.compile;

import com.newrelic.agent.util.BuildId;

import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class InstrumentationContext {

    private final ClassRemapperConfig config;
    private final Logger log;
    private boolean classModified;
    private String className;
    private String superClassName;
    private final ArrayList<String> tags = new ArrayList<>();
    private final HashMap<String, String> tracedMethods;
    private final HashMap<String, String> skippedMethods;
    private final HashMap<String, ArrayList<String>> tracedMethodParameters = new HashMap<>();
    private String variantName;
    private int computeFlags;

    public InstrumentationContext(ClassRemapperConfig config, final Logger log) {
        this.config = config;
        this.log = log;
        this.tracedMethods = new HashMap<>();
        this.skippedMethods = new HashMap<>();
        this.variantName = BuildId.DEFAULT_VARIANT;
        this.computeFlags = 0;
    }

    public Logger getLog() {
        return log;
    }

    public void reset() {
        classModified = false;
        className = "";
        superClassName = "";
        tags.clear();
        computeFlags = 0;
    }

    public void markModified() {
        classModified = true;
    }

    public boolean isClassModified() {
        return classModified;
    }

    public void addTag(final String tag) {
        tags.add(tag);
    }

    public void addUniqueTag(final String tag) {
        while (tags.remove(tag)) {
        }
        addTag(tag);
    }

    public void addTracedMethod(String name, String desc) {
        log.debug("Will trace method [" + className + "#" + name + ":" + desc + "] as requested");
        tracedMethods.put(className + "#" + name, desc);
    }

    public void addSkippedMethod(String name, String desc) {
        log.debug("Will skip all tracing in method [" + className + "#" + name + ":" + desc + "] as requested");
        skippedMethods.put(className + "#" + name, desc);
    }

    public void addTracedMethodParameter(String methodName, String parameterName, String parameterClass, String parameterValue) {
        log.debug("Adding traced method parameter [" + parameterName + "] for method [" + methodName + "]");

        String name = className + "#" + methodName;
        if (!tracedMethodParameters.containsKey(name)) {
            tracedMethodParameters.put(name, new ArrayList<>());
        }
        ArrayList<String> methodParameters = tracedMethodParameters.get(name);
        methodParameters.add(parameterName);
        methodParameters.add(parameterClass);
        methodParameters.add(parameterValue);
    }

    public ArrayList<String> getTracedMethodParameters(String methodName) {
        return tracedMethodParameters.get(className + "#" + methodName);
    }

    public boolean isTracedMethod(String name, String desc) {
        return searchMethodMap(tracedMethods, name, desc);
    }

    public boolean isSkippedMethod(String name, String desc) {
        return searchMethodMap(skippedMethods, name, desc);
    }

    private boolean searchMethodMap(Map<String, String> map, String name, String desc) {
        final String descToMatch = map.get(className + "#" + name);

        if (descToMatch == null) {
            return false;
        }
        return desc.equals(descToMatch);
    }

    public List<String> getTags() {
        return tags;
    }

    public boolean hasTag(final String tag) {
        return tags.contains(tag);
    }

    public void setClassName(final String className) {
        this.className = (className == null ? "" : className);
    }

    public String getClassName() {
        return (className == null ? "" : className);
    }

    public String getFriendlyClassName() {
        return className.replaceAll("/", ".");
    }

    public String getFriendlySuperClassName() {
        return superClassName.replaceAll("/", ".");
    }

    public String getSimpleClassName() {
        if (className.contains("/")) {
            return className.substring(className.lastIndexOf("/") + 1);
        } else {
            return className;
        }
    }

    public void setSuperClassName(final String superClassName) {
        this.superClassName = (superClassName == null ? "" : superClassName);
    }

    public String getSuperClassName() {
        return (superClassName == null ? "" : superClassName);
    }

    public ClassData newClassData(final byte[] mainClassBytes) {
        return new ClassData(mainClassBytes, isClassModified());
    }

    public ClassMethod getMethodWrapper(final ClassMethod method) {
        return config.getMethodWrapper(method);
    }

    public Collection<ClassMethod> getCallSiteReplacements(final String className, final String methodName, final String methodDesc) {
        return config.getCallSiteReplacements(className, methodName, methodDesc);
    }

    public Collection<ClassMethod> getShadowMethods(final String className, final String methodName, final String methodDesc) {
        return config.getShadowMethods(className, methodName, methodDesc);
    }

    /**
     * Returns the variant [debug, release, ...] of this instrumentation context
     *
     * @return variant name
     */
    public String getVariantName() {
        return (variantName == null ? BuildId.DEFAULT_VARIANT : variantName);
    }

    /**
     * Sets the variant [debug, release, ...] of this instrumentation context
     */
    public void setVariantName(String variantName) {
        if (!(variantName == null || variantName.isEmpty())) {
            this.variantName = variantName.toLowerCase();
        }
    }

    /**
     * Sets the ClassWriter frame computation flags used during instrumentation
     */
    public void setComputeFlags(int computeFlags) {
        this.computeFlags = computeFlags;
    }

    /**
     * Gets the ClassWriter frame computation flags used during instrumentation
     */
    public int getComputeFlags() {
        return computeFlags;
    }
}
