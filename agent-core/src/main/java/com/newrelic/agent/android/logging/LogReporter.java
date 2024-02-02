/*
 * Copyright (c) 2023. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.logging;

import static com.newrelic.agent.android.logging.LogReporting.gson;
import static com.newrelic.agent.android.logging.LogReporting.gtype;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.analytics.AnalyticsAttribute;
import com.newrelic.agent.android.harvest.Harvest;
import com.newrelic.agent.android.harvest.HarvestLifecycleAware;
import com.newrelic.agent.android.metric.MetricNames;
import com.newrelic.agent.android.payload.PayloadReporter;
import com.newrelic.agent.android.stats.StatsEngine;
import com.newrelic.agent.android.util.Constants;
import com.newrelic.agent.android.util.NamedThreadFactory;
import com.newrelic.agent.android.util.Streams;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.net.ssl.HttpsURLConnection;

public class LogReporter extends PayloadReporter {

    enum LogReportState {
        WORKING("tmp"),     // Contains data from the active logging session
        CLOSED("dat"),      // Contains a single log session, limited by Vortex payload size
        EXPIRED("bak"),     // Contains expired or backup data ready to be deleted
        ROLLUP("rollup"),   // Contains a JsonArray of collected closed log data files
        ALL(".*");          // All log file types

        final String value;

        LogReportState(final String suffix) {
            this.value = suffix;
        }
    }

    // TODO Provide for EU and FedRamp collectors
    static final String LOG_API_URL = "https://log-api.newrelic.com/log/v1";
    static final long LOG_ENDPOINT_TIMEOUT = 10;    // FIXME This is a guess

    static final String LOG_REPORTS_DIR = "newrelic/logreporting";      // root dir for local data files
    static final String LOG_FILE_MASK = "logdata%s.%s";                 // log data file name. suffix will indicate working state

    static final AtomicReference<LogReporter> instance = new AtomicReference<>(new LogReporter());
    static final ReentrantLock workingFileLock = new ReentrantLock();

    static File logDataStore = new File("").getAbsoluteFile();

    protected long reportTTL = TimeUnit.DAYS.convert(3, TimeUnit.MILLISECONDS);     // log data file expiration period (in MS)
    protected File workingLogFile;

    public static LogReporter initialize(File rootDir, AgentConfiguration agentConfiguration) throws IOException {
        if (!rootDir.isDirectory() || !rootDir.exists() || !rootDir.canWrite()) {
            throw new IOException("Reports directory [" + rootDir.getAbsolutePath() + "] must exist and be writable!");
        }

        logDataStore = new File(rootDir, LOG_REPORTS_DIR);
        logDataStore.mkdirs();

        if (!(logDataStore.exists() && logDataStore.canWrite())) {
            throw new IOException("Reports directory [" + rootDir.getAbsolutePath() + "] must exist and be writable!");
        }
        log.debug("LogReporting: saving log reports to " + logDataStore.getAbsolutePath());

        instance.set(new LogReporter(agentConfiguration));
        log.debug("LogReporting: reporter instance initialized");

        Harvest.addHarvestListener(instance.get());
        StatsEngine.get().inc(MetricNames.SUPPORTABILITY_LOG_REPORTING_INIT);

        LogReporting.setLogger(new RemoteLogger());
        log.debug("LogReporting: log has been set to " + LogReporting.getLogger().getClass().getSimpleName());

        return instance.get();
    }

    public LogReporter() {
        this(AgentConfiguration.getInstance());
    }

    public LogReporter(AgentConfiguration agentConfiguration) {
        super(agentConfiguration);
        setEnabled((agentConfiguration.getLogReportingConfiguration().getLoggingEnabled()));
    }

    @Override
    protected void start() {
        if (isEnabled()) {
            onHarvestStart();   // sweep for any cached report from last session
        }
    }

    @Override
    protected void stop() {
        if (isEnabled()) {
            onHarvestStop();
        }
    }

    @Override
    public void onHarvestStart() {
        // agent is starting: archive all working logs from previous session
        // and queue all complete logs for upload
        onHarvest();

        final LogReporting logger = LogReporting.getLogger();
        if (logger instanceof HarvestLifecycleAware) {
            ((HarvestLifecycleAware) logger).onHarvestStart();
        }

        // delete expired files
        expire(Math.toIntExact(reportTTL));
    }

    @Override
    public void onHarvestStop() {
        final LogReporting logger = LogReporting.getLogger();
        if (logger instanceof HarvestLifecycleAware) {
            ((HarvestLifecycleAware) logger).onHarvestStop();
        }
    }

    @Override
    public void onHarvest() {
        if (isEnabled()) {
            final LogReporting logger = LogReporting.getLogger();
            if (logger instanceof HarvestLifecycleAware) {
                ((HarvestLifecycleAware) logger).onHarvest();
            }

            Set<File> completedLogReports = getCachedLogReports(LogReportState.ROLLUP);
            for (File logReport : completedLogReports) {
                if (logReport.isFile()) {
                    if (postLogReport(logReport)) {
                        log.info("Uploaded remote log data [" + logReport.getAbsoluteFile() + "]");
                        LogReporter.safeDelete(logReport);
                    } else {
                        log.error("Upload failed for remote log data [" + logReport.getAbsoluteFile() + "]");
                    }
                }
            }
        }
    }

    @Override
    public void onHarvestConfigurationChanged() {
        //  Some aspect of logging configuration has changed. Update model...
        setEnabled(agentConfiguration.getLogReportingConfiguration().getLoggingEnabled());
        reportTTL = agentConfiguration.getLogReportingConfiguration().getExpirationPeriod();

    }

    /**
     * Return the current collection of logdata artifacts of a specified state.
     */
    protected Set<File> getCachedLogReports(final LogReportState state) {
        Set<File> reportSet = new HashSet<>();

        try {
            String logFileMask = String.format(Locale.getDefault(),
                    LogReporter.LOG_FILE_MASK, ".*", state.value);

            reportSet = Streams.list(LogReporter.logDataStore)
                    .filter(file -> file.isFile() && file.getName().matches(logFileMask))
                    .collect(Collectors.toSet());

        } catch (Exception e) {
            log.error("Can't query cached log reports: " + e.toString());
        }

        return reportSet;
    }

    /**
     * Merge all data from closed log file into a final rollup file.
     * <p>
     * Delete the closed file(s) if successfully moved to the rollup archive.
     *
     * @return
     */
    protected File rollupLogDataFiles() {
        Set<File> logDataFiles = getCachedLogReports(LogReportState.CLOSED);

        if (!logDataFiles.isEmpty()) {
            workingFileLock.lock();

            try {
                File archivedLogFile = new File(LogReporter.logDataStore,
                        String.format(Locale.getDefault(),
                                LogReporter.LOG_FILE_MASK,
                                String.valueOf(System.currentTimeMillis()),
                                LogReportState.ROLLUP.value));

                archivedLogFile.mkdirs();
                archivedLogFile.delete();
                archivedLogFile.createNewFile();

                try (BufferedWriter writer = Streams.newBufferedFileWriter(archivedLogFile)) {
                    JsonArray jsonArray = new JsonArray();

                    logDataFiles.forEach(file -> {
                        if (file != null && file.exists()) {
                            try {
                                Streams.lines(file).forEach(line -> {
                                    if (!line.isEmpty()) {
                                        JsonObject messageAsJson = gson.fromJson(line, JsonObject.class);
                                        jsonArray.add(messageAsJson);
                                    }
                                });

                            } catch (IOException e) {
                                throw new RuntimeException(e.toString());
                            }
                        }
                    });

                    writer.write(jsonArray.toString());
                    writer.flush();
                    writer.close();

                    archivedLogFile.setReadOnly();

                    // remove the completed file(s)
                    logDataFiles.forEach(file -> file.delete());

                } catch (Exception e) {
                    throw new RuntimeException(e.toString());
                }

                return archivedLogFile;

            } catch (IOException e) {
                log.error(e.toString());

            } finally {
                workingFileLock.unlock();

            }
        }

        return null;
    }

    /**
     * Upload closed log file to Logging ingest endpoint
     *
     * @param logDataFile
     * @return
     */
    synchronized boolean postLogReport(File logDataFile) {
        try {
            // disable uploads for now
            if (true) {
                return false;
            }
            if (logDataFile.exists()) {
                JsonArray jsonArr = new JsonArray();

                Streams.lines(logDataFile).forEach(line -> {
                    JsonObject jsonObj = new JsonObject();
                    jsonObj.addProperty(LogReporting.LOG_MESSAGE_ATTRIBUTE, line);
                    jsonObj.addProperty(LogReporting.LOG_ENTITY_ATTRIBUTE, LogReporting.getEntityGuid());
                    jsonObj.addProperty(AnalyticsAttribute.SESSION_ID_ATTRIBUTE, agentConfiguration.getSessionID());
                    jsonArr.add(jsonObj);
                });

                // LogForwarder forwarder = new LogForwarder(jsonArr.toString().getBytes(StandardCharsets.UTF_8), agentConfiguration);
                // forwarder.call();

                URL url = new URL(LOG_API_URL);
                HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty(Constants.Network.CONTENT_TYPE_HEADER, Constants.Network.ContentType.JSON);
                connection.setRequestProperty(Constants.Network.APPLICATION_LICENSE_HEADER, agentConfiguration.getApplicationToken());
                connection.setReadTimeout((int) TimeUnit.MILLISECONDS.convert(LOG_ENDPOINT_TIMEOUT, TimeUnit.SECONDS));
                connection.setDoOutput(true);
                connection.setDoInput(true);

                try (DataOutputStream localdos = new DataOutputStream(connection.getOutputStream())) {
                    localdos.writeBytes(jsonArr.toString());
                    localdos.flush();
                }

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "utf-8"))) {
                    StringBuilder sb = new StringBuilder();
                    while (reader.readLine() != null) {
                        sb.append(reader.readLine());
                    }
                }

                return true;

            }
        } catch (Exception ex) {
            AgentLogManager.getAgentLog().error("Log upload failed: " + ex.getLocalizedMessage());
        }

        return false;
    }

    static void safeDelete(File fileToDelete) {
        // race condition here so rename the file prior to deleting it
        fileToDelete.renameTo(new File(fileToDelete.getAbsolutePath() + "." + LogReportState.EXPIRED.value));
    }

    /**
     * Get or create a file suitable for receiving log data. Currently we use a single file
     * suitable for thread-safe i/o. When the file is closed it will be renamed to a file
     * using a timestamped filename.
     *
     * @return Working log data file.
     * @throws IOException
     */
    File getWorkingLogfile() throws IOException {
        File logFile = new File(LogReporter.logDataStore,
                String.format(Locale.getDefault(),
                        LogReporter.LOG_FILE_MASK,
                        "",
                        LogReporter.LogReportState.WORKING.value));

        logFile.getParentFile().mkdirs();
        if (!logFile.exists()) {
            logFile.createNewFile();
        }

        logFile.setLastModified(System.currentTimeMillis());

        return logFile;
    }

    /**
     * Rename the passed working file to a timestamped filename, ready to be picked up by the log forwarder.
     *
     * @param workingLogeFile
     * @return The renamed (timestamped) working file
     * @throws IOException
     */
    File rollLogfile(File workingLogeFile) throws IOException {
        File closedLogFile = new File(LogReporter.logDataStore,
                String.format(Locale.getDefault(),
                        LogReporter.LOG_FILE_MASK,
                        String.valueOf(System.currentTimeMillis()),
                        LogReportState.CLOSED.value));

        while (closedLogFile.exists()) {
            closedLogFile = new File(closedLogFile.getAbsolutePath() + ".1");
        }

        workingLogeFile.renameTo(closedLogFile);
        closedLogFile.setLastModified(System.currentTimeMillis());

        return closedLogFile;
    }

    /**
     * Give expired log data file one last chance to upload, then safely delete (or not).
     * This should be run at least once per session.
     *
     * @param expirationTTL Time in milliseconds since the file was last modified
     */
    Set<File> expire(long expirationTTL) {
        FileFilter expirationFilter = logReport -> (logReport.exists() && (logReport.lastModified() + expirationTTL) < System.currentTimeMillis());

        Set<File> expiredFiles = Streams.list(LogReporter.logDataStore, expirationFilter).collect(Collectors.toSet());
        expiredFiles.forEach(logReport -> {
            postLogReport(logReport);
            log.error("Remote log data [" + logReport.getName() + "] has expired and will be removed.");
            safeDelete(logReport);
        });

        return expiredFiles;
    }

    /**
     * Remove log data files that have been "deleted" by other operations. We don't really delete files once
     * used, byt rather rename them as a backup. This gives us a window where data can be salvaged in neccesary.
     * This sweep should only be run once in a while.
     */
    Set<File> cleanup() {
        Set<File> expiredFiles = getCachedLogReports(LogReportState.EXPIRED);
        expiredFiles.forEach(new Consumer<File>() {
            @Override
            public void accept(File logReport) {
                if (logReport.delete()) {
                    log.debug("Log data [" + logReport.getName() + "] removed.");
                } else {
                    log.error("Log data [" + logReport.getName() + "] not removed!");
                }
            }
        });

        return expiredFiles;
    }

    // TODO migrate from RemoteLogger

    static final int VORTEX_PAYLOAD_LIMIT = (1 * 1024 * 1024);          // Vortex upload limit: 1 MB (10^6 B) compressed
    static final int POOL_SIZE = 4;
    protected int payloadBudget = VORTEX_PAYLOAD_LIMIT;
    protected AtomicReference<BufferedWriter> workingLogFileWriter = new AtomicReference<>(null);   // lazy initialized
    protected ThreadPoolExecutor executor = new ThreadPoolExecutor(2, POOL_SIZE,
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(),
            new NamedThreadFactory("LogReporting"));

    /**
     * Close the working file with its current contents. Do not flush pending request.
     * Restet the working file and create a new file writer
     *
     * @return Updated working file
     * @throws IOException
     */
    File rollWorkingLogFile() throws IOException {
        File closedLogFile;

        try {
            workingFileLock.lock();
            closedLogFile = LogReporter.instance.get().rollLogfile(workingLogFile);
            workingLogFile = LogReporter.instance.get().getWorkingLogfile();
            resetWorkingLogFile();

            log.debug("Finalized log data to [" + closedLogFile.getAbsolutePath() + "]");
        } catch (IOException e) {
            throw new RuntimeException(e.toString());
        } finally {
            workingFileLock.unlock();
        }

        return closedLogFile;
    }

    BufferedWriter resetWorkingLogFile() throws IOException {
        workingLogFile = LogReporter.instance.get().getWorkingLogfile();

        // BufferedWriter for performance, true to set append to file flag
        workingLogFileWriter.set(new BufferedWriter(new FileWriter(workingLogFile, true)));
        payloadBudget = VORTEX_PAYLOAD_LIMIT;

        return workingLogFileWriter.get();
    }

    void appendToWorkingLogFile(Map<String, Object> logDataMap) throws IOException {
        try {
            String logJsonData = gson.toJson(logDataMap, gtype);

            workingFileLock.lock();

            if (workingLogFileWriter.get() != null) {
                workingLogFileWriter.get().append(logJsonData);
                workingLogFileWriter.get().newLine();

                // Check Vortex limits
                payloadBudget -= (logJsonData.length() + System.lineSeparator().length());
                if (0 > payloadBudget) {
                    rollWorkingLogFile();
                }
            } else {
                // the writer has closed, usually a result of the agent stopping
                // FIXME super.logAttributes(logDataMap);
            }

        } finally {
            workingFileLock.unlock();
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
