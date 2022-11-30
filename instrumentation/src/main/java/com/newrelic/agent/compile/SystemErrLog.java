/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.compile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.Map;

public final class SystemErrLog extends Log {

    Logger logger = LoggerFactory.getLogger(getFullyQualifiedCallerName());

    public SystemErrLog(Map<String, String> agentOptions) {
        super(agentOptions);
    }

    @Override
    protected void log(LogLevel level, String message) {
        switch (level) {
            case ERROR:
                logger.error(message);
                break;
            case WARN:
                logger.warn(message);
                break;
            case INFO:
                logger.info(message);
                break;
            case DEBUG:
                logger.debug(message);
                break;
            case TRACE:
                logger.trace(message);
                break;
            default:
                break;
        }
    }

    protected void log(String level, String message) {
        synchronized (this) {
            System.out.println("[newrelic." + level.substring(0, 1).toUpperCase(Locale.ROOT) + "] " + message);
        }
    }

    @Override
    public void warn(String message, Throwable cause) {
        if (logLevelEnabled(LogLevel.WARN)) {
            synchronized (this) {
                log(LogLevel.WARN, message);
                cause.printStackTrace(System.err);
            }
        }
    }

    @Override
    public void error(String message, Throwable cause) {
        if (logLevelEnabled(LogLevel.ERROR)) {
            synchronized (this) {
                log(LogLevel.ERROR, message);
                cause.printStackTrace(System.err);
            }
        }
    }

}