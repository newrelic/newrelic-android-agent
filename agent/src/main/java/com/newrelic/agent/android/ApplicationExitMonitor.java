/**
 * Copyright 2021-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android;

import static com.newrelic.agent.android.analytics.AnalyticsEvent.EVENT_TYPE_MOBILE_APPLICATION_EXIT;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.os.Build;

import com.newrelic.agent.android.analytics.AnalyticsAttribute;
import com.newrelic.agent.android.analytics.AnalyticsControllerImpl;
import com.newrelic.agent.android.analytics.AnalyticsEventCategory;
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
import java.util.HashMap;
import java.util.List;

public class ApplicationExitMonitor {
    private static final AgentLog log = AgentLogManager.getAgentLog();

    protected final String packageName;
    protected final File reportsDir;
    protected ActivityManager am;

    public ApplicationExitMonitor(final Context context) {
        this.reportsDir = new File(context.getCacheDir(), "newrelic/applicationExitInfo");
        this.am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        this.packageName = context.getPackageName();

        reportsDir.mkdirs();
    }

    /**
     * Gather application exist status reports for this process
     **/
    @SuppressLint("SwitchIntDef")
    @SuppressWarnings("deprecation")
    protected void harvestApplicationExitInfo() {

        // Only supported in Android 11+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            boolean eventsAdded = false;
            int recordsVisited = 0;
            int recordsSkipped = 0;

            if (null == am) {
                log.error("harvestApplicationExitInfo: ActivityManager is null!");
                return;
            }

            // we are reporting all reasons
            final List<android.app.ApplicationExitInfo> applicationExitInfos =
                    am.getHistoricalProcessExitReasons(packageName, 0, 0);

            // the set may contain more than one report for this package name
            for (ApplicationExitInfo exitInfo : applicationExitInfos) {
                File artifact = new File(reportsDir, "app-exit-" + exitInfo.getPid() + ".log");

                // If an artifact for this pid exists, it's been recorded already
                if (artifact.exists() && (artifact.length() > 0)) {
                    log.debug("ApplicationExitMonitor: skipping exit info for pid[" + exitInfo.getPid() + "]: already recorded.");
                    recordsSkipped++;

                } else {
                    String traceReport = exitInfo.toString();

                    // remove any empty files
                    if (artifact.exists() && artifact.length() == 0) {
                        artifact.delete();
                    }

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

                        recordsVisited++;

                    } catch (IOException e) {
                        log.debug("harvestApplicationExitInfo: AppExitInfo artifact error. " + e);
                    }

                    // finally, emit an event for the record
                    final HashMap<String, Object> eventAttributes = new HashMap<>();

                    eventAttributes.put(AnalyticsAttribute.APP_EXIT_TIMESTAMP_ATTRIBUTE, exitInfo.getTimestamp());
                    eventAttributes.put(AnalyticsAttribute.APP_EXIT_REASON_ATTRIBUTE, exitInfo.getReason());
                    eventAttributes.put(AnalyticsAttribute.APP_EXIT_IMPORTANCE_ATTRIBUTE, exitInfo.getImportance());
                    eventAttributes.put(AnalyticsAttribute.APP_EXIT_IMPORTANCE_STRING_ATTRIBUTE, getImportanceAsString(exitInfo.getImportance()));
                    eventAttributes.put(AnalyticsAttribute.APP_EXIT_DESCRIPTION_ATTRIBUTE, toValidAttributeValue(exitInfo.getDescription()));
                    eventAttributes.put(AnalyticsAttribute.APP_EXIT_PROCESS_NAME_ATTRIBUTE, toValidAttributeValue(exitInfo.getProcessName()));

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
                            EVENT_TYPE_MOBILE_APPLICATION_EXIT,
                            eventAttributes);

                    StatsEngine.SUPPORTABILITY.inc(MetricNames.SUPPORTABILITY_AEI_EXIT_STATUS + exitInfo.getStatus());
                    StatsEngine.SUPPORTABILITY.inc(MetricNames.SUPPORTABILITY_AEI_EXIT_BY_REASON + exitInfo.getReason());
                    StatsEngine.SUPPORTABILITY.inc(MetricNames.SUPPORTABILITY_AEI_EXIT_BY_IMPORTANCE + exitInfo.getImportance());
                    StatsEngine.SUPPORTABILITY.sample(MetricNames.SUPPORTABILITY_AEI_VISITED, recordsVisited);
                    StatsEngine.SUPPORTABILITY.sample(MetricNames.SUPPORTABILITY_AEI_SKIPPED, recordsSkipped);
                }

                log.debug("AEI: inspected " + applicationExitInfos.size() + " records: new[ " + recordsVisited + "] existing [" + recordsSkipped + "]");
            }

            if (eventsAdded) {
                // flush eventManager buffer on next harvest
                AnalyticsControllerImpl.getInstance().getEventManager().setTransmitRequired();
            }

        } else {
            log.warn("ApplicationExitMonitor: exit info reporting was enabled, but not supported by the current OS");
            StatsEngine.SUPPORTABILITY.inc(MetricNames.SUPPORTABILITY_AEI_UNSUPPORTED_OS + Build.VERSION.SDK_INT);
        }
    }

    protected String toValidAttributeValue(String attributeValue) {
        return (null == attributeValue ? "null" : attributeValue.substring(0, Math.min(attributeValue.length(), AnalyticsAttribute.ATTRIBUTE_VALUE_MAX_LENGTH - 1)));
    }

    protected String getImportanceAsString(int importance) {
        String importanceAsString = "";
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

}
