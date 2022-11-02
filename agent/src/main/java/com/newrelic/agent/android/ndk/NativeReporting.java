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

public class NativeReporting extends HarvestAdapter implements AgentNDKListener {
    protected static final AgentLog log = AgentLogManager.getAgentLog();
    protected static AtomicReference<NativeReporting> instance = new AtomicReference<>(null);

    public static NativeReporting getInstance() {
        return instance.get();
    }

    public static NativeReporting initialize(Context context, AgentConfiguration agentConfiguration) {
        instance.compareAndSet(null, new NativeReporting(context, agentConfiguration));
        Harvest.addHarvestListener(instance.get());
        StatsEngine.get().inc(MetricNames.SUPPORTABILITY_NDK_INIT);
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
        return (instance.get() != null) && (AgentNDK.getInstance() != null);
    }

    public static void crashNow(final String message) {
        if (isInitialized()) {
            AgentNDK.getInstance().crashNow(message);
        }
    }

    public static boolean isRooted() {
        boolean isRooted = false;
        if (isInitialized()) {
            StatsEngine.get().inc(MetricNames.SUPPORTABILITY_NDK_ROOTED_DEVICE);
            isRooted = AgentNDK.getInstance().isRooted();
        }
        return isRooted;
    }

    NativeReporting(Context context, AgentConfiguration agentConfiguration) {
        AgentNDK.Builder builder = new AgentNDK.Builder(context);
        builder.withBuildId(Agent.getBuildId())
                .withSessionId(agentConfiguration.getSessionID())
                .withReportListener(this)
                .withLogger(log)
                .build();
    }

    public void start() {
        if (NativeReporting.isInitialized()) {
            AgentNDK.getInstance().start();
            StatsEngine.get().inc(MetricNames.SUPPORTABILITY_NDK_START);
            boolean isRooted = isRooted();
            if (isRooted)
                NewRelic.setAttribute("RootedDevice", isRooted);
        } else {
            log.error("CrashReporter: Must first initialize native module.");
        }
    }

    void stop() {
        if (NativeReporting.isInitialized()) {
            AgentNDK.getInstance().stop();
            StatsEngine.get().inc(MetricNames.SUPPORTABILITY_NDK_STOP);
        }
    }

    @Override
    public boolean onNativeCrash(String crashAsString) {
        StatsEngine.get().inc(MetricNames.SUPPORTABILITY_NDK_REPORTS_CRASH);

        final AnalyticsControllerImpl analyticsController = AnalyticsControllerImpl.getInstance();
        final Set<AnalyticsAttribute> sessionAttributes = new HashSet<AnalyticsAttribute>() {{
            addAll(analyticsController.getSessionAttributes());
        }};

        NativeException exceptionToHandle = new NativeCrashException(crashAsString);

        sessionAttributes.add(new AnalyticsAttribute(AnalyticsAttribute.APPLICATION_PLATFORM_ATTRIBUTE, "native"));
        sessionAttributes.add(new AnalyticsAttribute(AnalyticsAttribute.NATIVE_CRASH, true));
        sessionAttributes.add(new AnalyticsAttribute("exceptionMessage", exceptionToHandle.getNativeStackTrace().getExceptionMessage()));
        sessionAttributes.add(new AnalyticsAttribute("crashingThreadId", exceptionToHandle.getNativeStackTrace().getCrashedThread().getThreadId()));

        NativeCrash crash = new NativeCrash(exceptionToHandle,
                sessionAttributes,
                analyticsController.getEventManager().getQueuedEvents());

        CrashReporter.getInstance().storeAndReportCrash(crash);

        return true;
    }

    @Override
    public boolean onNativeException(String exceptionAsString) {
        StatsEngine.get().inc(MetricNames.SUPPORTABILITY_NDK_REPORTS_EXCEPTION);

        Map<String, Object> exceptionAttributes = new HashMap<String, Object>() {{
            put(AnalyticsAttribute.APPLICATION_PLATFORM_ATTRIBUTE, "native");
            put(AnalyticsAttribute.UNHANDLED_NATIVE_EXCEPTION, true);
        }};

        NativeException exceptionToHandle = new NativeUnhandledException(exceptionAsString);
        exceptionAttributes.put("crashingThreadId", exceptionToHandle.getNativeStackTrace().getCrashedThread().getThreadId());
        exceptionAttributes.put("nativeThreads", exceptionToHandle.getNativeStackTrace().getThreads());
        exceptionAttributes.put("exceptionMessage", exceptionToHandle.getNativeStackTrace().getExceptionMessage());

        return AgentDataController.sendAgentData(exceptionToHandle, exceptionAttributes);
    }

    @Override
    public boolean onApplicationNotResponding(String anrAsString) {
        StatsEngine.get().inc(MetricNames.SUPPORTABILITY_NDK_REPORTS_ANR);

        Map<String, Object> exceptionAttributes = new HashMap<String, Object>() {{
            put(AnalyticsAttribute.APPLICATION_PLATFORM_ATTRIBUTE, "native");
            put(AnalyticsAttribute.ANR, true);
        }};

        NativeException exceptionToHandle = new ANRException(anrAsString);
        exceptionAttributes.put("crashingThreadId", exceptionToHandle.getNativeStackTrace().getCrashedThread().getThreadId());
        exceptionAttributes.put("nativeThreads", exceptionToHandle.getNativeStackTrace().getThreads());
        exceptionAttributes.put("exceptionMessage", exceptionToHandle.getNativeStackTrace().getExceptionMessage());

        return AgentDataController.sendAgentData(exceptionToHandle, exceptionAttributes);
    }

    @Override
    public void onHarvestStart() {
        AgentNDK.getInstance().flushPendingReports();
        StatsEngine.get().inc(MetricNames.SUPPORTABILITY_NDK_REPORTS_FLUSH);
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

}