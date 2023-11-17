package com.newrelic.agent.android.logging;

import android.content.Context;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.Map;

public class NewRelicLogger {

    protected static final String TAG = NewRelicLogger.class.getName();
    protected boolean remoteLoggingEnabled = true;
    protected int logLevel = Log.INFO;
    protected Context context;
    private static File agentLogFile;

    private NewRelicLogger(Builder builder) {
        this.remoteLoggingEnabled = builder.remoteLoggingEnabled;
        this.logLevel = builder.logLevel;
        this.context = builder.context;
        agentLogFile = new File(context.getFilesDir(), "agentLog.txt");
    }

    public void verbose(String message) {
        Log.v(TAG, message);
    }

    public void verbose(String message, Throwable throwable) {
        Log.v(TAG, message, throwable);
    }

    public void verbose(String message, Throwable throwable, Map<String, String> attributes) {
        Log.v(TAG, message, throwable);
    }

    public void debug(String message) {
        Log.d(TAG, message);
    }

    public void debug(String message, Throwable throwable) {
        Log.d(TAG, message, throwable);
    }

    public void debug(String message, Throwable throwable, Map<String, String> attributes) {
        Log.d(TAG, message, throwable);
    }

    public void info(String message) {
        Log.i(TAG, message);
    }

    public void info(String message, Throwable throwable) {
        Log.i(TAG, message, throwable);
    }

    public void info(String message, Throwable throwable, Map<String, String> attributes) {
        Log.i(TAG, message, throwable);
    }

    public void error(String message) {
        Log.e(TAG, message);
    }

    public void error(String message, Throwable throwable) {
        Log.e(TAG, message, throwable);
    }

    public void error(String message, Throwable throwable, Map<String, String> attributes) {
        Log.e(TAG, message, throwable);
    }

    public void warning(String message) {
        Log.w(TAG, message);
    }

    public void warning(String message, Throwable throwable) {
        Log.w(TAG, message, throwable);
    }

    public void warning(String message, Throwable throwable, Map<String, String> attributes) {
        Log.w(TAG, message, throwable);
    }

    public static void appendLog(String logLevel, String message, Throwable throwable, Map<String, String> attributes) {
        try {
            if (!agentLogFile.exists()) {
                agentLogFile.createNewFile();
            }
            //BufferedWriter for performance, true to set append to file flag
            BufferedWriter buf = new BufferedWriter(new FileWriter(agentLogFile, true));

            Map<String, String> log = new HashMap<>();
            log.put("timeStamp", String.valueOf(System.currentTimeMillis()));
            log.put("message", message);
            log.put("logLevel", logLevel);
            if (throwable != null) {
                log.put("throwableMessage", throwable.getLocalizedMessage());
            }
            if (attributes != null) {
                log.putAll(attributes);
            }

            JSONObject jsonObject = new JSONObject(log);
            String logJsonData = jsonObject.toString();
            buf.append(logJsonData);
            buf.append(",");
            buf.newLine();
            buf.close();
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }


    public static class Builder {
        private boolean remoteLoggingEnabled;
        private int logLevel;
        private Context context;
        // other fields

        public Builder remoteLoggingEnabled(boolean remoteLoggingEnabled) {
            this.remoteLoggingEnabled = remoteLoggingEnabled;
            return this;
        }

        public Builder logLevel(int logLevel) {
            this.logLevel = logLevel;
            return this;
        }

        public Builder context(Context context) {
            this.context = context;
            return this;
        }

        // other setter methods
        public NewRelicLogger build() {
            return new NewRelicLogger(this);
        }
    }

}