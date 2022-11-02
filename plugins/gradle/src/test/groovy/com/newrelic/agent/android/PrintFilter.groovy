/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android

import org.jetbrains.annotations.NotNull

class PrintFilter extends StringWriter {
    def filteredLog = new StringBuffer()
    def buf = getBuffer()

    @Override
    String toString() {
        return filteredLog.toString()
    }

    @Override
    void write(@NotNull char[] cbuf, int off, int len) {
        super.write(cbuf, off, len)
        if (cbuf[len - 1] == '\n') {
            buf.findAll(~/\[newrelic[\.\]].*\n/).each {
                filteredLog.append(it)
            }
            buf.setLength(0)
        }
    }
}
