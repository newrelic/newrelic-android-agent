/*
 * Copyright (c) 2022-2023. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.instrumentation

import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.api.instrumentation.ClassContext
import com.android.build.api.instrumentation.ClassData
import com.android.build.api.instrumentation.InstrumentationParameters
import com.android.build.api.instrumentation.FramesComputationMode
import com.android.build.api.instrumentation.InstrumentationScope


import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.util.TraceClassVisitor

abstract class TraceClassVisitorFactory implements AsmClassVisitorFactory<VisitorFactoryParams> {

    interface VisitorFactoryParams extends InstrumentationParameters {
        @Input
        @Optional
        abstract Property<Boolean> getTruth()
    }

    @Override
    ClassVisitor createClassVisitor(ClassContext classContext, ClassVisitor nextClassVisitor) {
        def className = classContext.currentClassData.className
        def classReader = nextClassVisitor.findClassWriter().findClassReader()
        def isMinifiedClass = classReader.isMinifiedClass() ?: false

        return new TraceClassVisitor(nextClassVisitor, new PrintWriter(new File("trace.asm")))
    }

    @Override
    boolean isInstrumentable( ClassData classData) {
        return classData.className.startsWith("com.newrelic")
    }
}
