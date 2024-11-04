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

import androidx.annotation.RequiresApi;

import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.analytics.AnalyticsAttribute;
import com.newrelic.agent.android.analytics.AnalyticsControllerImpl;
import com.newrelic.agent.android.analytics.AnalyticsEvent;
import com.newrelic.agent.android.analytics.AnalyticsEventCategory;
import com.newrelic.agent.android.analytics.EventManager;
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
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ApplicationExitMonitor {
    private static final AgentLog log = AgentLogManager.getAgentLog();

    static final String SESSION_ID_MAPPING_STORE = "sessionid.map";
    static final String ARTIFACT_NAME = "aei-%s.dat";

    protected final File reportsDir;
    protected final String packageName;
    protected final SessionMapper sessionMapper;
    protected final ActivityManager am;
    protected final AEITraceReporter traceReporter;

    public ApplicationExitMonitor(final Context context) {
        this.reportsDir = new File(context.getCacheDir(), "newrelic/applicationExitInfo");
        this.packageName = context.getPackageName();
        this.sessionMapper = new SessionMapper(new File(reportsDir, SESSION_ID_MAPPING_STORE));
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

    // a gettor useful in mock tests
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
            boolean eventsAdded = false;
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
                String aeiSessionId = sessionMapper.get(exitInfo.getPid());
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
                            traceReporter.reportAEITrace(traceReport, Integer.valueOf(exitInfo.getPid()));

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

                if (aeiSessionId == null || aeiSessionId.isEmpty() || aeiSessionId.equals(AgentConfiguration.getInstance().getSessionID())) {
                    // No previous session ID found in cache. Can't do anything with the event, so drop it
                    recordsDropped.incrementAndGet();
                    continue;
                }

                // finally, emit an event for the record
                final HashMap<String, Object> eventAttributes = new HashMap<>();

                eventAttributes.put(AnalyticsAttribute.APP_EXIT_TIMESTAMP_ATTRIBUTE, exitInfo.getTimestamp());
                eventAttributes.put(AnalyticsAttribute.APP_EXIT_REASON_ATTRIBUTE, exitInfo.getReason());
                eventAttributes.put(AnalyticsAttribute.APP_EXIT_IMPORTANCE_ATTRIBUTE, exitInfo.getImportance());
                eventAttributes.put(AnalyticsAttribute.APP_EXIT_IMPORTANCE_STRING_ATTRIBUTE, getImportanceAsString(exitInfo.getImportance()));
                eventAttributes.put(AnalyticsAttribute.APP_EXIT_DESCRIPTION_ATTRIBUTE, toValidAttributeValue(exitInfo.getDescription()));
                eventAttributes.put(AnalyticsAttribute.APP_EXIT_PROCESS_NAME_ATTRIBUTE, toValidAttributeValue(exitInfo.getProcessName()));

                // map the AEI with the session it occurred in (will be translated later)
                eventAttributes.put(AnalyticsAttribute.APP_EXIT_SESSION_ID_ATTRIBUTE, aeiSessionId);

                // Add fg/bg flag based on inferred importance:
                switch (exitInfo.getImportance()) {
                    case ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND:
                    case ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE:
                    case ActivityManager.RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE:
                    case ActivityManager.RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE_PRE_26:
                    case ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE:
                    case ActivityManager.RunningAppProcessInfo.IMPORTANCE_TOP_SLEEPING:
                    case ActivityManager.RunningAppProcessInfo.IMPORTANCE_TOP_SLEEPING_PRE_28:
                        eventAttributes.put(AnalyticsAttribute.APP_EXIT_APP_STATE_ATTRIBUTE, "foreground");
                        break;
                    default:
                        eventAttributes.put(AnalyticsAttribute.APP_EXIT_APP_STATE_ATTRIBUTE, "background");
                        break;
                }

                eventsAdded |= AnalyticsControllerImpl.getInstance().internalRecordEvent(packageName,
                        AnalyticsEventCategory.ApplicationExit,
                        AnalyticsEvent.EVENT_TYPE_MOBILE_APPLICATION_EXIT,
                        eventAttributes);

                StatsEngine.SUPPORTABILITY.inc(MetricNames.SUPPORTABILITY_AEI_EXIT_STATUS + exitInfo.getStatus());
                StatsEngine.SUPPORTABILITY.inc(MetricNames.SUPPORTABILITY_AEI_EXIT_BY_REASON + getReasonAsString(exitInfo.getReason()));
                StatsEngine.SUPPORTABILITY.inc(MetricNames.SUPPORTABILITY_AEI_EXIT_BY_IMPORTANCE + getImportanceAsString(exitInfo.getImportance()));
                StatsEngine.SUPPORTABILITY.sample(MetricNames.SUPPORTABILITY_AEI_VISITED, recordsVisited.get());
                StatsEngine.SUPPORTABILITY.sample(MetricNames.SUPPORTABILITY_AEI_SKIPPED, recordsSkipped.get());
                StatsEngine.SUPPORTABILITY.sample(MetricNames.SUPPORTABILITY_AEI_DROPPED, recordsSkipped.get());
            }

            log.debug("AEI: inspected [" + applicationExitInfoList.size() + "] records: new[" + recordsVisited + "] existing [" + recordsSkipped + "] dropped[" + recordsDropped.get() + "]");

            if (eventsAdded) {
                final EventManager eventMgr = AnalyticsControllerImpl.getInstance().getEventManager();

                // isolate AEI events with `aeiSessionId` attribute
                eventMgr.getQueuedEvents().stream()
                        .filter(aeiEvent -> (AnalyticsEvent.EVENT_TYPE_MOBILE_APPLICATION_EXIT == aeiEvent.getEventType()))
                        .collect(Collectors.toList())
                        .forEach(aeiEvent -> {
                            Collection<AnalyticsAttribute> attrSet = aeiEvent.getMutableAttributeSet();
                            Optional<AnalyticsAttribute> aeiSessionId = attrSet.stream()
                                    .filter(attribute -> attribute.getName().equals(AnalyticsAttribute.APP_EXIT_SESSION_ID_ATTRIBUTE))
                                    .findFirst();

                            // and swap the session ID attributes
                            if (aeiSessionId.isPresent()) {
                                attrSet.add(new AnalyticsAttribute(AnalyticsAttribute.SESSION_ID_ATTRIBUTE,
                                        aeiSessionId.get().getStringValue()));
                                attrSet.remove(aeiSessionId.get());
                            }
                        });

                // flush eventManager buffer on next harvest
                eventMgr.setTransmitRequired();
            }

            // sync the cache dir and session mapper with ART's current queue
            reconcileMetadata(applicationExitInfoList);

            sessionMapper.put(getCurrentProcessId(), AgentConfiguration.getInstance().getSessionID());
            sessionMapper.flush();

        } else {
            log.warn("ApplicationExitMonitor: exit info reporting was enabled, but not supported by the current OS");
            StatsEngine.SUPPORTABILITY.inc(MetricNames.SUPPORTABILITY_AEI_UNSUPPORTED_OS + Build.VERSION.SDK_INT);
        }
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

    protected String toValidAttributeValue(String attributeValue) {
        return (null == attributeValue ? "null" : attributeValue.substring(0, Math.min(attributeValue.length(), AnalyticsAttribute.ATTRIBUTE_VALUE_MAX_LENGTH - 1)));
    }

    protected String getImportanceAsString(int importance) {
        String importanceAsString = String.valueOf(importance);

        switch (importance) {
            case ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND:
                importanceAsString = "Foreground";
                break;
            case ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE:
                importanceAsString = "Foreground service";
                break;
            case ActivityManager.RunningAppProcessInfo.IMPORTANCE_TOP_SLEEPING:
                importanceAsString = "Top sleeping";
                break;
            case ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE:
                importanceAsString = "Visible";
                break;
            case ActivityManager.RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE:
                importanceAsString = "Perceptible";
                break;
            case ActivityManager.RunningAppProcessInfo.IMPORTANCE_CANT_SAVE_STATE:
                importanceAsString = "Can't save state";
                break;
            case ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE:
                importanceAsString = "Service";
                break;
            case ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED:
                importanceAsString = "Cached";
                break;
            case ActivityManager.RunningAppProcessInfo.IMPORTANCE_GONE:
                importanceAsString = "Gone";
                break;
        }
        return importanceAsString;
    }

    protected String getReasonAsString(int reason) {
        String reasonAsString = String.valueOf(reason);

        switch (reason) {
            case 0:     // ApplicationExitInfo.REASON_UNKNOWN
                reasonAsString = "Unknown";
                break;

            case 1:     // ApplicationExitInfo.REASON_EXIT_SELF
                reasonAsString = "Exit self";
                break;

            case 2:     // ApplicationExitInfo.REASON_SIGNALED
                reasonAsString = "Signaled";
                break;

            case 3:     // ApplicationExitInfo.REASON_LOW_MEMORY
                reasonAsString = "Low memory";
                break;

            case 4:     // ApplicationExitInfo.REASON_CRASH
                reasonAsString = "Crash";
                break;

            case 5:     // ApplicationExitInfo.REASON_CRASH_NATIVE
                reasonAsString = "Native crash";
                break;

            case 6:     // ApplicationExitInfo.REASON_ANR
                reasonAsString = "ANR";
                break;

            case 7:     // ApplicationExitInfo.REASON_INITIALIZATION_FAILURE
                reasonAsString = "Initialization failure";
                break;

            case 8:     // ApplicationExitInfo.REASON_PERMISSION_CHANGE
                reasonAsString = "Permission change";
                break;

            case 9:     // ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE
                reasonAsString = "Excessive resource usage";
                break;

            case 10:    // ApplicationExitInfo.REASON_USER_REQUESTED
                reasonAsString = "User requested";
                break;

            case 11:    // ApplicationExitInfo.REASON_USER_STOPPED
                reasonAsString = "User stopped";
                break;

            case 12:    // ApplicationExitInfo.REASON_DEPENDENCY_DIED
                reasonAsString = "Dependency died";
                break;

            case 13:    // ApplicationExitInfo.REASON_OTHER
                reasonAsString = "Other";
                break;

            // requires update up SDK 33
            case 14:    // ApplicationExitInfo.REASON_FREEZER
                reasonAsString = "Freezer";
                break;

            // requires update up SDK 34
            case 15:    // ApplicationExitInfo.REASON_PACKAGE_STATE_CHANGED
                reasonAsString = "Package state changed";
                break;

            case 16:    // ApplicationExitInfo.REASON_PACKAGE_UPDATED
                reasonAsString = "Package updated";
                break;

        }
        return reasonAsString;  // TODO
    }

    List<File> getArtifacts() {
        String regexp = String.format(Locale.getDefault(), ARTIFACT_NAME, "\\d+");

        return Streams.list(reportsDir)
                .filter(file -> file.isFile() && file.getName().matches(regexp))
                .collect(Collectors.toList());
    }

}
