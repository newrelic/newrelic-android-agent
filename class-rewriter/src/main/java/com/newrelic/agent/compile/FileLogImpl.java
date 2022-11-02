package com.newrelic.agent.compile;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.Map;

final class FileLogImpl extends Log {

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
            writer.write("[newrelic." + level.toLowerCase() + "] " + message + "\n");
            writer.flush();
        }
    }

    @Override
    public void warning(String message, Throwable cause) {
        if (logLevel >= LogLevel.WARN.getValue()) {
            log("warn", message);
            cause.printStackTrace(writer);
            writer.flush();
        }
    }

    @Override
    public void error(String message, Throwable cause) {
        if (logLevel >= LogLevel.ERROR.getValue()) {
            log("error", message);
            cause.printStackTrace(writer);
            writer.flush();
        }
    }

}