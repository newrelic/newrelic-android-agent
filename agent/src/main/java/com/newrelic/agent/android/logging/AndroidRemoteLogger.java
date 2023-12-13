package com.newrelic.agent.android.logging;

import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.Map;

public class AndroidRemoteLogger implements AgentLog {
    protected static final String TAG = AndroidRemoteLogger.class.getName();
    protected int logLevel = LogLevel.NONE.ordinal(); // default
    protected static String agentLogFilePath = "";

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

    public void warn(String message) {
        Log.w(TAG, message);
    }

    public void warn(String message, Throwable throwable) {
        Log.w(TAG, message, throwable);
    }

    public void warn(String message, Throwable throwable, Map<String, String> attributes) {
        Log.w(TAG, message, throwable);
    }

    @Override
    public void audit(String message) {

    }

    public void logAttributes(Map<String, Object> attributes) {
        //Requirement: logLevel is included in attributes
        int level = Integer.valueOf(attributes.get("logLevel").toString());
        //TODO: what do we want to print out about attributes
    }

    public void logAll(Throwable throwable, Map<String, Object> attributes) {
        //Requirement: logLevel is included in attributes
        int level = Integer.valueOf(attributes.get("logLevel").toString());
        //TODO: what do we want to print out about attributes + throwable
    }

    @Override
    public int getLevel() {
        return 0;
    }

    @Override
    public void setLevel(int level) {

    }

    public String getAgentLogFilePath() {
        return agentLogFilePath;
    }

    public void setAgentLogFilePath(String agentLogFilePath) {
        this.agentLogFilePath = agentLogFilePath;
    }

    public static void appendLog(String logLevel, String message, Throwable throwable, Map<String, String> attributes) {
        try {
            File agentLogFile = new File(agentLogFilePath);
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
}