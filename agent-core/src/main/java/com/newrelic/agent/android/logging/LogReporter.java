/*
 * Copyright (c) 2023. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.logging;

import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.payload.PayloadReporter;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class LogReporter extends PayloadReporter {

    enum LogReportState {
        WORKING(".tmp"),
        CLOSED(".dat"),
        EXPIRED(".old");

        final String value;

        LogReportState(final String suffix) {
            this.value = suffix;
        }
    }

    @SuppressWarnings("NewApi")
    private Long reportTTL = TimeUnit.DAYS.convert(3, TimeUnit.MILLISECONDS);     // log data file expiration period (in MS)

    public LogReporter(AgentConfiguration agentConfiguration) {
        super(agentConfiguration);
        onHarvestConfigurationChanged();
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
        // agent is starting: rename any working logs from previous session and queue all
        // complete logs for upload
        onHarvestBefore();
    }

    @Override
    public void onHarvestBefore() {
        // roll the current log file
        Set<File> unclosedLogReports = getCachedLogReports(LogReportState.WORKING);
        for (File logReport : unclosedLogReports) {
            if (logReport.isFile()) {
                String newReportname = logReport.getAbsolutePath().replace(LogReportState.WORKING.name(), LogReportState.CLOSED.name());
                logReport.renameTo(new File(newReportname));
            }
        }
    }

    @Override
    public void onHarvestStop() {
        // close and upload any pending reports
        onHarvest();
    }

    @Override
    public void onHarvest() {
        // TODO Sweep the log reports dir and upload to ingest
        if (isEnabled()) {
            Set<File> completedLogReports = getCachedLogReports(LogReportState.WORKING);

            // TODO Consider merging all log files here

            for (File logReport : completedLogReports) {
                if (logReport.isFile()) {
                    if (postLogReport(logReport)) {
                        log.info("Uploaded remote log data [" + logReport.getAbsoluteFile() + "]");
                        safeDelete(logReport);
                    } else {
                        log.error("Upload failed for remote log data [" + logReport.getAbsoluteFile() + "]");
                    }

                    // if log file still exists, check and remove if expiration time has passed
                    if (logReport.exists()) {
                        if ((logReport.lastModified() + reportTTL) < System.currentTimeMillis()) {
                            log.error("Remote log data [" + logReport.getAbsoluteFile() + "] has expired and will be removed.");
                            safeDelete(logReport);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void onHarvestConfigurationChanged() {
        // TODO Some aspect of logging configuration has changed. Update model...
        setEnabled(agentConfiguration.getLogReportingConfiguration().getLoggingEnabled());
        reportTTL = agentConfiguration.getLogReportingConfiguration().getExpirationPeriod();

    }

    private Set<File> getCachedLogReports(final LogReportState state) {
        HashSet<File> reportSet = new HashSet<File>();

        synchronized (this) {
            switch (state) {
                case WORKING:
                    break;
                case CLOSED:
                    break;
                case EXPIRED:
                    break;
            }
            return reportSet;
        }
    }

    private void safeDelete(File logDataFile) {
        // race condition here so rename the file prior to deleting it
        logDataFile.renameTo(new File(logDataFile.getAbsolutePath() + ".old"));
        logDataFile.delete();
    }

    boolean postLogReport(File logDataFile) {
        boolean uploaded = false;

        if (uploaded) {
            safeDelete(logDataFile);
        }

        return uploaded;
    }

    /**
     * Get or create a file suitable for receiving log data. Currently we use a single file
     * suitable for thread-safe i/o. When the file is closed it will be renamed to a file
     * using a timestamped filename.
     *
     * @return Working log data file.
     * @throws IOException
     */
    static File getWorkingLogfile() throws IOException {
        File logFile = new File(LogForwarding.logDataStore,
                String.format(Locale.getDefault(),
                        LogForwarding.LOG_FILE_MASK,
                        "",
                        LogReporter.LogReportState.WORKING.value));

        logFile.getParentFile().mkdirs();
        if (!logFile.exists()) {
            logFile.createNewFile();
        }

        return logFile;
    }


    /**
     * Rename the passed working file to a timestamped filename, ready to be picked up by the log forwarder.
     *
     * @param l
     * @return The renamed (timestamped) working file
     * @throws IOException
     */
    static File rollLogfile(File l) throws IOException {
        File closedLogFile = new File(LogForwarding.logDataStore,
                String.format(Locale.getDefault(),
                        LogForwarding.LOG_FILE_MASK,
                        String.valueOf(System.currentTimeMillis()),
                        LogReportState.CLOSED.value));

        l.renameTo(closedLogFile);

        return closedLogFile;
    }
}
