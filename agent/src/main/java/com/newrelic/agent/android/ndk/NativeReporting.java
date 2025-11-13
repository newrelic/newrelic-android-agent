/**
 * Copyright 2021-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.ndk;

import android.content.Context;

import com.newrelic.agent.android.Agent;
import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.NewRelic;
import com.newrelic.agent.android.agentdata.AgentDataController;
import com.newrelic.agent.android.agentdata.AgentDataReporter;
import com.newrelic.agent.android.analytics.AnalyticsAttribute;
import com.newrelic.agent.android.analytics.AnalyticsControllerImpl;
import com.newrelic.agent.android.crash.CrashReporter;
import com.newrelic.agent.android.harvest.Harvest;
import com.newrelic.agent.android.harvest.HarvestAdapter;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.metric.MetricNames;
import com.newrelic.agent.android.stats.StatsEngine;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

public class NativeReporting extends HarvestAdapter {
    protected static final AgentLog log = AgentLogManager.getAgentLog();
    protected static AtomicReference<NativeReporting> instance = new AtomicReference<>(null);
    protected static AtomicReference<AgentNDK> agentNdk = new AtomicReference<>(null);

    protected final NativeReportListener nativeReportListener;

    public static NativeReporting getInstance() {
        return instance.get();
    }


    public static NativeReporting initialize(Context context, AgentConfiguration agentConfiguration) {
        instance.compareAndSet(null, new NativeReporting(context, agentConfiguration));
        Harvest.addHarvestListener(instance.get());
        StatsEngine.SUPPORTABILITY.inc(MetricNames.SUPPORTABILITY_NDK_INIT);
        return instance.get();
    }

    public static void shutdown() {
        if (isInitialized()) {
            Harvest.removeHarvestListener(instance.get());
            instance.get().stop();
        }
        instance.set(null);
    }

    public static boolean isInitialized() {
        if ((instance.get() == null || agentNdk.get() == null)) return false;
        AgentNDK.getInstance();
        return true;
    }

    public static void crashNow(final String message) {
        if (isInitialized()) {
            agentNdk.get().crashNow(message);
        }
    }

    public static boolean isRooted() {
        boolean isRooted = false;
        if (isInitialized()) {
            StatsEngine.SUPPORTABILITY.inc(MetricNames.SUPPORTABILITY_NDK_ROOTED_DEVICE);
            isRooted = AgentNDK.getInstance().isRooted();
        }
        return isRooted;
    }

    protected NativeReporting(Context context, AgentConfiguration agentConfiguration) {
        this.nativeReportListener = new NativeReportListener();
        agentNdk.compareAndSet(null,
                new AgentNDK.Builder(context)
                        .withBuildId(Agent.getBuildId())
                        .withANRMonitor(!agentConfiguration.getApplicationExitConfiguration().isEnabled())
                        .withSessionId(agentConfiguration.getSessionID())
                        .withReportListener(this.nativeReportListener)
                        .withLogger(AgentLogManager.getAgentLog())
                        .build());
    }

    public void start() {
        if (NativeReporting.isInitialized()) {
            try {
                agentNdk.get().start();
                StatsEngine.SUPPORTABILITY.inc(MetricNames.SUPPORTABILITY_NDK_START);
            } catch (Exception e) {
                log.error(e.toString());
            }
            boolean isRooted = isRooted();
            if (isRooted)
                NewRelic.setAttribute(AnalyticsAttribute.NATIVE_ROOTED_DEVICE_ATTRIBUTE, isRooted);
        } else {
            log.error("CrashReporter: Must first initialize native module.");
        }
    }

    void stop() {
        if (NativeReporting.isInitialized()) {
            try {
                agentNdk.get().stop();
            } catch (Exception e) {
                log.error(e.toString());
            }
            StatsEngine.SUPPORTABILITY.inc(MetricNames.SUPPORTABILITY_NDK_STOP);
            agentNdk.set(null);
        }
    }

    @Override
    public void onHarvestStart() {
        agentNdk.get().flushPendingReports();
        StatsEngine.SUPPORTABILITY.inc(MetricNames.SUPPORTABILITY_NDK_REPORTS_FLUSH);
    }


    static class NativeCrashException extends NativeException {
        public NativeCrashException(String crashAsString) {
            super(crashAsString);
        }
    }

    static class NativeUnhandledException extends NativeException {
        public NativeUnhandledException(String exceptionAsString) {
            super(exceptionAsString);
        }
    }

    static class ANRException extends NativeException {
        public ANRException(String anrAsString) {
            super(anrAsString);
        }
    }

    static class NativeReportListener implements AgentNDKListener {
        @Override
        public boolean onNativeCrash(String crashAsString) {
            StatsEngine.SUPPORTABILITY.inc(MetricNames.SUPPORTABILITY_NDK_REPORTS_CRASH);

            final AnalyticsControllerImpl analyticsController = AnalyticsControllerImpl.getInstance();
            final Set<AnalyticsAttribute> sessionAttributes = new HashSet<AnalyticsAttribute>() {{
                addAll(analyticsController.getSessionAttributes());
            }};

            NativeException exceptionToHandle = new NativeCrashException(crashAsString);

            sessionAttributes.add(new AnalyticsAttribute(AnalyticsAttribute.APPLICATION_PLATFORM_ATTRIBUTE, "native"));
            sessionAttributes.add(new AnalyticsAttribute(AnalyticsAttribute.NATIVE_CRASH, true));
            sessionAttributes.add(new AnalyticsAttribute(AnalyticsAttribute.NATIVE_EXCEPTION_MESSAGE_ATTRIBUTE, exceptionToHandle.getNativeStackTrace().getExceptionMessage()));

            if (null != exceptionToHandle.getNativeStackTrace().getCrashedThread()) {
                sessionAttributes.add(new AnalyticsAttribute(AnalyticsAttribute.NATIVE_CRASHING_THREAD_ID_ATTRIBUTE, exceptionToHandle.getNativeStackTrace().getCrashedThread().getThreadId()));
            }

            NativeCrash crash = new NativeCrash(exceptionToHandle,
                    sessionAttributes,
                    analyticsController.getEventManager().getQueuedEvents());

            if (null != CrashReporter.getInstance()) {
                 CrashReporter.getInstance().storeAndReportCrash(crash);
                return true;
            } else {
                log.error("Could not report native crash: CrashReporter is disabled.");
            }

            return false;
        }

        @Override
        public boolean onNativeException(String exceptionAsString) {
            StatsEngine.SUPPORTABILITY.inc(MetricNames.SUPPORTABILITY_NDK_REPORTS_EXCEPTION);

            Map<String, Object> exceptionAttributes = new HashMap<String, Object>() {{
                put(AnalyticsAttribute.APPLICATION_PLATFORM_ATTRIBUTE, "native");
                put(AnalyticsAttribute.UNHANDLED_NATIVE_EXCEPTION, true);
            }};

            NativeException exceptionToHandle = new NativeUnhandledException(exceptionAsString);
            exceptionAttributes.put(AnalyticsAttribute.NATIVE_THREADS_ATTRIBUTE, exceptionToHandle.getNativeStackTrace().getThreads());
            exceptionAttributes.put(AnalyticsAttribute.NATIVE_EXCEPTION_MESSAGE_ATTRIBUTE, exceptionToHandle.getNativeStackTrace().getExceptionMessage());

            if (null != exceptionToHandle.getNativeStackTrace().getCrashedThread()) {
                exceptionAttributes.put(AnalyticsAttribute.NATIVE_CRASHING_THREAD_ID_ATTRIBUTE, exceptionToHandle.getNativeStackTrace().getCrashedThread().getThreadId());
            }

            if (null != AgentDataReporter.getInstance()) {
                return AgentDataController.sendAgentData(exceptionToHandle, exceptionAttributes);
            } else {
                log.error("Could not report native exception: AgentDataReporter is disabled.");
            }

            return false;
        }

        @Override
        public boolean onApplicationNotResponding(String anrAsString) {
            StatsEngine.SUPPORTABILITY.inc(MetricNames.SUPPORTABILITY_NDK_REPORTS_ANR);

            Map<String, Object> exceptionAttributes = new HashMap<String, Object>() {{
                put(AnalyticsAttribute.APPLICATION_PLATFORM_ATTRIBUTE, "native");
                put(AnalyticsAttribute.ANR, true);
            }};

            NativeException exceptionToHandle = new ANRException(anrAsString);
            exceptionAttributes.put("nativeThreads", exceptionToHandle.getNativeStackTrace().getThreads());
            exceptionAttributes.put("exceptionMessage", exceptionToHandle.getNativeStackTrace().getExceptionMessage());

            if (null != exceptionToHandle.getNativeStackTrace().getCrashedThread()) {
                exceptionAttributes.put("crashingThreadId", exceptionToHandle.getNativeStackTrace().getCrashedThread().getThreadId());
            }

            if (null != AgentDataReporter.getInstance()) {
                return AgentDataController.sendAgentData(exceptionToHandle, exceptionAttributes);
            } else {
                log.error("Could not report native exception: AgentDataReporter is disabled.");
            }

            return false;
        }

    }
}