/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.compile;

import org.slf4j.event.Level;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.Map;

public final class FileLogger extends Logger {

    private final PrintWriter writer;

    public FileLogger(Map<String, String> agentOptions, String logFileName) {
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
            writer.write("[" + level + "] [" + TAG + "] " + message);
            writer.flush();
        }
    }

    @Override
    protected void log(Level level, String message) {
        log(level.name(), message);
    }

    @Override
    public void warn(String message, Throwable cause) {
        if (isLevelEnabled(Level.WARN)) {
            log(Level.WARN, message);
            cause.printStackTrace(writer);
            writer.flush();
        }
    }

    @Override
    public void error(String message, Throwable cause) {
        if (isLevelEnabled(Level.ERROR)) {
            log(Level.ERROR, message);
            cause.printStackTrace(writer);
            writer.flush();
        }
    }

}