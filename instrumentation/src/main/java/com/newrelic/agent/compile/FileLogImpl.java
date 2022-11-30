/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.compile;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.Locale;
import java.util.Map;

public final class FileLogImpl extends Log {

    private final PrintWriter writer;

    public FileLogImpl(Map<String, String> agentOptions, String logFileName) {
        super(agentOptions);
        try {
            writer = new PrintWriter(new FileOutputStream(logFileName));
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void log(String level, String message) {
        synchronized (this) {
            writer.write("[newrelic." + level.substring(0, 1).toUpperCase(Locale.ROOT) + "] " + message + "\n");
            writer.flush();
        }
    }

    @Override
    protected void log(LogLevel level, String message) {
        log(level.name(), message);
    }

    @Override
    public void warn(String message, Throwable cause) {
        if (logLevelEnabled(LogLevel.WARN)) {
            log(LogLevel.WARN, message);
            cause.printStackTrace(writer);
            writer.flush();
        }
    }

    @Override
    public void error(String message, Throwable cause) {
        if (logLevelEnabled(LogLevel.ERROR)) {
            log("error", message);
            cause.printStackTrace(writer);
            writer.flush();
        }
    }

}