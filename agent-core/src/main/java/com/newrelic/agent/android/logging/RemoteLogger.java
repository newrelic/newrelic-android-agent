/*
 * Copyright (c) 2023-2024. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.logging;

import com.newrelic.agent.android.analytics.AnalyticsAttribute;
import com.newrelic.agent.android.harvest.HarvestLifecycleAware;
import com.newrelic.agent.android.util.NamedThreadFactory;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

public class RemoteLogger extends LogReporting implements HarvestLifecycleAware {
    static int VORTEX_PAYLOAD_LIMIT = 1 * 1024 * 1024;  // Vortex upload limit: 1 MB (10^6 B) compressed
    static int POOL_SIZE = 4;  // Buffer up this this number of requests

    static int MAX_ATTRIBUTES_PER_EVENT = 255;
    static int MAX_ATTRIBUTES_NAME_SIZE = 255;
    static int MAX_ATTRIBUTES_VALUE_SIZE = 4096;

    private static ReentrantLock pauseLock = new ReentrantLock();

    protected AtomicReference<BufferedWriter> workingLogFileWriter = new AtomicReference<>(null);   // lazy initialized
    /*
        protected ThreadPoolExecutor executor = new ThreadPoolExecutor(2,
                POOL_SIZE, 50L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(POOL_SIZE * 1000),
                new NamedThreadFactory("LogReporting"),
                new ThreadPoolExecutor.CallerRunsPolicy() {
                    @Override
                    public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
                        logToAgent(LogLevel.ERROR, "e.submit(r)");        // re-submit
                    }
                });
    */
    protected ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newCachedThreadPool(new NamedThreadFactory("LogReporting"));

    protected int uploadBudget = VORTEX_PAYLOAD_LIMIT;
    protected File workingLogFile;

    public RemoteLogger() {
        try {
            workingLogFileWriter.compareAndSet(null, getWorkingLogFileWriter());
            executor.setCorePoolSize(POOL_SIZE);
            executor.prestartCoreThread();

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
     * Emit log data into file as Json-encoded string. We follow the NewRelic Simple Logging format.
     *
     * @param logLevel
     * @param @Nullable message
     * @param @Nullable throwable
     * @param @Nullable attributes
     * @link https://docs.newrelic.com/docs/logs/log-api/introduction-log-api/#simple-json
     */
    public void appendLog(final LogLevel logLevel, @Nullable final String message, @Nullable final Throwable throwable, @Nullable final Map<String, Object> attributes) {
        if (!(isRemoteLoggingEnabled() && isLevelEnabled(logLevel))) {
            return;
        }

        if (executor.isTerminating() || executor.isShutdown()) {
            return;
        }

        // always run the request on a background thread
        executor.submit(() -> {
            try {
                pauseLock.lock();

                final Map<String, Object> logDataObjects = new HashMap<>();

                /**
                 * Some specific attributes have additional restrictions:
                 *
                 * accountId: This is a reserved attribute name. If it is included, it will be dropped during ingest.
                 * appId: Must be an integer. When using a non-integer data type, the data will be ingested but becomes unqueryable.
                 * entity.guid, entity.name, and entity.type: These attributes are used internally to identify entities.
                 * Any values submitted with these keys in the attributes section of a metric data point may cause undefined behavior
                 * such as missing entities in the UI or telemetry not associating with the expected entities.
                 * eventType: This is a reserved attribute name. If it is included, it will be dropped during ingest.
                 * timestamp: Must be a Unix epoch timestamp (either in seconds or in milliseconds) or an ISO8601-formatted timestamp.
                 *
                 * @link reserved attributes: https://source.datanerd.us/agents/agent-specs/blob/main/Application-Logging.md#log-record-attributes
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

                    logDataObjects.put(LogReporting.LOG_ATTRIBUTES_ATTRIBUTE, attributes);
                }

                // pass data map to forwarder
                appendLog(logDataObjects);

            } catch (Exception e) {
                AgentLogManager.getAgentLog().error(e.getLocalizedMessage());
            } finally {
                pauseLock.unlock();
            }
        });
    }

    BufferedWriter getWorkingLogFileWriter() throws IOException {
        workingLogFile = LogReporter.getWorkingLogfile();

        // BufferedWriter for performance, true to set append to file flag
        workingLogFileWriter.compareAndSet(null, new BufferedWriter(new FileWriter(workingLogFile, true)));
        uploadBudget = VORTEX_PAYLOAD_LIMIT;

        if (workingLogFile.length() == 0) {
            workingLogFileWriter.get().append("[");    // start the json array
            workingLogFileWriter.get().newLine();
        }

        return workingLogFileWriter.get();
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

        workingLogFileWriter.set(null);
    }

    protected void flushPendingRequests() {
        try {
            while (!executor.getQueue().isEmpty() && !executor.isTerminating() && !executor.isTerminated()) {
                Thread.yield();
                Thread.sleep(10);
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    void finalizeWorkingLogFile() {
        try {
            flushPendingRequests();
            pauseLock.lock();
            workingLogFileWriter.get().append("]");
            workingLogFileWriter.get().flush();
            workingLogFileWriter.get().close();
            workingLogFileWriter.set(null);
        } catch (Exception e) {
            logToAgent(LogLevel.ERROR, e.getLocalizedMessage());
        } finally {
            pauseLock.unlock();
        }
    }

    File rollWorkingLogFile() throws IOException {
        File closedLogFile = null;
        try {
            pauseLock.lock();
            finalizeWorkingLogFile();
            closedLogFile = LogReporter.rollLogfile(workingLogFile);
            workingLogFile = LogReporter.getWorkingLogfile();
            workingLogFileWriter.compareAndSet(null, getWorkingLogFileWriter());

            logToAgent(LogLevel.DEBUG, "Finalized log data to [" + closedLogFile.getAbsolutePath() + "]");
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            pauseLock.unlock();
        }

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

    public void appendLog(Map<String, Object> logDataMap) throws IOException {
        String logJsonData = gson.toJson(logDataMap, gtype);

        // Check Vortex limits
        uploadBudget -= logJsonData.length();
        if (0 > uploadBudget) {
            rollWorkingLogFile();
        }

        workingLogFileWriter.get().append(logJsonData + ",");
        workingLogFileWriter.get().newLine();
    }
}