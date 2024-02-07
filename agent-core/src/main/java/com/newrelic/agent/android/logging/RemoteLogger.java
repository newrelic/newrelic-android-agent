/*
 * Copyright (c) 2023-2024. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.logging;

import com.newrelic.agent.android.harvest.HarvestLifecycleAware;
import com.newrelic.agent.android.util.NamedThreadFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class RemoteLogger extends LogReporting implements HarvestLifecycleAware {
    static int POOL_SIZE = Math.max(2, Runtime.getRuntime().availableProcessors() / 8); // Buffer up this this number of requests
    static long QUEUE_THREAD_TTL = 1000;

    // TODO enforce log message constraints
    static int MAX_ATTRIBUTES_PER_EVENT = 255;
    static int MAX_ATTRIBUTES_NAME_SIZE = 255;
    static int MAX_ATTRIBUTES_VALUE_SIZE = 4096;

    protected ThreadPoolExecutor executor = new ThreadPoolExecutor(2,
            POOL_SIZE,
            QUEUE_THREAD_TTL, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<Runnable>(),
            new NamedThreadFactory("LogReporting"));

    public RemoteLogger() {
        executor.allowCoreThreadTimeOut(true);
        executor.prestartCoreThread();
    }

    @Override
    public void log(LogLevel logLevel, String message) {
        super.logToAgent(logLevel, message);

        if (isLevelEnabled(logLevel)) {
            appendToWorkingLogFile(logLevel, message, null, null);
        }
    }

    @Override
    public void logThrowable(LogLevel logLevel, String message, Throwable throwable) {
        super.logThrowable(logLevel, message, throwable);

        if (isLevelEnabled(logLevel)) {
            appendToWorkingLogFile(logLevel, message, throwable, null);
        }
    }

    @Override
    public void logAttributes(Map<String, Object> attributes) {
        super.logAttributes(attributes);

        if (isLevelEnabled(logLevel)) {
            String level = (String) attributes.getOrDefault(LogReporting.LOG_LEVEL_ATTRIBUTE, LogLevel.INFO.name());
            String message = (String) attributes.getOrDefault(LogReporting.LOG_MESSAGE_ATTRIBUTE, null);
            appendToWorkingLogFile(LogLevel.valueOf(level.toUpperCase()), message, null, attributes);
        }
    }

    @Override
    public void logAll(Throwable throwable, Map<String, Object> attributes) {
        super.logAll(throwable, attributes);

        if (isLevelEnabled(logLevel)) {
            String level = (String) attributes.getOrDefault(LogReporting.LOG_LEVEL_ATTRIBUTE, LogLevel.INFO.name());
            String message = (String) attributes.getOrDefault(LogReporting.LOG_MESSAGE_ATTRIBUTE, null);
            appendToWorkingLogFile(LogLevel.valueOf(level.toUpperCase()), message, throwable, attributes);
        }
    }

    /**
     * Emit log data into file as Json-encoded string. We follow the NewRelic Simple Logging format.
     *
     * @param logLevel
     * @param @Nullable message
     * @param @Nullable throwable
     * @param @Nullable attributes
     *                  Gson does not support serialization of anonymous nested classes. They will be serialized as JSON null.
     *                  Convert the classes to static nested classes to enable serialization and deserialization for them.
     * @link https://docs.newrelic.com/docs/logs/log-api/introduction-log-api/#simple-json
     */
    public void appendToWorkingLogFile(final LogLevel logLevel, final String message, final Throwable throwable, final Map<String, Object> attributes) {
        if (!(isRemoteLoggingEnabled() && isLevelEnabled(logLevel))) {
            return;
        }

        final LogReporter logReporter = LogReporter.getInstance();

        Callable<Boolean> callable = () -> {
            final Map<String, Object> logDataMap = new HashMap<>();

            try {
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
                logDataMap.put(LogReporting.LOG_TIMESTAMP_ATTRIBUTE, String.valueOf(System.currentTimeMillis()));
                logDataMap.put(LogReporting.LOG_LEVEL_ATTRIBUTE, logLevel.name().toUpperCase());

                // set data with reserved attribute values
                logDataMap.putAll(getCommonBlockAttributes());

                // translate a passed message to attributes
                if (message != null) {
                    logDataMap.put(LogReporting.LOG_MESSAGE_ATTRIBUTE, message);
                }

                // translate any passed throwable to attributes
                if (throwable != null) {
                    logDataMap.put(LogReporting.LOG_ERROR_MESSAGE_ATTRIBUTE, throwable.toString());
                    logDataMap.put(LogReporting.LOG_ERROR_STACK_ATTRIBUTE, throwable.getStackTrace()[0].toString());
                    logDataMap.put(LogReporting.LOG_ERROR_CLASS_ATTRIBUTE, throwable.getClass().getSimpleName());
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

                    logDataMap.put(LogReporting.LOG_ATTRIBUTES_ATTRIBUTE, attributes);
                }

                if (null == logReporter) {
                    return false;
                }

                // pass data map to the reporter
                logReporter.appendToWorkingLogFile(logDataMap);

            } catch (IOException e) {
                AgentLogManager.getAgentLog().error("Error recording log message: " + e.toString());

                // try recovery:
                if (!(executor.isTerminating() || executor.isShutdown())) {
                    if (null != logReporter.resetWorkingLogFile()) {
                        super.logAttributes(logDataMap);
                    }
                }

                return false;

            } finally {
                synchronized (executor) {
                    executor.notify();
                }
            }

            return true;
        };

        if (executor.isTerminating() || executor.isShutdown()) {
            try {
                callable.call();        // Non-blocking
            } catch (Exception e) {
                AgentLogManager.getAgentLog().error(e.toString());
            }

            return;
        }

        // always run the request on a background thread
        synchronized (executor) {
            executor.submit(callable);
        }
    }

    @Override
    public void onHarvest() {
        flushPendingRequests();
    }

    @Override
    public void onHarvestStop() {
        try {
            onHarvest();
            // The logger can continue to run until the agent exists, collecting log data
            // to be picked up on the nex app launch.
        } catch (Exception e) {
            AgentLogManager.getAgentLog().error(e.toString());
        }
    }

    /**
     * Pending tasks are those queued and currently executing
     *
     * @return Sum of queued tasks
     */
    private int getPendingTaskCount() {
        return (executor.getQueue().size() + executor.getActiveCount());
    }

    // Block until the in-progress tasks have completed
    protected void flushPendingRequests() {
        synchronized (executor) {
            try {
                while (getPendingTaskCount() > 0 && !executor.isTerminating() && !executor.isTerminated()) {
                    executor.wait(QUEUE_THREAD_TTL, 0);
                }
            } catch (InterruptedException e) {
                logToAgent(LogLevel.ERROR, e.toString());
            }
        }
    }

    void shutdown() {
        executor.shutdown();
        try {
            executor.awaitTermination(3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
    }

}