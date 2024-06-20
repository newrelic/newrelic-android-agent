/*
 * Copyright (c) 2023. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.logging;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.harvest.Harvest;
import com.newrelic.agent.android.harvest.HarvestLifecycleAware;
import com.newrelic.agent.android.metric.MetricNames;
import com.newrelic.agent.android.payload.PayloadReporter;
import com.newrelic.agent.android.stats.StatsEngine;
import com.newrelic.agent.android.util.Streams;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileFilter;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.net.ssl.HttpsURLConnection;


public class LogReporter extends PayloadReporter {

    protected static final Type gtype = new TypeToken<Map<String, Object>>() {}.getType();
    protected static final Gson gson = new GsonBuilder()
            .enableComplexMapKeySerialization()
            .create();

    enum LogReportState {
        WORKING("tmp"),     // Contains data from the active logging session
        CLOSED("dat"),      // Contains a single log session, limited by Vortex payload size
        EXPIRED("bak"),     // Contains expired or backup data ready to be deleted
        ROLLUP("rollup"),   // Contains a JsonArray of closed log data files
        ALL(".*");          // All log file types

        final String extension;

        LogReportState(final String extension) {
            this.extension = extension;
        }

        public String asExtension() {
            return String.format(Locale.getDefault(), ".%s", extension);
        }
    }

    static int VORTEX_PAYLOAD_LIMIT = (1024 * 1000);        // Vortex upload limit: ~1 MB (10^6 B) compressed (less some padding)
    static int MIN_PAYLOAD_THRESHOLD = -1;                  // Don't upload until we have at least this much data (-1 disables check)
    protected int payloadBudget = VORTEX_PAYLOAD_LIMIT;

    static final long LOG_ENDPOINT_TIMEOUT = 10;    // FIXME This is a guess, check with Logging team

    static final String LOG_REPORTS_DIR = "newrelic/logreporting";      // root dir for local data files
    static final String LOG_FILE_MASK = "logdata%s.%s";                 // log data file name. suffix will indicate working state
    static final Pattern LOG_FILE_REGEX = Pattern.compile("^(?<path>.*\\/" + LOG_REPORTS_DIR + ")\\/(?<file>logdata.*)\\.(?<extension>.*)$");

    static final AtomicReference<LogReporter> instance = new AtomicReference<>(null);
    static final ReentrantLock workingFileLock = new ReentrantLock();

    static File logDataStore = new File(System.getProperty("java.io.tmpdir", "/tmp"), LOG_REPORTS_DIR).getAbsoluteFile();

    protected long reportTTL = LogReportingConfiguration.DEFAULT_EXPIRATION_PERIOD;     // log data file expiration period (in MS)
    protected File workingLogFile;
    protected AtomicReference<BufferedWriter> workingLogFileWriter = new AtomicReference<>(null);   // lazy initialized

    public static LogReporter initialize(File rootDir, AgentConfiguration agentConfiguration) throws IOException {
        if (!rootDir.isDirectory() || !rootDir.exists() || !rootDir.canWrite()) {
            throw new IOException("Reports directory [" + rootDir.getAbsolutePath() + "] must exist and be writable!");
        }

        logDataStore = new File(rootDir, LOG_REPORTS_DIR);
        logDataStore.mkdirs();

        if (!(logDataStore.exists() && logDataStore.canWrite())) {
            throw new IOException("LogReporter: Reports directory [" + rootDir.getAbsolutePath() + "] must exist and be writable!");
        }
        log.debug("LogReporting: saving log reports to " + logDataStore.getAbsolutePath());

        instance.set(new LogReporter(agentConfiguration));
        log.debug("LogReporting: reporter instance initialized");

        LogReporting.setLogger(new RemoteLogger());
        log.debug("LogReporting: logger has been set to " + LogReporting.getLogger().getClass().getSimpleName());

        StatsEngine.get().inc(MetricNames.SUPPORTABILITY_LOG_REPORTING_INIT);

        return instance.get();
    }

    public static LogReporter getInstance() {
        return instance.get();
    }


    public LogReporter(AgentConfiguration agentConfiguration) {
        super(agentConfiguration);
        setEnabled((agentConfiguration.getLogReportingConfiguration().getLoggingEnabled()));
        try {
            resetWorkingLogFile();
        } catch (IOException e) {
            log.error("LogReporter error: " + e);
            setEnabled(false);
        }
    }

    @Override
    protected void start() {
        if (isEnabled()) {
            Harvest.addHarvestListener(instance.get());
            LogReportingConfiguration.reseed();
            isStarted.set(true);
        } else {
            log.error("Attempted to start the log reported when disabled.");
        }
    }

    @Override
    protected void stop() {
        Harvest.removeHarvestListener(instance.get());

        isStarted.set(false);
        if (isEnabled()) {
            onHarvestStop();
        }

        workingLogFileWriter.set(null);
    }

    @Override
    public void onHarvestStart() {
        final Logger logger = LogReporting.getLogger();

        if (logger instanceof HarvestLifecycleAware) {
            ((HarvestLifecycleAware) logger).onHarvestStart();
        }

        // expire and delete leftover files
        expire(Math.toIntExact(reportTTL));
        cleanup();
    }

    @Override
    public void onHarvestStop() {
        try {
            final Logger logger = LogReporting.getLogger();
            if (logger instanceof HarvestLifecycleAware) {
                ((HarvestLifecycleAware) logger).onHarvestStop();
            }

            // The logger can continue to run collecting log data until the agent exists.
            // but will no longer be triggered by the harvest lifecycle nor uploaded to ingest

        } catch (Exception e) {
            log.error(e.toString());
        }
    }

    @Override
    public void onHarvest() {
        try {
            final Logger logger = LogReporting.getLogger();
            if (logger instanceof HarvestLifecycleAware) {
                ((HarvestLifecycleAware) logger).onHarvest();
            }

            workingFileLock.lock();

            // roll the log only if data has been added to the working file
            workingLogFileWriter.get().flush();
            if (workingLogFile.length() > LogReporter.MIN_PAYLOAD_THRESHOLD) {
                finalizeWorkingLogFile();
                rollWorkingLogFile();
            }

        } catch (IOException e) {
            log.error("LogReporter: " + e);

        } finally {
            workingFileLock.unlock();

        }

        if (isEnabled()) {
            // create a single log archive from all available closed files, up to 1Mb in size
            File logReport = rollupLogDataFiles();

            if (null != logReport && logReport.isFile()) {
                if (postLogReport(logReport)) {
                    log.info("LogReporter: Uploaded remote log data [" + logReport.getName() + "]");
                    safeDelete(logReport);
                } else {
                    log.error("LogReporter: Upload failed for remote log data [" + logReport.getAbsoluteFile() + "]");
                }
            }
        }
    }

    @Override
    public void onHarvestComplete() {
        final Logger logger = LogReporting.getLogger();

        if (logger instanceof HarvestLifecycleAware) {
            ((HarvestLifecycleAware) logger).onHarvestComplete();
        }

        getCachedLogReports(LogReportState.ROLLUP).forEach(logReport -> {
            if (postLogReport(logReport)) {
                log.info("LogReporter: Uploaded remote log data [" + logReport.getAbsolutePath() + "]");
                safeDelete(logReport);
            } else {
                log.error("LogReporter: Upload failed for remote log data [" + logReport.getAbsolutePath() + "]");
            }
        });

        expire(Math.toIntExact(reportTTL));
    }

    @Override
    public void onHarvestConfigurationChanged() {
        //  Some aspect of logging configuration has changed. Update model...
        setEnabled(agentConfiguration.getLogReportingConfiguration().getLoggingEnabled());

        if (agentConfiguration.getLogReportingConfiguration().getExpirationPeriod() != reportTTL) {
            reportTTL = Math.max(agentConfiguration.getLogReportingConfiguration().getExpirationPeriod(),
                    TimeUnit.MILLISECONDS.convert(30, TimeUnit.SECONDS));
            log.debug("LogReporter: logging configuration changed [" + agentConfiguration.getLogReportingConfiguration().toString() + "]");
        }
    }

    /**
     * Return the current collection of logdata artifacts of a specified state.
     */
    protected Set<File> getCachedLogReports(final LogReportState state) {
        Set<File> reportSet = new HashSet<>();

        try {
            String logFileMask = String.format(Locale.getDefault(),
                    LogReporter.LOG_FILE_MASK, ".*", state.extension);

            reportSet = Streams.list(LogReporter.logDataStore)
                    .filter(file -> file.isFile() && file.getName().matches(logFileMask))
                    .collect(Collectors.toSet());

        } catch (Exception e) {
            log.error("LogReporter: Can't query cached log reports: " + e);
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
        int totalFileSize = logDataFiles.stream().mapToInt(file -> Math.toIntExact(file.length())).sum();

        if (MIN_PAYLOAD_THRESHOLD > totalFileSize) {
            if (!logDataFiles.isEmpty()) {
                log.debug("LogReporter: buffering log data until the minimum threshold: " + totalFileSize + "/" + MIN_PAYLOAD_THRESHOLD + " bytes");
            }

            return null;
        }

        Set<File> mergedFiles = new HashSet<>();
        int payloadSizeBudget = LogReporter.VORTEX_PAYLOAD_LIMIT;

        try {
            workingFileLock.lock();

            JsonArray jsonArray = new JsonArray();

            for (File file : logDataFiles) {
                if (null != file && file.exists() && file.length() > 0) {
                    try {
                        // truncate at payload size limit. Test first so we don't overflow the budget
                        payloadSizeBudget -= file.length();
                        if (0 > payloadSizeBudget) {
                            break;
                        }

                        Streams.lines(file).forEach(line -> {
                            if (!line.isEmpty()) {
                                try {
                                    JsonObject messageAsJson = gson.fromJson(line, JsonObject.class);
                                    jsonArray.add(messageAsJson);
                                } catch (JsonSyntaxException e) {
                                    log.error("LogReporter: Invalid log record dropped: " + line);
                                    // TODO Save rejected log for later review
                                }
                            }
                        });

                        // remove the completed file(s)
                        mergedFiles.add(file);

                    } catch (Exception e) {
                        log.error("LogReporter: " + e.toString());
                    }
                }
            }

            if (jsonArray.size() > 0) {
                File archivedLogFile = new File(LogReporter.logDataStore,
                        String.format(Locale.getDefault(),
                                LogReporter.LOG_FILE_MASK,
                                System.currentTimeMillis(),
                                LogReportState.ROLLUP.extension));

                archivedLogFile.mkdirs();
                archivedLogFile.delete();
                archivedLogFile.createNewFile();

                try (BufferedWriter writer = Streams.newBufferedFileWriter(archivedLogFile)) {
                    writer.write(jsonArray.toString());
                    writer.flush();
                    writer.close();

                    // critical section:
                    archivedLogFile.setReadOnly();

                } catch (Exception e) {
                    log.error("Log file rollup failed: " + e);
                }

                mergedFiles.forEach(file -> safeDelete(file));

                return archivedLogFile;
            }

        } catch (IOException e) {
            log.error(e.toString());

        } finally {
            workingFileLock.unlock();
        }

        return null;
    }

    /**
     * Upload closed log file to Logging ingest endpoint
     *
     * @param logDataFile
     * @return
     */
    boolean postLogReport(File logDataFile) {
        try {
            if (logDataFile.exists()) {
                if (!isLogfileTypeOf(logDataFile, LogReportState.ROLLUP)) {
                    logDataFile = rollupLogDataFiles();
                }

                if (logDataFile.exists() && isLogfileTypeOf(logDataFile, LogReportState.ROLLUP)) {
                    LogForwarder logForwarder = new LogForwarder(logDataFile, agentConfiguration);

                    switch (logForwarder.call().getResponseCode()) {
                        // Upload timed-out
                        case HttpURLConnection.HTTP_CLIENT_TIMEOUT:
                            break;

                        // Payload too large:
                        case HttpURLConnection.HTTP_ENTITY_TOO_LARGE:
                            // TODO The payload was too large, despite filtering prior to upload
                            // Only solution in this case is to decompose and redistribute the payload
                            break;

                        // Upload was throttled
                        case 429:
                            break;

                        // Payload was rejected
                        case HttpsURLConnection.HTTP_INTERNAL_ERROR:
                            break;

                        default:
                            break;
                    }

                    return logForwarder.isSuccessfulResponse();
                }

            } else {
                log.warn("LogReporter: Logfile [" + logDataFile.getName() + "] vanished before it could be uploaded.");
            }
        } catch (Exception e) {
            AgentLogManager.getAgentLog().error("LogReporter: Log upload failed: " + e);
        }

        return false;
    }

    void safeDelete(File fileToDelete) {
        // Potential race condition here so rename the file rather than deleting it.
        // Expired files will be removed during an expiration sweep.
        if (!isLogfileTypeOf(fileToDelete, LogReportState.EXPIRED)) {
            fileToDelete.setReadOnly();
            fileToDelete.renameTo(new File(fileToDelete.getAbsolutePath() + LogReportState.EXPIRED.asExtension()));
        }
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
                        LogReporter.LogReportState.WORKING.extension));

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
     * @param workingLogFile
     * @return The renamed (timestamped) working file
     * @throws IOException
     */
    File rollLogfile(File workingLogFile) throws IOException {
        File closedLogFile;
        int retries = 5;

        do {
            closedLogFile = new File(LogReporter.logDataStore,
                    String.format(Locale.getDefault(),
                            LogReporter.LOG_FILE_MASK,
                            System.currentTimeMillis(),
                                LogReportState.CLOSED.extension));

        } while (closedLogFile.exists() && 0 < closedLogFile.length() && retries-- > 0);

        workingLogFile.renameTo(closedLogFile);
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
        FileFilter expirationFilter = logReport -> (logReport.exists()
                && isLogfileTypeOf(logReport, LogReportState.WORKING)
                && (logReport.lastModified() + expirationTTL) < System.currentTimeMillis());

        Set<File> expiredFiles = Streams.list(LogReporter.logDataStore, expirationFilter).collect(Collectors.toSet());
        expiredFiles.forEach(logReport -> {
            StatsEngine.SUPPORTABILITY.inc(MetricNames.SUPPORTABILITY_LOG_EXPIRED);
            log.debug("LogReporter: Remote log data [" + logReport.getName() + "] has expired and will be removed.");
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
        expiredFiles.forEach(logReport -> {
            if (logReport.delete()) {
                log.debug("LogReporter: Log data [" + logReport.getName() + "] removed.");
            } else {
                log.warn("LogReporter: Log data [" + logReport.getName() + "] not removed!");
            }
        });

        return expiredFiles;
    }

    /**
     * Restore closed log files. This is for testing at the moment and will unlikely be included
     * in GA, unless early adoption testing exposes a need for it.
     *
     * @return Set contain the File instance af call recovered log files.
     */
    Set<File> recover() {
        Set<File> recoveredFiles = getCachedLogReports(LogReportState.EXPIRED);
        recoveredFiles.forEach(logReport -> {
            logReport.setWritable(true);
            logReport.renameTo(new File(logReport.getAbsolutePath().replace(LogReportState.EXPIRED.asExtension(), "")));
        });

        return recoveredFiles;

    }

    /**
     * Flush and close the working log file. On return, the workingLogFileWriter stream will be invalid.
     */
    void finalizeWorkingLogFile() {
        try {
            workingFileLock.lock();
            workingLogFileWriter.get().flush();
            workingLogFileWriter.get().close();
            workingLogFileWriter.set(null);

        } catch (Exception e) {
            log.error(e.toString());
        } finally {
            workingFileLock.unlock();
        }
    }

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
            closedLogFile = rollLogfile(workingLogFile);
            workingLogFile = getWorkingLogfile();
            resetWorkingLogFile();

            if (AgentConfiguration.getInstance().getLogReportingConfiguration().isSampled()) {
                closedLogFile.setReadOnly();
            } else {
                closedLogFile.delete();
            }

            log.debug("LogReporter: Finalized log data to [" + closedLogFile.getAbsolutePath() + "]");

        } finally {
            workingFileLock.unlock();
        }

        return closedLogFile;
    }

    /**
     * Reset and prepare the working log file and output stream for incoming data.
     *
     * @return
     * @throws IOException
     */
    BufferedWriter resetWorkingLogFile() throws IOException {
        workingLogFile = getWorkingLogfile();

        // BufferedWriter for performance, true to set append to file flag
        workingLogFileWriter.set(new BufferedWriter(new FileWriter(workingLogFile, true)));
        payloadBudget = VORTEX_PAYLOAD_LIMIT;

        return workingLogFileWriter.get();
    }

    /**
     * Move validated log data from a logger into teh working log file.
     *
     * @param logDataMap A map of verified log data attributes. Updates the Vortex payload budget,
     *                   and rolls the working log if aggregated *uncompressed* log data exceeds
     *                   the limit (1MB).
     * @throws IOException
     */
    public void appendToWorkingLogFile(Map<String, Object> logDataMap) throws IOException {
        try {
            String logJsonData = gson.toJson(logDataMap, gtype);

            workingFileLock.lock();

            if (null != workingLogFileWriter.get()) {
                // Check Vortex limits prior to writing
                payloadBudget -= (logJsonData.length() + System.lineSeparator().length());
                if (0 > payloadBudget) {
                    finalizeWorkingLogFile();
                    rollWorkingLogFile();
                }

                workingLogFileWriter.get().append(logJsonData);
                workingLogFileWriter.get().newLine();

            } else {
                // the writer has closed, usually a result of the agent stopping
            }

        } finally {
            workingFileLock.unlock();
        }
    }

    void shutdown() {
        if (isStarted.get()) {
            stop();
        }
        log.info("LogReporting: reporter instance has been shutdown");
    }


    // helpers

    Map<String, String> logFileNameAsParts(final File logDataFile) {
        final Map<String, String> parts = new HashMap<>();
        final Matcher matcher = LOG_FILE_REGEX.matcher(logDataFile.getAbsolutePath());
        if (matcher.matches()) {
            if (3 > matcher.groupCount()) {
                log.error("LogReporter: Couldn't determine log filename components. " + logDataFile.getAbsolutePath());
            } else {
                parts.put("path", matcher.group(1));
                parts.put("file", matcher.group(2));
                parts.put("extension", matcher.group(3));
            }
        }

        return parts;
    }

    boolean isLogfileTypeOf(final File logDatafile, LogReportState state) {
        return logFileNameAsParts(logDatafile).getOrDefault("extension", "").equals(state.extension);
    }

    LogReportState typeOfLogfile(final File logDatafile) throws IOException {
        String extension = logFileNameAsParts(logDatafile).getOrDefault("extension", "");
        if (null == extension || extension.isEmpty()) {
            throw new IOException("LogReporter:  Could not parse the log file name. " + logDatafile.getAbsolutePath());
        }

        return Arrays.stream(LogReportState.values()).filter(logReportState -> logReportState.extension.equals(extension)).findFirst().get();
    }


}
