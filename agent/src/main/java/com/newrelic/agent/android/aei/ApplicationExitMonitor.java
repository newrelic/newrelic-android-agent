/*
 * Copyright (c) 2024. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.aei;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.os.Build;
import android.os.Process;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.analytics.AnalyticsAttribute;
import com.newrelic.agent.android.analytics.AnalyticsControllerImpl;
import com.newrelic.agent.android.analytics.AnalyticsEvent;
import com.newrelic.agent.android.harvest.Harvest;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.metric.MetricNames;
import com.newrelic.agent.android.stats.StatsEngine;
import com.newrelic.agent.android.util.Streams;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ApplicationExitMonitor {
    private static final AgentLog log = AgentLogManager.getAgentLog();

    static final String SESSION_ID_MAPPING_STORE = "sessionMeta.map";
    static final String ARTIFACT_NAME = "aei-%s.dat";

    protected final File reportsDir;
    protected final String packageName;
    protected final AEISessionMapper sessionMapper;
    protected final ActivityManager am;
    protected final AEITraceReporter traceReporter;

    private static final Map<Integer, String> REASON_MAP = new HashMap<>();
    private static final Map<Integer, String> IMPORTANCE_MAP = new HashMap<>();

    static {
        REASON_MAP.put(0, "Unknown");
        REASON_MAP.put(1, "Exit self");
        REASON_MAP.put(2, "Signaled");
        REASON_MAP.put(3, "Low memory");
        REASON_MAP.put(4, "Crash");
        REASON_MAP.put(5, "Native crash");
        REASON_MAP.put(6, "ANR");
        REASON_MAP.put(7, "Initialization failure");
        REASON_MAP.put(8, "Permission change");
        REASON_MAP.put(9, "Excessive resource usage");
        REASON_MAP.put(10, "User requested");
        REASON_MAP.put(11, "User stopped");
        REASON_MAP.put(12, "Dependency died");
        REASON_MAP.put(13, "Other");
        REASON_MAP.put(14, "Freezer");
        REASON_MAP.put(15, "Package state changed");
        REASON_MAP.put(16, "Package updated");

        IMPORTANCE_MAP.put(ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND, "Foreground");
        IMPORTANCE_MAP.put(ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE, "Foreground service");
        IMPORTANCE_MAP.put(ActivityManager.RunningAppProcessInfo.IMPORTANCE_TOP_SLEEPING, "Top sleeping");
        IMPORTANCE_MAP.put(ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE, "Visible");
        IMPORTANCE_MAP.put(ActivityManager.RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE, "Perceptible");
        IMPORTANCE_MAP.put(ActivityManager.RunningAppProcessInfo.IMPORTANCE_CANT_SAVE_STATE, "Can't save state");
        IMPORTANCE_MAP.put(ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE, "Service");
        IMPORTANCE_MAP.put(ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED, "Cached");
        IMPORTANCE_MAP.put(ActivityManager.RunningAppProcessInfo.IMPORTANCE_GONE, "Gone");
    }


    public ApplicationExitMonitor(final Context context) {
        this.reportsDir = new File(context.getCacheDir(), "newrelic/applicationExitInfo");
        this.packageName = context.getPackageName();
        this.sessionMapper = new AEISessionMapper(new File(reportsDir, SESSION_ID_MAPPING_STORE));
        this.am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);

        reportsDir.mkdirs();

        AEITraceReporter traceReporter = AEITraceReporter.getInstance();
        try {
            traceReporter = AEITraceReporter.initialize(reportsDir, AgentConfiguration.getInstance());
            if (traceReporter != null) {
                traceReporter.start();
                if (!traceReporter.isStarted()) {
                    log.warn("ApplicationExitMonitor: AEI trace reporter not started. AEITrace reporting will be disabled.");
                }
            } else {
                log.warn("ApplicationExitMonitor: No AEI trace reporter. AEITrace reporting will be disabled.");
            }

        } catch (IOException e) {
            log.error("ApplicationExitMonitor: " + e);
        }

        this.traceReporter = traceReporter;
    }

    // a getter useful in mock tests
    int getCurrentProcessId() {
        return Process.myPid();
    }

    /**
     * Gather application exist status reports for this process
     **/
    @SuppressLint("SwitchIntDef")
    @SuppressWarnings("deprecation")
    public void harvestApplicationExitInfo() {
        sessionMapper.load();

        // Only supported in Android 11+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            AtomicInteger recordsVisited = new AtomicInteger(0);
            AtomicInteger recordsSkipped = new AtomicInteger(0);
            AtomicInteger recordsDropped = new AtomicInteger(0);

            if (null == am) {
                log.error("harvestApplicationExitInfo: ActivityManager is null! Cannot record ApplicationExitInfo data.");
                return;
            }

            // we are reporting all reasons
            final List<android.app.ApplicationExitInfo> applicationExitInfoList =
                    am.getHistoricalProcessExitReasons(packageName, 0, 0);

            // the set may contain more than one report for this package name
            for (ApplicationExitInfo exitInfo : applicationExitInfoList) {
                File artifact = new File(reportsDir, String.format(Locale.getDefault(), ARTIFACT_NAME,
                        exitInfo.getPid()));

                // If an artifact for this pid exists, it's been recorded already
                if (artifact.exists() && (artifact.length() > 0)) {
                    log.debug("ApplicationExitMonitor: skipping exit info for pid[" +
                            exitInfo.getPid() + "]: already recorded.");
                    recordsSkipped.incrementAndGet();
                    continue;
                }

                // try to map the AEI with the session it occurred in
                String aeiSessionId = sessionMapper.getSessionId(exitInfo.getPid());
                if (!(aeiSessionId == null || aeiSessionId.isEmpty() || aeiSessionId.equals(AgentConfiguration.getInstance().getSessionID()))) {
                    // found a prior session ID
                    log.debug("ApplicationExitMonitor: Found session id [" + aeiSessionId + "] for AEI pid[" + exitInfo.getPid() + "]");
                }

                String traceReport = exitInfo.toString();

                // remove any empty files
                if (artifact.exists() && artifact.length() == 0) {
                    artifact.delete();
                }

                // write a marker so we don't inspect this record again (over-reporting)
                try (OutputStream artifactOs = new FileOutputStream(artifact, false)) {

                    if (null != exitInfo.getTraceInputStream()) {
                        try (InputStream traceIs = exitInfo.getTraceInputStream()) {
                            traceReport = Streams.slurpString(traceIs);
                        } catch (IOException e) {
                            log.info("ApplicationExitMonitor: " + e);
                        }
                    }

                    artifactOs.write(traceReport.getBytes(StandardCharsets.UTF_8));
                    artifactOs.flush();
                    artifactOs.close();
                    artifact.setReadOnly();

                    recordsVisited.incrementAndGet();

                } catch (IOException e) {
                    log.debug("harvestApplicationExitInfo: AppExitInfo artifact error. " + e);
                }

                // try to map the AEI with the session it occurred in
                AEISessionMapper.AEISessionMeta sessionMeta = sessionMapper.get(exitInfo.getPid());

                // we are not dropping it if the session is not found, we will still report it
//                if (sessionMeta == null || !sessionMeta.isValid() || sessionMeta.sessionId.equals(AgentConfiguration.getInstance().getSessionID())) {
//                    // No previous session ID found in cache. Can't do anything with the event, so drop it
//                    recordsDropped.incrementAndGet();
//                    continue;
//                }

                // found a prior session ID
                if(sessionMeta != null) {
                    log.debug("ApplicationExitMonitor: Using session meta [" + sessionMeta.sessionId + ", " + sessionMeta.realAgentId + "] for AEI pid[" + exitInfo.getPid() + "]");
                }

                // finally, emit an event for the record
                final HashMap<String, Object> eventAttributes;
                try {
                    eventAttributes = getEventAttributesForAEI(exitInfo, sessionMeta, traceReport);
                } catch (UnsupportedEncodingException e) {
                    throw new RuntimeException(e);
                }

                StatsEngine.SUPPORTABILITY.inc(MetricNames.SUPPORTABILITY_AEI_EXIT_STATUS + exitInfo.getStatus());
                StatsEngine.SUPPORTABILITY.inc(MetricNames.SUPPORTABILITY_AEI_EXIT_BY_REASON + getReasonAsString(exitInfo.getReason()));
                StatsEngine.SUPPORTABILITY.inc(MetricNames.SUPPORTABILITY_AEI_EXIT_BY_IMPORTANCE + getImportanceAsString(exitInfo.getImportance()));
                StatsEngine.SUPPORTABILITY.sample(MetricNames.SUPPORTABILITY_AEI_VISITED, recordsVisited.get());
                StatsEngine.SUPPORTABILITY.sample(MetricNames.SUPPORTABILITY_AEI_SKIPPED, recordsSkipped.get());
//                StatsEngine.SUPPORTABILITY.sample(MetricNames.SUPPORTABILITY_AEI_DROPPED, recordsDropped.get());

                final AnalyticsControllerImpl analyticsController = AnalyticsControllerImpl.getInstance();
                Error error = new Error(analyticsController.getSessionAttributes(),eventAttributes,sessionMeta);

                traceReporter.reportAEITrace(error.asJsonObject().toString(),exitInfo.getPid());
            }
            log.debug("AEI: inspected [" + applicationExitInfoList.size() + "] records: new[" + recordsVisited.get() + "] existing [" + recordsSkipped.get() + "] dropped[" + recordsDropped.get() + "]");

            AEISessionMapper.AEISessionMeta model = new AEISessionMapper.AEISessionMeta(AgentConfiguration.getInstance().getSessionID(), Harvest.getHarvestConfiguration().getDataToken().getAgentId());
            sessionMapper.put(getCurrentProcessId(), model);
            sessionMapper.flush();

            // sync the cache dir and session mapper
            reconcileMetadata(applicationExitInfoList);

        } else {
            log.warn("ApplicationExitMonitor: exit info reporting was enabled, but not supported by the current OS");
            StatsEngine.SUPPORTABILITY.inc(MetricNames.SUPPORTABILITY_AEI_UNSUPPORTED_OS + Build.VERSION.SDK_INT);
        }
    }

    /**
     * Create an event for the AEI record and return the attributes
     **/
    @RequiresApi(api = Build.VERSION_CODES.R)
    @NonNull
    protected HashMap<String, Object> getEventAttributesForAEI(ApplicationExitInfo exitInfo, AEISessionMapper.AEISessionMeta sessionMeta, String traceReport) throws UnsupportedEncodingException {
        final HashMap<String, Object> eventAttributes = new HashMap<>();

            eventAttributes.put(AnalyticsAttribute.APP_EXIT_TIMESTAMP_ATTRIBUTE, exitInfo.getTimestamp());

            eventAttributes.put(AnalyticsAttribute.APP_EXIT_REASON_ATTRIBUTE, exitInfo.getReason());
            eventAttributes.put(AnalyticsAttribute.APP_EXIT_IMPORTANCE_ATTRIBUTE, exitInfo.getImportance());
            eventAttributes.put(AnalyticsAttribute.APP_EXIT_IMPORTANCE_STRING_ATTRIBUTE, getImportanceAsString(exitInfo.getImportance()));
            eventAttributes.put(AnalyticsAttribute.APP_EXIT_DESCRIPTION_ATTRIBUTE, toValidAttributeValue(exitInfo.getDescription()));
            eventAttributes.put(AnalyticsAttribute.APP_EXIT_PROCESS_NAME_ATTRIBUTE, toValidAttributeValue(exitInfo.getProcessName()));

            // map the AEI with the session it occurred in (will be translated later)
            if(sessionMeta != null) {
                eventAttributes.put(AnalyticsAttribute.SESSION_ID_ATTRIBUTE, sessionMeta.sessionId);
            }
            eventAttributes.put(AnalyticsAttribute.APP_EXIT_ID_ATTRIBUTE, UUID.randomUUID().toString());
            eventAttributes.put(AnalyticsAttribute.APP_EXIT_PROCESS_ID_ATTRIBUTE, exitInfo.getPid());
            eventAttributes.put(AnalyticsAttribute.EVENT_TYPE_ATTRIBUTE, AnalyticsEvent.EVENT_TYPE_MOBILE_APPLICATION_EXIT);

            // Add fg/bg flag based on inferred importance:
            switch (exitInfo.getImportance()) {
                case ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND:
                case ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE:
                case ActivityManager.RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE:
                case ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE:
                case ActivityManager.RunningAppProcessInfo.IMPORTANCE_TOP_SLEEPING:
                    eventAttributes.put(AnalyticsAttribute.APP_EXIT_APP_STATE_ATTRIBUTE, "foreground");
                    break;
                default:
                    eventAttributes.put(AnalyticsAttribute.APP_EXIT_APP_STATE_ATTRIBUTE, "background");
                    break;
            }

            // Add the reason for the exit
            if (exitInfo.getReason() == ApplicationExitInfo.REASON_ANR) {
                AEITrace aeiTrace = new AEITrace();
                aeiTrace.decomposeFromSystemTrace(traceReport);
                eventAttributes.put(AnalyticsAttribute.APP_EXIT_THREADS_ATTRIBUTE, URLEncoder.encode(aeiTrace.toString(), StandardCharsets.UTF_8.toString()));
                eventAttributes.put(AnalyticsAttribute.APP_EXIT_FINGERPRINT_ATTRIBUTE, Build.FINGERPRINT);

        }
        return eventAttributes;
    }

    @RequiresApi(api = Build.VERSION_CODES.R)
    public Set<Integer> currentPidSet(List<ApplicationExitInfo> applicationExitInfoList) {
        return applicationExitInfoList.stream()
                .mapToInt(applicationExitInfo -> applicationExitInfo.getPid())
                .boxed()
                .collect(Collectors.toSet());
    }

    /**
     * For each existing artifact, if the pid contained in the filename doesn't exist in
     * the passed AEI record set, delete the artifact.
     *
     * @param applicationExitInfoList List of records reported from ART
     **/
    @RequiresApi(api = Build.VERSION_CODES.R)
    void reconcileMetadata(List<ApplicationExitInfo> applicationExitInfoList) {
        List<File> artifacts = getArtifacts();
        Pattern regexp = Pattern.compile(String.format(Locale.getDefault(), ARTIFACT_NAME, "(\\d+)"));
        Set<Integer> currentPids = currentPidSet(applicationExitInfoList);

        artifacts.forEach(aeiArtifact -> {
            Matcher matcher = regexp.matcher(aeiArtifact.getName());
            if (matcher.matches()) {
                int pid = Integer.valueOf(matcher.group(1));
                if (!currentPids.contains(pid)) {
                    aeiArtifact.delete();
                    sessionMapper.erase(pid);
                }
            }
        });

        sessionMapper.flush();
    }


    public void resetSessionMap() {
        sessionMapper.delete();
    }

    protected String toValidAttributeValue(String attributeValue) {
        return (null == attributeValue ? "null" : attributeValue.substring(0, Math.min(attributeValue.length(), AnalyticsAttribute.ATTRIBUTE_VALUE_MAX_LENGTH - 1)));
    }

    protected String getReasonAsString(int reason) {
        return REASON_MAP.getOrDefault(reason, String.valueOf(reason));
    }

    protected String getImportanceAsString(int importance) {
        return IMPORTANCE_MAP.getOrDefault(importance, String.valueOf(importance));
    }

    List<File> getArtifacts() {
        String regexp = String.format(Locale.getDefault(), ARTIFACT_NAME, "\\d+");

        return Streams.list(reportsDir)
                .filter(file -> file.isFile() && file.getName().matches(regexp))
                .collect(Collectors.toList());
    }

}
