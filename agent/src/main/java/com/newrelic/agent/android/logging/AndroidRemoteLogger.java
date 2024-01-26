/*
 * Copyright (c) 2023. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.logging;

import android.util.Log;

import com.newrelic.agent.android.analytics.AnalyticsAttribute;
import com.newrelic.agent.android.harvest.HarvestLifecycleAware;
import com.newrelic.agent.android.util.NamedThreadFactory;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

public class AndroidRemoteLogger extends LogReporting implements HarvestLifecycleAware {
    static final String TAG = AndroidRemoteLogger.class.getSimpleName();
    static int VORTEX_PAYLOAD_LIMIT = 1 * 1024 * 1024;  // Vortex upload limit (1 MB compressed)
    static int POOL_SIZE = 20;  // Buffer up this this number of requests

    static int MAX_ATTRIBUTES_PER_EVENT = 255;
    static int MAX_ATTRIBUTES_NAME_SIZE = 255;
    static int MAX_ATTRIBUTES_VALUE_SIZE = 4096;

    private static ReentrantLock pauseLock = new ReentrantLock();

    protected AtomicReference<BufferedWriter> workingFileWriter = new AtomicReference<>(null);   // lazy initialized
    protected ThreadPoolExecutor executor = new ThreadPoolExecutor(1,
            POOL_SIZE, 50L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(POOL_SIZE),
            new NamedThreadFactory("LogReporting"),
            new ThreadPoolExecutor.CallerRunsPolicy());

    // protected ExecutorService executor1 = Executors.newFixedThreadPool(1, new NamedThreadFactory("LogReporting"));

    protected int uploadBudget = VORTEX_PAYLOAD_LIMIT;
    protected File workingLogFile;

    public AndroidRemoteLogger() {
        try {
            workingFileWriter.compareAndSet(null, getWorkingFileWriter());
        } catch (IOException e) {
            AgentLogManager.getAgentLog().error(e.getLocalizedMessage());
        }
    }

    @Override
    public void log(LogLevel logLevel, String message) {
        super.logToAgent(logLevel, message);

        if (isLevelEnabled(logLevel)) {
            appendLog(logLevel, message, null, null);
        }
    }

    @Override
    public void logThrowable(LogLevel logLevel, String message, Throwable throwable) {
        super.logThrowable(logLevel, message, throwable);

        if (isLevelEnabled(logLevel)) {
            appendLog(logLevel, message, throwable, null);
        }
    }

    @Override
    public void logAttributes(@NotNull Map<String, Object> attributes) {
        super.logAttributes(attributes);

        if (isLevelEnabled(logLevel)) {
            String level = (String) attributes.getOrDefault("level", "NONE");
            String message = (String) attributes.getOrDefault("message", null);
            appendLog(LogLevel.valueOf(level.toUpperCase()), message, null, attributes);
        }
    }

    @Override
    public void logAll(@NotNull Throwable throwable, @NotNull Map<String, Object> attributes) {
        super.logAll(throwable, attributes);

        if (isLevelEnabled(logLevel)) {
            String level = (String) attributes.getOrDefault("level", "NONE");
            String message = (String) attributes.getOrDefault("message", null);
            appendLog(LogLevel.valueOf(level.toUpperCase()), message, throwable, attributes);
        }
    }

    /**
     * Emit log data into file as Json-encoded string
     *
     * @param logLevel
     * @param @Nullable message
     * @param @Nullable throwable
     * @param @Nullable attributes
     * @link https://docs.newrelic.com/docs/logs/log-api/log-event-data/
     */
    public synchronized void appendLog(final LogLevel logLevel, @Nullable final String message, @Nullable final Throwable throwable, @Nullable final Map<String, Object> attributes) {
        if (!(isRemoteLoggingEnabled() && isLevelEnabled(logLevel))) {
            return;
        }

        // always run the request on a background thread
        // executor.submit(() -> {
        try {
            final Map<String, Object> logDataObjects = new HashMap<>();

            /**
             * Some specific attributes have additional restrictions:
             *
             * accountId: This is a reserved attribute name. If it is included, it will be dropped during ingest.
             * appId: Must be an integer. When using a non-integer data type, the data will be ingested but becomes unqueryable.
             * entity.guid, entity.name, and entity.type: These attributes are used internally to identify entities. Any values submitted with these keys in the attributes section of a metric data point may cause undefined behavior such as missing entities in the UI or telemetry not associating with the expected entities. For more information please refer to Entity synthesis.
             * eventType: This is a reserved attribute name. If it is included, it will be dropped during ingest.
             * timestamp: Must be a Unix epoch timestamp (either in seconds or in milliseconds) or an ISO8601-formatted timestamp.
             *
             * @see reserved attributes: https://source.datanerd.us/agents/agent-specs/blob/main/Application-Logging.md#log-record-attributes
             */
            logDataObjects.put(AnalyticsAttribute.EVENT_TIMESTAMP_ATTRIBUTE, String.valueOf(System.currentTimeMillis()));
            logDataObjects.put(LogReporting.LOG_LEVEL_ATTRIBUTE, logLevel.name().toUpperCase());

            // set data with reserved attribute values
            logDataObjects.putAll(getCommonBlockAttributes());

            // translate a passed message to attributes
            if (message != null) {
                logDataObjects.put(LogReporting.LOG_MESSAGE_ATTRIBUTE, message);
            }

            // translate any passed throwable to attributes
            if (throwable != null) {
                logDataObjects.put(LogReporting.LOG_ERROR_MESSAGE_ATTRIBUTE, throwable.getLocalizedMessage());
                logDataObjects.put(LogReporting.LOG_ERROR_STACK_ATTRIBUTE, throwable.getStackTrace()[0].toString());
                logDataObjects.put(LogReporting.LOG_ERROR_CLASS_ATTRIBUTE, throwable.getClass().getSimpleName());
            }

            // finally add any passed attributes, which should not override reserved keys
            if (attributes != null) {
                /**
                 * "attributes" Object: This sub-object contains all other attributes of the message
                 *
                 * Number of attributes per event: 255 maximum.
                 * Length of attribute name: 255 characters.
                 * Length of attribute value: 4,094 characters are stored in NRDB as a Log event field
                 **/

                logDataObjects.putAll(attributes);
            }

            if (workingFileWriter.get() != null) {
                JSONObject jsonObject = new JSONObject(logDataObjects);
                String logJsonData = jsonObject.toString();

                workingFileWriter.get().append(logJsonData + ",");
                workingFileWriter.get().newLine();

                // Check Vortex limits
                uploadBudget -= logJsonData.length();
                if (0 > uploadBudget) {
                    rollWorkingLogFile();
                }
            }
        } catch (Exception e) {
            AgentLogManager.getAgentLog().error(e.getLocalizedMessage());
        }
        // });
    }

    BufferedWriter getWorkingFileWriter() throws IOException {
        workingLogFile = LogReporter.getWorkingLogfile();

        // BufferedWriter for performance, true to set append to file flag
        workingFileWriter.compareAndSet(null, new BufferedWriter(new FileWriter(workingLogFile, true)));
        uploadBudget = VORTEX_PAYLOAD_LIMIT;

        if (workingLogFile.length() == 0) {
            workingFileWriter.get().append("[");    // start the json array
            workingFileWriter.get().newLine();
        }

        return workingFileWriter.get();
    }

    @Override
    public synchronized void onHarvest() {
        try {
            rollWorkingLogFile();
        } catch (IOException e) {
            AgentLogManager.getAgentLog().error(e.getLocalizedMessage());
        }
    }

    @Override
    public void onHarvestStop() {
        try {
            onHarvest();
            shutdown();
        } catch (Exception e) {
            AgentLogManager.getAgentLog().error(e.getLocalizedMessage());
        }

        workingFileWriter.set(null);
    }

    protected void flushPendingRequests() {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        while (!executor.getQueue().isEmpty() && !executor.isTerminating() && !executor.isTerminated()) {
            Thread.yield();
        }
    }

    synchronized void finalizeWorkingLogFile() {
        try {
            flushPendingRequests();
            workingFileWriter.get().append("]");
            workingFileWriter.get().flush();
            workingFileWriter.get().close();
            workingFileWriter.set(null);
        } catch (Exception e) {
            Log.e(TAG, e.getLocalizedMessage());
        }
    }

    File rollWorkingLogFile() throws IOException {
        finalizeWorkingLogFile();

        File closedLogFile = LogReporter.rollLogfile(workingLogFile);
        workingLogFile = LogReporter.getWorkingLogfile();
        workingFileWriter.compareAndSet(null, getWorkingFileWriter());

        logToAgent(LogLevel.DEBUG, "Finalized log data to [" + closedLogFile.getAbsolutePath() + "]");

        return closedLogFile;
    }

    public int getBytesRemaining() {
        return uploadBudget;
    }

    void shutdown() {
        finalizeWorkingLogFile();
        executor.shutdown();
        try {
            executor.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
    }
}