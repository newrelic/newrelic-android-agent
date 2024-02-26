/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android

class PrintFilter extends FileWriter {
    def filename
    def filteredLog = new StringBuffer()

    PrintFilter(def filename = "build.log") throws IOException {
        super(filename)
        this.filename = filename
    }

    @Override
    String toString() {
        if (filteredLog.size() == 0) {
            flush()
            new File(filename).eachLine { line ->
                def matcher = line =~ /^.*\[newrelic\] (?<msg>.*)$/
                matcher.matches() && filteredLog.append(matcher.group('msg')).append("\n")
            }
        }
        return filteredLog.toString()
    }
}