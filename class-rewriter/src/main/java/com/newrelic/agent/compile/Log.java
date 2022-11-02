package com.newrelic.agent.compile;

import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;

public abstract class Log {
    public enum LogLevel {
        DEBUG(5),
        VERBOSE(4),
        INFO(3),
        WARN(2),
        ERROR(1);

        private final int value;

        LogLevel(final int newValue) {
            value = newValue;
        }

        public int getValue() {
            return value;
        }
    }

    public static Log LOGGER = new Log(new HashMap<String, String>()) {};

    protected final int logLevel;

    public Log(Map<String, String> agentOptions) {
        String logLevelOpt = agentOptions.get("loglevel");
        if (logLevelOpt != null) {
            this.logLevel = LogLevel.valueOf(logLevelOpt).getValue();
        } else {
            logLevel = LogLevel.WARN.getValue();
        }

        LOGGER = this;
    }

    public void info(String message) {
        if (logLevel >= LogLevel.INFO.getValue()) {
            log("info", message);
        }
    }

    public void debug(String message) {
        if (logLevel >= LogLevel.DEBUG.getValue()) {
            synchronized (this) {
                log("debug", message);
            }
        }
    }

    public void warning(String message) {
        if (logLevel >= LogLevel.WARN.getValue()) {
            log("warn", message);
        }
    }

    public void error(String message) {
        if (logLevel >= LogLevel.ERROR.getValue()) {
            log("error", message);
        }
    }

    protected void log(String level, String message) {
        // log nothing
    }

    public void warning(String message, Throwable cause) {
        // log nothing
    }

    public void error(String message, Throwable cause) {
        // log nothing
    }

}
