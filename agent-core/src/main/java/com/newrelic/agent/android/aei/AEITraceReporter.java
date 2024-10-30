/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.aei;

import com.newrelic.agent.android.Agent;
import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.harvest.DeviceInformation;
import com.newrelic.agent.android.harvest.Harvest;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.metric.MetricNames;
import com.newrelic.agent.android.payload.PayloadController;
import com.newrelic.agent.android.payload.PayloadReporter;
import com.newrelic.agent.android.payload.PayloadSender;
import com.newrelic.agent.android.stats.StatsEngine;
import com.newrelic.agent.android.util.Constants;
import com.newrelic.agent.android.util.Streams;

import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import javax.net.ssl.HttpsURLConnection;

public class AEITraceReporter extends PayloadReporter {
    protected static AtomicReference<AEITraceReporter> instance = new AtomicReference<>(null);

    static final String TRACE_DATA_DIR = "aeitrace/";
    static final String FILE_MASK = "threads-%s.dat";

    static long reportTTL = TimeUnit.SECONDS.convert(2, TimeUnit.DAYS); // thread data file expiration period (in MS)

    public static AEITraceReporter getInstance() {
        return instance.get();
    }

    File traceStore = new File(System.getProperty("java.io.tmpdir", "/tmp"), TRACE_DATA_DIR).getAbsoluteFile();

    protected final Callable reportCachedAgentDataCallable = () -> {
        postCachedAgentData();

        // shut down the reporter if all work is done, otherwise try again during the next harvest
        if (getCachedTraces().isEmpty()) {
            AEITraceReporter.shutdown();
        }
        return null;
    };

    public static AEITraceReporter initialize(File rootDir, AgentConfiguration agentConfiguration) throws IOException {
        if (!rootDir.isDirectory() || !rootDir.exists() || !rootDir.canWrite()) {
            throw new IOException("Trace reports directory [" + rootDir.getAbsolutePath() + "] must exist and be writable!");
        }

        if (instance.compareAndSet(null, new AEITraceReporter(agentConfiguration))) {
            AEITraceReporter instance = AEITraceReporter.getInstance();

            instance.traceStore = new File(rootDir, TRACE_DATA_DIR);
            instance.traceStore.mkdirs();

            if (!(instance.traceStore.exists() && instance.traceStore.canWrite())) {
                throw new IOException("AEI: Threads directory [" + rootDir.getAbsolutePath() + "] must exist and be writable!");
            }
            log.debug("AEI: saving AEI trace data to " + instance.traceStore.getAbsolutePath());

            log.debug("AEI: reporter instance initialized");
        }

        return instance.get();
    }

    protected static boolean isInitialized() {
        return instance.get() != null;
    }

    protected AEITraceReporter(AgentConfiguration agentConfiguration) {
        super(agentConfiguration);
        setEnabled((agentConfiguration.getApplicationExitConfiguration().isEnabled()));
    }

    /**
     * Start the reporter: add it as a HarvestAware listener
     * <p>
     * Once started, the reporter will run until all artifacts have been uploaded, or the app terminates.
     */
    @Override
    public void start() {
        if (isInitialized()) {
            if (isStarted.compareAndSet(false, true)) {
                Harvest.addHarvestListener(instance.get());
                isStarted.set(true);
            } else {
                log.error("AEITraceReporter: failed to initialize.");
            }
        }
    }

    /**
     * Stop the reporter: remove it from the Harvester's HarvestAware listeners
     */
    @Override
    protected void stop() {
        if (isStarted()) {
            Harvest.removeHarvestListener(instance.get());
            isStarted.set(false);
        }
    }

    public static void shutdown() {
        if (isInitialized()) {
            instance.get().stop();
            instance.set(null);
        }
    }

    public AEITrace reportAEITrace(String systrace, int pid) {
        return reportAEITrace(systrace, generateUniqueDataFilename(pid));
    }

