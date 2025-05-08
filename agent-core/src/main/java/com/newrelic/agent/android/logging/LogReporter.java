/*
 * Copyright (c) 2023. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.logging;

import static com.newrelic.agent.android.logging.LogReporting.LOG_PAYLOAD_ATTRIBUTES_ATTRIBUTE;
import static com.newrelic.agent.android.logging.LogReporting.LOG_PAYLOAD_COMMON_ATTRIBUTE;
import static com.newrelic.agent.android.logging.LogReporting.LOG_PAYLOAD_LOGS_ATTRIBUTE;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.ApplicationFramework;
import com.newrelic.agent.android.analytics.AnalyticsAttribute;
import com.newrelic.agent.android.analytics.AnalyticsControllerImpl;
import com.newrelic.agent.android.harvest.Harvest;
import com.newrelic.agent.android.harvest.HarvestLifecycleAware;
import com.newrelic.agent.android.metric.MetricNames;
import com.newrelic.agent.android.payload.PayloadReporter;
import com.newrelic.agent.android.stats.StatsEngine;
import com.newrelic.agent.android.util.Streams;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
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
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;

import javax.net.ssl.HttpsURLConnection;


public class LogReporter extends PayloadReporter {

    protected static final Type gtype = new TypeToken<Map<String, Object>>() {
    }.getType();
    protected static final Gson gson = new GsonBuilder()
            .enableComplexMapKeySerialization()
            .create();

    enum LogReportState {
        WORKING("tmp"),     // Contains data from the active logging session
        CLOSED("dat"),      // Contains a single log session, limited by Vortex payload size
        ROLLUP("rollup"),   // Contains a JsonArray of closed log data files
        EXPIRED("bak"),     // Contains expired or backup data ready to be deleted
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

    static final String LOG_REPORTS_DIR = "newrelic/logReporting";      // root dir for local data files
    static final String LOG_FILE_MASK = "logdata%s.%s";                 // log data file name. suffix will indicate working state
    static final Pattern LOG_FILE_REGEX = Pattern.compile("^(.*\\/" + LOG_REPORTS_DIR + ")\\/(logdata.*)\\.(.*)$");

    static final AtomicReference<LogReporter> instance = new AtomicReference<>(null);
    static final ReentrantLock workingFileLock = new ReentrantLock();

    static File logDataStore = new File(System.getProperty("java.io.tmpdir", "/tmp"), LOG_REPORTS_DIR).getAbsoluteFile();

    protected long reportTTL = LogReportingConfiguration.DEFAULT_EXPIRATION_PERIOD;     // log data file expiration period (in MS)
    protected File workingLogfile;
    protected AtomicReference<BufferedWriter> workingLogfileWriter = new AtomicReference<>(null);   // lazy initialized

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

        StatsEngine.SUPPORTABILITY.inc(MetricNames.SUPPORTABILITY_LOG_REPORTING_INIT);
        StatsEngine.SUPPORTABILITY.inc(MetricNames.SUPPORTABILITY_LOG_SAMPLED + (agentConfiguration.getLogReportingConfiguration().isSampled() ? "true" : "false"));

        return instance.get();
    }

    public static LogReporter getInstance() {
        return instance.get();
    }


    public LogReporter(AgentConfiguration agentConfiguration) {
        super(agentConfiguration);
        setEnabled((agentConfiguration.getLogReportingConfiguration().getLoggingEnabled()));
        try {
            resetWorkingLogfile();
        } catch (IOException e) {
            log.error("LogReporter error: " + e);
            setEnabled(false);
        }
    }

    @Override
    protected void start() {
        if (isEnabled()) {
            Harvest.addHarvestListener(instance.get());
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

        workingLogfileWriter.set(null);
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
    public void onHarvestConnected() {
        //submit what left when the app was terminated last time
        processLogs();
    }

    @Override
    public void onHarvest() {
        processLogs();
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
        return getCachedLogReports(state.extension);
    }

    protected Set<File> getCachedLogReports(final String extension) {
        Set<File> reportSet = new HashSet<>();

        try {
            String logFileMask = String.format(Locale.getDefault(),
                    LogReporter.LOG_FILE_MASK, ".*", extension);

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
                if (file.length() >= LogReporter.VORTEX_PAYLOAD_LIMIT) {
                    decompose(file);
                    continue;
                }

                if (null != file && file.exists() && file.length() > 0) {
                    try {
                        // truncate at payload size limit. Test first so we don't overflow the budget
                        payloadSizeBudget -= file.length();
                        if (0 > payloadSizeBudget) {
                            break;
                        }

                        logfileToJsonArray(file, jsonArray);

                        // remove the completed file(s)
                        mergedFiles.add(file);

                    } catch (Exception e) {
                        log.error("LogReporter: " + e.toString());
                    }
                }
            }

            if (jsonArray.size() > 0) {
                File archivedLogfile = generateUniqueLogfile(LogReportState.ROLLUP);

                archivedLogfile.mkdirs();
                archivedLogfile.delete();
                archivedLogfile.createNewFile();

                try {
                    jsonArrayToLogfile(jsonArray, archivedLogfile);

                } catch (Exception e) {
                    log.error("Log file rollup failed: " + e);
                }

                mergedFiles.forEach(file -> safeDelete(file));

                return archivedLogfile;
            }

        } catch (IOException e) {
            log.error(e.toString());

        } finally {
            workingFileLock.unlock();
        }

        return null;
    }

    void processLogs() {
        try {
            final Logger logger = LogReporting.getLogger();
            if (logger instanceof HarvestLifecycleAware) {
                ((HarvestLifecycleAware) logger).onHarvest();
            }

            workingFileLock.lock();

            // roll the log only if data has been added to the working file
            workingLogfileWriter.get().flush();
            if (workingLogfile.length() > LogReporter.MIN_PAYLOAD_THRESHOLD) {
                finalizeWorkingLogfile();
                rollWorkingLogfile();
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
                            // Only solution in this case is to compress the file and retry, or
                            // decompose and redistribute the payload,
                            // logDataFile = compress(logDataFile, false);
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

    boolean safeDelete(File fileToDelete) {
        // Potential race condition here so rename the file rather than deleting it.
        // Expired files will be removed during an expiration sweep.
        // 5/8/25 Delete expired file due to memory issues
        if (!isLogfileTypeOf(fileToDelete, LogReportState.EXPIRED)) {
            fileToDelete.delete();
        }

        return !fileToDelete.exists();
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
     * Create a new filename for the log data artifact, based on the state of the report
     *
     * @param state State of log data file
     * @return Unique filename
     */
    static File generateUniqueLogfile(LogReportState state) {
        File closedLogfile;
        int retries = 5;

        do {
            closedLogfile = new File(LogReporter.logDataStore,
                    String.format(Locale.getDefault(),
                            LogReporter.LOG_FILE_MASK,
                            UUID.randomUUID(),
                            state.extension));

        } while (closedLogfile.exists() && 0 < closedLogfile.length() && retries-- > 0);

        return closedLogfile;
    }

    /**
     * Rename the passed working file to a timestamped filename, ready to be picked up by the log forwarder.
     *
     * @param workingLogfile
     * @return The renamed (timestamped) working file
     * @throws IOException
     */
    File rollLogfile(File workingLogfile) {
        File closedLogfile = generateUniqueLogfile(LogReportState.CLOSED);

        workingLogfile.renameTo(closedLogfile);
        closedLogfile.setLastModified(System.currentTimeMillis());

        return closedLogfile;
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
     * Decompose large log data files in half (2 parts), then delete the original if the sum
     * of parts equals the original file. Data from the file is decomposed into rollup files
     * smaller than the Vortex payload limit.
     *
     * @param logDataFile
     * @return Set containing new files
     * @throws IOException
     */
    Set<File> decompose(File logDataFile) throws IOException {
        if (logDataFile.length() > VORTEX_PAYLOAD_LIMIT) {
            final Set<File> splitFiles = new HashSet<>();
            JsonArray jsonArray = new JsonArray();

            switch (typeOfLogfile(logDataFile)) {
                case CLOSED:
                    jsonArray = logfileToJsonArray(logDataFile).get(0).getAsJsonObject().get(LOG_PAYLOAD_LOGS_ATTRIBUTE).getAsJsonArray();
                    break;

                case ROLLUP:
                    jsonArray = LogReporter.gson.fromJson(Streams.slurpString(logDataFile, null), JsonArray.class).get(0).getAsJsonObject().get(LOG_PAYLOAD_LOGS_ATTRIBUTE).getAsJsonArray();
                    break;
            }

            if (!jsonArray.isEmpty()) {
                int splitSize = (jsonArray.size() / 2);
                int logDataMsgs = jsonArray.size();
                JsonArray splitArray = new JsonArray();

                for (JsonElement jsonElement : jsonArray) {
                    splitArray.add(jsonElement);
                    if (splitArray.size() > splitSize) {
                        splitFiles.add(jsonArrayToLogfile(splitArray, generateUniqueLogfile(LogReportState.ROLLUP)));
                        logDataMsgs -= splitArray.size();
                        splitArray = new JsonArray();
                    }
                }

                if (!splitArray.isEmpty()) {
                    splitFiles.add(jsonArrayToLogfile(splitArray, generateUniqueLogfile(LogReportState.ROLLUP)));
                    logDataMsgs -= splitArray.size();
                }

                if (0 == logDataMsgs) {
                    logDataFile.delete();
                }

            }

            return splitFiles;
        }

        return Set.of();
    }

    /**
     * GZIP compress the passed file.
     *
     * @param logDatFile File to be compressed
     * @param replace    Delete the passed file, rename the compressed file to the pass file name
     * @return File of new compressed file
     * @throws IOException
     */
    File compress(final File logDatFile, boolean replace) throws IOException {
        File compressedFile = new File(logDatFile.getAbsolutePath() + ".gz");

        try (FileInputStream fis = new FileInputStream(logDatFile);
             FileOutputStream fos = new FileOutputStream(compressedFile);
             GZIPOutputStream gzOut = new GZIPOutputStream(fos, Streams.DEFAULT_BUFFER_SIZE, true)) {
            Streams.copy(fis, gzOut);
            gzOut.flush();

            if (replace && logDatFile.delete()) {
                // compressedFile.renameTo(logDatFile);
            }
        }

        return compressedFile;
    }

    /**
     * Remove log data files that have been "deleted" by other operations. We don't really delete files once
     * used, but rather rename them as a backup. This gives us a window where data can be salvaged as neccesary.
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
     * Flush and close the working log file. On return, the workingLogfileWriter stream will be invalid.
     */
    void finalizeWorkingLogfile() {
        try {
            workingFileLock.lock();
            workingLogfileWriter.get().flush();
            workingLogfileWriter.get().close();
            workingLogfileWriter.set(null);

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
    File rollWorkingLogfile() throws IOException {
        File closedLogfile;

        try {
            workingFileLock.lock();
            closedLogfile = rollLogfile(workingLogfile);
            workingLogfile = getWorkingLogfile();
            resetWorkingLogfile();

            if (AgentConfiguration.getInstance().getLogReportingConfiguration().isSampled()) {
                closedLogfile.setReadOnly();
            } else {
                closedLogfile.delete();
            }

            log.debug("LogReporter: Finalized log data to [" + closedLogfile.getAbsolutePath() + "]");

        } finally {
            workingFileLock.unlock();
        }

        return closedLogfile;
    }

    /**
     * Reset and prepare the working log file and output stream for incoming data.
     *
     * @return
     * @throws IOException
     */
    BufferedWriter resetWorkingLogfile() throws IOException {
        workingLogfile = getWorkingLogfile();

        // BufferedWriter for performance, true to set append to file flag
        workingLogfileWriter.set(new BufferedWriter(new FileWriter(workingLogfile, true)));
        payloadBudget = VORTEX_PAYLOAD_LIMIT;

        return workingLogfileWriter.get();
    }

    static Map<String, Object> getCommonBlockAttributes() {
        Map<String, Object> attrs = new HashMap<>();

        attrs.put(LogReporting.LOG_ENTITY_ATTRIBUTE, AgentConfiguration.getInstance().getEntityGuid());
        attrs.put(LogReporting.LOG_SESSION_ID, AgentConfiguration.getInstance().getSessionID());
        attrs.put(LogReporting.LOG_INSTRUMENTATION_PROVIDER, LogReporting.LOG_INSTRUMENTATION_PROVIDER_ATTRIBUTE);
        attrs.put(LogReporting.LOG_INSTRUMENTATION_NAME, AgentConfiguration.getInstance().getApplicationFramework().equals(ApplicationFramework.Native) ? LogReporting.LOG_INSTRUMENTATION_ANDROID_NAME : AgentConfiguration.getInstance().getApplicationFramework().name());
        attrs.put(LogReporting.LOG_INSTRUMENTATION_VERSION, AgentConfiguration.getInstance().getApplicationFrameworkVersion());
        attrs.put(LogReporting.LOG_INSTRUMENTATION_COLLECTOR_NAME, LogReporting.LOG_INSTRUMENTATION_ANDROID_NAME);

        // adding session attributes
        final AnalyticsControllerImpl analyticsController = AnalyticsControllerImpl.getInstance();

        Map<String, Object> sessionAttributes = new HashMap<>();
        for (AnalyticsAttribute analyticsAttribute : analyticsController.getSessionAttributes()) {
            sessionAttributes.put(analyticsAttribute.getName(), analyticsAttribute.asJsonElement());
        }
        attrs.putAll(sessionAttributes);

        return attrs;
    }


    /**
     * Move validated log data from a logger into teh working log file.
     *
     * @param logDataMap A map of verified log data attributes. Updates the Vortex payload budget,
     *                   and rolls the working log if aggregated *uncompressed* log data exceeds
     *                   the limit (1MB).
     * @throws IOException
     */
    public void appendToWorkingLogfile(Map<String, Object> logDataMap) throws IOException {
        try {
            String logJsonData = gson.toJson(logDataMap, gtype);

            workingFileLock.lock();

            if (null != workingLogfileWriter.get()) {
                // Check Vortex limits prior to writing
                payloadBudget -= (logJsonData.length() + System.lineSeparator().length());
                if (0 > payloadBudget) {
                    finalizeWorkingLogfile();
                    rollWorkingLogfile();
                }

                workingLogfileWriter.get().append(logJsonData);
                workingLogfileWriter.get().newLine();

            } else {
                // the writer has closed, usually a result of the agent stopping
            }

        } finally {
            workingFileLock.unlock();
        }
    }

    /**
     * Shutdown the reporter. Remove from HarvestLifecycle notifications.
     */
    void shutdown() {
        if (isStarted.get()) {
            stop();
        }
        log.info("LogReporting: reporter instance has been shutdown");
    }

    // helpers

    /**
     * Decompose the logdata filename into parts
     *
     * @param logDataFile
     * @return Map of filename parts [path, file, extension]
     */
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

    /**
     * Return true if logdata file represents an instance of a LogReportState
     *
     * @param logDatafile
     * @param state       Log data state
     * @return
     */
    boolean isLogfileTypeOf(final File logDatafile, LogReportState state) {
        return logFileNameAsParts(logDatafile).getOrDefault("extension", "").equals(state.extension);
    }

    /**
     * Determines LogReportSTate type of logdata filename
     *
     * @param logDatafile Filename
     * @return
     * @throws IOException
     */
    LogReportState typeOfLogfile(final File logDatafile) throws IOException {
        String extension = logFileNameAsParts(logDatafile).getOrDefault("extension", "");
        if (null == extension || extension.isEmpty()) {
            throw new IOException("LogReporter:  Could not parse the log file name. " + logDatafile.getAbsolutePath());
        }

        return Arrays.stream(LogReportState.values()).filter(logReportState -> logReportState.extension.equals(extension)).findFirst().get();
    }

    /**
     * Deserialize log file contents into a new JsonArray, validating each record as it is read.
     *
     * @param logfile Assumed to contain a valid JsonElements
     * @return All JsonElements as JsonArray
     * @throws IOException
     */
    static JsonArray logfileToJsonArray(File logfile) throws IOException {
        return logfileToJsonArray(logfile, new JsonArray());
    }

    /**
     * Deserialize log file contents into a passed JsonArray, validating each record as it is read.
     * Invalid Json records are dropped. (TODO add metric)
     * The input file size is limited to LogReporter.VORTEX_PAYLOAD_LIMIT (1MB). Larger files
     * should be decomposed() beforehand.
     *
     * @param logfile   Assumed to contain a valid JsonElements
     * @param jsonArray JsonArray to receive log data json
     * @return passed JsonArray
     * @throws IOException
     */


    static JsonArray logfileToJsonArray(File logfile, JsonArray jsonArray) throws IOException {
        JsonArray logsJsonArray = new JsonArray();
        JsonObject logsJson = new JsonObject();
        //add Shared attributes
        JsonObject sharedAttributes = LogReporter.gson.toJsonTree(getCommonBlockAttributes()).getAsJsonObject();
        JsonObject attributes = new JsonObject();
        attributes.add(LOG_PAYLOAD_ATTRIBUTES_ATTRIBUTE, sharedAttributes);
        logsJson.add(LOG_PAYLOAD_COMMON_ATTRIBUTE, attributes);
        try (BufferedReader reader = Streams.newBufferedFileReader(logfile)) {
            reader.lines().forEach(s -> {
                if (!(null == s || s.isEmpty())) {
                    try {
                        JsonObject messageAsJson = LogReporter.gson.fromJson(s, JsonObject.class);
                        logsJsonArray.add(messageAsJson);
                    } catch (JsonSyntaxException e) {
                        log.error("Invalid Json entry skipped [" + s + "]");
                    }
                }
            });
        }
        logsJson.add(LOG_PAYLOAD_LOGS_ATTRIBUTE, logsJsonArray);
        jsonArray.add(logsJson);
        return jsonArray;
    }

    /**
     * Serialize JsonArray into passed logdata file name.
     *
     * @param jsonArray   JsonArray data
     * @param logDataFile Output file
     * @return
     * @throws IOException
     */
    static File jsonArrayToLogfile(JsonArray jsonArray, File logDataFile) throws IOException {
        if (null == logDataFile) {
            logDataFile = generateUniqueLogfile(LogReportState.CLOSED);
        }

        try (BufferedWriter writer = Streams.newBufferedFileWriter(logDataFile)) {
            writer.write(jsonArray.toString());
            writer.flush();
            writer.close();

            logDataFile.setReadOnly();
        }

        return logDataFile;
    }

}
