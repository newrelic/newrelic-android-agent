/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.compile;

import org.objectweb.asm.MethodVisitor;

public interface MethodVisitorFactory {
    MethodVisitor create(MethodVisitor mv, int access, String name, String desc);

}