    public AEITrace reportAEITrace(String systrace, File artifact) {
        AEITrace trace = new AEITrace(artifact);

        trace.decomposeFromSystemTrace(systrace);

        // store the report prior to upload:
        try (OutputStream artifactOs = new FileOutputStream(artifact, false)) {
            artifactOs.write(trace.toString().getBytes(StandardCharsets.UTF_8));
            artifactOs.flush();
            artifactOs.close();
            artifact.setReadOnly();

        } catch (IOException e) {
            log.debug("AEITraceReporter: AppExitInfo artifact error. " + e);
        }

        return trace;
    }

    // upload any cached agent data posts
    protected void postCachedAgentData() {
        if (isInitialized()) {
            getCachedTraces().forEach(traceDataFile -> {
                if (postAEITrace(traceDataFile)) {
                    log.info("AEI: Uploaded trace data [" + traceDataFile.getAbsolutePath() + "]");
                    if (safeDelete(traceDataFile)) {
                        log.debug("AEI: Trace data artifact[" + traceDataFile.getName() + "] removed from device");
                    }
                } else {
                    log.error("AEITraceReporter: upload failed for trace data [" + traceDataFile.getAbsolutePath() + "]");
                }
            });
        }

        // delete (drop) any traces that have aged out
        expire(Math.toIntExact(reportTTL));
    }


