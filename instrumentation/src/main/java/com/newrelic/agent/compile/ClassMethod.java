/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.compile;


import org.objectweb.asm.commons.Method;

public final class ClassMethod {
    private final String className;
    private final String methodName;
    private final String methodDesc;

    public ClassMethod(String className, String methodName, String methodDesc) {
        super();
        this.className = className;
        this.methodName = methodName;
        this.methodDesc = methodDesc;
    }

    static ClassMethod getClassMethod(String signature) {
        try {
            int descIndex = signature.lastIndexOf('(');
            String methodDesc;
            if (descIndex == -1) {
                descIndex = signature.length();
                methodDesc = "";
            } else {
                methodDesc = signature.substring(descIndex);
            }
            String beforeMethodDesc = signature.substring(0, descIndex);
            int methodIndex = beforeMethodDesc.lastIndexOf('.');

            return new ClassMethod(signature.substring(0, methodIndex), signature.substring(methodIndex + 1, descIndex), methodDesc);
        } catch (Exception ex) {
            throw new RuntimeException("Error parsing " + signature, ex);
        }
    }

    Method getMethod() {
        return new Method(methodName, methodDesc);
    }

    public String getClassName() {
        return className;
    }

    public String getMethodName() {
        return methodName;
    }

    public String getMethodDesc() {
        return methodDesc;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result
                + ((className == null) ? 0 : className.hashCode());
        result = prime * result
                + ((methodDesc == null) ? 0 : methodDesc.hashCode());
        result = prime * result
                + ((methodName == null) ? 0 : methodName.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj == null) {
            return false;
        }

        if (getClass() != obj.getClass()) {
            return false;
        }

        ClassMethod other = (ClassMethod) obj;

        if (className == null) {
            if (other.className != null) {
                return false;
            }
        } else if (!className.equals(other.className)) {
            return false;
        }

        if (methodDesc == null) {
            if (other.methodDesc != null) {
                return false;
            }
        } else if (!methodDesc.equals(other.methodDesc)) {
            return false;
        }

        if (methodName == null) {
            return other.methodName == null;
        } else if (!methodName.equals(other.methodName)) {
            return false;
        }

        return true;
    }

    @Override
    public String toString() {
        return className + '.' + methodName + methodDesc;
    }


}
