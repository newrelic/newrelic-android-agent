/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.compile.visitor;

import com.newrelic.agent.util.ClassAnnotation;
import com.newrelic.agent.util.ClassAnnotationImpl;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Visits a class and builds a collection of {@link ClassAnnotation}s.
 */
public class ClassAnnotationVisitor extends ClassVisitor {
    private final Collection<ClassAnnotation> annotations = new ArrayList<ClassAnnotation>();
    private String className;
    private final String annotationDescription;

    public ClassAnnotationVisitor(String annotationDescription) {
        super(Opcodes.ASM8);
        this.annotationDescription = annotationDescription;
    }

    public Collection<ClassAnnotation> getAnnotations() {
        return annotations;
    }

    public static Collection<ClassAnnotation> getAnnotations(ClassReader cr, String annotationDescription) {
        ClassAnnotationVisitor visitor = new ClassAnnotationVisitor(annotationDescription);
        cr.accept(visitor, 0);
        return visitor.getAnnotations();
    }

    @Override
    public void visit(int version, int access, String name, String signature,
                      String superName, String[] interfaces) {
        this.className = name;
    }

    @Override
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        if (annotationDescription.equals(desc)) {
            ClassAnnotationImpl annotationVisitor = new ClassAnnotationImpl(className, desc);
            annotations.add(annotationVisitor);
            return annotationVisitor;
        } else {
            return null;
        }
    }
}
