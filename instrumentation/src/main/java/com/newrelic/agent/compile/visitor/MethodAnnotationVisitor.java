/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.compile.visitor;

import com.newrelic.agent.util.AnnotationImpl;
import com.newrelic.agent.util.MethodAnnotation;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.ArrayList;
import java.util.Collection;

public class MethodAnnotationVisitor {
    public static Collection<MethodAnnotation> getAnnotations(ClassReader cr, String annotationDescription) {
        MethodAnnotationClassVisitor visitor = new MethodAnnotationClassVisitor(annotationDescription);
        cr.accept(visitor, 0);
        return visitor.getAnnotations();
    }

    private static class MethodAnnotationClassVisitor extends ClassVisitor {

        String className;
        private final String annotationDescription;
        private final Collection<MethodAnnotation> annotations = new ArrayList<MethodAnnotation>();

        public MethodAnnotationClassVisitor(String annotationDescription) {
            super(Opcodes.ASM9);
            this.annotationDescription = annotationDescription;
        }

        public Collection<MethodAnnotation> getAnnotations() {
            return annotations;
        }

        public void visit(int version, int access, String name, String signature,
                          String superName, String[] interfaces) {
            this.className = name;
        }

        public MethodVisitor visitMethod(int access, String name, String desc,
                                         String signature, String[] exceptions) {

            return new MethodAnnotationVisitorImpl(name, desc);
        }

        private class MethodAnnotationVisitorImpl extends MethodVisitor {

            private final String methodName;
            private final String methodDesc;

            public MethodAnnotationVisitorImpl(String name, String desc) {
                super(Opcodes.ASM9);
                this.methodName = name;
                this.methodDesc = desc;
            }

            @Override
            public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                if (annotationDescription.equals(desc)) {
                    MethodAnnotationImpl annotation = new MethodAnnotationImpl(desc);
                    annotations.add(annotation);
                    return annotation;
                }
                return null;
            }

            private class MethodAnnotationImpl extends AnnotationImpl implements MethodAnnotation {

                public MethodAnnotationImpl(String desc) {
                    super(desc);
                }

                @Override
                public String getMethodName() {
                    return methodName;
                }

                @Override
                public String getMethodDesc() {
                    return methodDesc;
                }

                @Override
                public String getClassName() {
                    return className;
                }
            }
        }

    }

}