    /**
     * Synchronously upload trace data file to Errors ingest endpoint
     *
     * @param traceDataFile
     * @return true
     */
    boolean postAEITrace(File traceDataFile) {
        final boolean hasValidDataToken = Harvest.getHarvestConfiguration().getDataToken().isValid();

        if (hasValidDataToken) {
            try {
                if (traceDataFile.exists()) {
                    AEITraceSender traceSender = new AEITraceSender(traceDataFile, agentConfiguration);

                    switch (traceSender.call().getResponseCode()) {
                        // Upload timed-out
                        case HttpURLConnection.HTTP_CLIENT_TIMEOUT:
                            break;

                        // Payload too large:
                        case HttpURLConnection.HTTP_ENTITY_TOO_LARGE:
                            // TODO The payload was too large, despite filtering prior to upload
                            // Only solution in this case is to compress the file and retry
                            // threadDataFile = compress(logDataFile, false);
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

                    return traceSender.isSuccessfulResponse();

                } else {
                    log.warn("AEI: Trace [" + traceDataFile.getName() + "] vanished before it could be uploaded.");
                }
            } catch (Exception e) {
                AgentLogManager.getAgentLog().error("AEI: Trace upload failed: " + e);
            }
        } else {
            log.warn("AEITraceReporter: agent has not successfully connected and cannot report AEITrace. Will retry later");
        }

        return false;
    }

    /**
     * Asynchronously upload the trace data contained in the passed string.
     *
     * @param sysTrace
     * @return
     */
    protected Future postAEITrace(String sysTrace) {
        final boolean hasValidDataToken = Harvest.getHarvestConfiguration().getDataToken().isValid();

        if (hasValidDataToken) {
            if (sysTrace != null) {
                final AEITrace aeiTrace = new AEITrace().decomposeFromSystemTrace(sysTrace);
                long aeiSize = aeiTrace.toString().getBytes().length;

                if (aeiSize > Constants.Network.MAX_PAYLOAD_SIZE) {
                    DeviceInformation deviceInformation = Agent.getDeviceInformation();

                    String name = MetricNames.SUPPORTABILITY_MAXPAYLOADSIZELIMIT_ENDPOINT
                            .replace(MetricNames.TAG_FRAMEWORK, deviceInformation.getApplicationFramework().name())
                            .replace(MetricNames.TAG_DESTINATION, MetricNames.METRIC_DATA_USAGE_COLLECTOR)
                            .replace(MetricNames.TAG_SUBDESTINATION, "errors");

                    StatsEngine.SUPPORTABILITY.inc(name);

                    // delete AEITrace here?
                    // safeDelete(aeiTraceFile)
                    // log.error("Unable to upload crashes because payload is larger than 1 MB, trace report is discarded.");

                    return null;
                }

                final PayloadSender.CompletionHandler completionHandler = new PayloadSender.CompletionHandler() {

                    @Override
                    public void onResponse(PayloadSender payloadSender) {
                        if (payloadSender.isSuccessfulResponse()) {
                            // add supportability metrics
                            DeviceInformation deviceInformation = Agent.getDeviceInformation();
                            String name = MetricNames.SUPPORTABILITY_SUBDESTINATION_OUTPUT_BYTES
                                    .replace(MetricNames.TAG_FRAMEWORK, deviceInformation.getApplicationFramework().name())
                                    .replace(MetricNames.TAG_DESTINATION, MetricNames.METRIC_DATA_USAGE_COLLECTOR)
                                    .replace(MetricNames.TAG_SUBDESTINATION, "mobile_crash");

                            StatsEngine.get().sampleMetricDataUsage(name, payloadSender.getPayloadSize(), 0);
                        }
                    }

                    @Override
                    public void onException(PayloadSender payloadSender, Exception e) {
                        log.error("AEITraceReporter: AEITrace upload failed: " + e);
                    }
                };

                final AEITraceSender sender = new AEITraceSender(aeiTrace.toString(), agentConfiguration);

                if (!sender.shouldUploadOpportunistically()) {
                    log.warn("AEITraceReporter: network is unreachable. AEITrace will be uploaded on next app launch");
                }

                return PayloadController.submitPayload(sender, completionHandler);
            } else {
                log.warn("AEITraceReporter: attempted to report null AEITrace.");
            }

        } else {
            log.error("AEITraceReporter: agent has not successfully connected and cannot report AEITrace.");
        }

        return null;
    }

    @Override
    public void onHarvest() {
        postCachedAgentData();

        // shut down the reporter if all work is done, otherwise try again during the next harvest
        if (getCachedTraces().isEmpty()) {
            AEITraceReporter.shutdown();
        }

        // PayloadController.submitCallable(reportCachedAgentDataCallable);
    }

    /**
     * Return the current collection of trace artifacts
     */
    protected Set<File> getCachedTraces() {
        Set<File> reportSet = new HashSet<>();

        try {
            String fileMask = String.format(Locale.getDefault(), FILE_MASK, "\\d+");
            reportSet = Streams.list(traceStore)
                    .filter(file -> file.isFile() && file.getName().matches(fileMask))
                    .collect(Collectors.toSet());

        } catch (Exception e) {
            log.error("AEI:Can't query cached log reports: " + e);
        }

        return reportSet;
    }

    /**
     * Give expired thread data file one last chance to upload, then safely delete.
     * This should be run at least once per session.
     *
     * @param expirationTTL Time in milliseconds since the file was last modified
     */
    Set<File> expire(long expirationTTL) {
        FileFilter expirationFilter = traceFile -> (traceFile.exists() &&
                (traceFile.lastModified() + expirationTTL) < System.currentTimeMillis());

        Set<File> expiredFiles = Streams.list(traceStore, expirationFilter).collect(Collectors.toSet());

        expiredFiles.forEach(threadData -> {
            log.debug("AEI:Thread data [" + threadData.getName() + "] has expired and will be removed.");
            safeDelete(threadData);
        });

        return expiredFiles;
    }

    boolean safeDelete(File fileToDelete) {
        fileToDelete.setReadOnly();
        fileToDelete.delete();

        return !fileToDelete.exists();
    }

    /**
     * Create a new filename for the trace data artifacts
     *
     * @return Unique filename
     */
    File generateUniqueDataFilename(int pid) {
        File traceFile;
        int retries = 5;
        do {
            traceFile = new File(traceStore, String.format(Locale.getDefault(), FILE_MASK, pid));

        } while (traceFile.exists() && 0 < traceFile.length() && retries-- > 0);

        return traceFile.exists() ? null : traceFile;
    }

}
