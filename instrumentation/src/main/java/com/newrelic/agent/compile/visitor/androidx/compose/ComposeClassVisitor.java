/*
 * Copyright (c) 2022-2023. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.compile.visitor.androidx.compose;

import com.google.common.collect.ImmutableSet;
import com.newrelic.agent.compile.InstrumentationContext;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.slf4j.Logger;

import java.util.Set;

/**
 * This class visitor adds calls to agent delegate methods from classes which extend Jetpack Compose
 * classes.
 */
public class ComposeClassVisitor extends ClassVisitor {

    /**
     * This set should include the names of all Compose SDK classes that would be base classes
     * of an instrumented client class:
     * - androidx.navigation.compose.ComposeNavigator
     */
    static final Set<String> COMPOSE_CLASSES = ImmutableSet.of(
            "^(androidx\\/compose(.*))"
    );

    ClassVisitor createVisitorChain(ClassVisitor cv, InstrumentationContext context, Logger log) {
        cv = new ComposeNavigatorClassVisitor(cv, context, log);
        return cv;
    }

    public ComposeClassVisitor(ClassVisitor cv, InstrumentationContext context, Logger log) {
        super(Opcodes.ASM9);
        this.cv = createVisitorChain(cv, context, log);
    }
}