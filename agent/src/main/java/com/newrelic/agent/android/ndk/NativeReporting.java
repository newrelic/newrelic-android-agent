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
import com.newrelic.agent.android.sessioncontext.SessionContextStore;
import com.newrelic.agent.android.sessioncontext.SessionManifest;
import com.newrelic.agent.android.stats.StatsEngine;

import org.json.JSONObject;

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
            // A native crash is captured in the crashing session but flushed on the next launch
            // (after the process dies). Attribute it to the session that actually crashed — its
            // sessionId is embedded in the native report ("backtrace.sessionid") — by resolving
            // that session's frozen attributes from the SessionContextStore rather than stamping
            // the current (relaunched) session's attributes.
            final String originSessionId = extractNativeSessionId(crashAsString);
            final boolean fromPriorSession = originSessionId != null
                    && !originSessionId.equals(AgentConfiguration.getInstance().getSessionID());
            final SessionManifest originManifest = fromPriorSession ? lookupSessionManifest(originSessionId) : null;
            final Set<AnalyticsAttribute> sessionAttributes = new HashSet<>(
                    originManifest != null ? originManifest.getAttributes()
                            : analyticsController.getSessionAttributes());

            // sessionDuration (NR-487823): Java crashes record it but NDK crashes did not. Attach
            // the crashed session's elapsed time so MobileCrash carries sessionDuration for NDK too.
            addCrashSessionDuration(sessionAttributes, crashAsString, fromPriorSession, originManifest);

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
                 CrashReporter.getInstance().storeAndReportCrash(crash,true);
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

        /**
         * Look up the manifest of the prior session that produced a native crash. Returns null (and
         * logs) when none is stored — e.g. the session crashed before its first harvest — in which
         * case the caller falls back to the current session's attributes.
         */
        private SessionManifest lookupSessionManifest(String originSessionId) {
            final SessionContextStore store = AgentConfiguration.getInstance().getSessionContextStore();
            final SessionManifest manifest = (store != null) ? store.get(originSessionId) : null;
            if (manifest == null) {
                log.warn("NativeReporting: no session manifest for crash origin [" + originSessionId
                        + "]; using current session attributes");
            }
            return manifest;
        }

        /**
         * Attach {@code sessionDuration} (seconds) to a native crash. For a crash reported within the
         * current session, the live session duration applies. For one recovered from a prior session,
         * derive it from the native crash timestamp ({@code backtrace.timestamp}, epoch seconds) minus
         * that session's start ({@link SessionManifest#getSessionStartMs()}, epoch ms). No-op when it
         * cannot be determined (e.g. prior session with no manifest), so the crash still reports.
         */
        private void addCrashSessionDuration(Set<AnalyticsAttribute> sessionAttributes, String crashAsString,
                                             boolean fromPriorSession, SessionManifest originManifest) {
            float durationSec = -1f;
            if (!fromPriorSession) {
                final long ms = Harvest.getMillisSinceStart();
                if (ms != Harvest.INVALID_SESSION_DURATION) {
                    durationSec = ms / 1000.0f;
                }
            } else if (originManifest != null && originManifest.getSessionStartMs() > 0L) {
                final long crashSec = extractNativeTimestampSec(crashAsString);
                if (crashSec > 0L) {
                    // Subtract as longs (ms) before converting to float seconds. Subtracting at
                    // epoch scale (~1.7e9) in float arithmetic would lose precision — a 32-bit
                    // float's resolution there is ~128, so short durations would round to 0.
                    final long durationMs = (crashSec * 1000L) - originManifest.getSessionStartMs();
                    if (durationMs >= 0L) {
                        durationSec = durationMs / 1000.0f;
                    }
                }
            }
            if (durationSec >= 0f) {
                sessionAttributes.add(new AnalyticsAttribute(
                        AnalyticsAttribute.SESSION_DURATION_ATTRIBUTE, durationSec));
            }
        }

        /** @return the crash time from a native report ({@code backtrace.timestamp}, epoch seconds), or 0. */
        private long extractNativeTimestampSec(String crashAsString) {
            try {
                return new JSONObject(crashAsString).getJSONObject("backtrace").optLong("timestamp", 0L);
            } catch (Exception e) {
                return 0L;
            }
        }

        /** @return the originating sessionId from a native crash report ({@code backtrace.sessionid}), or null. */
        private String extractNativeSessionId(String crashAsString) {
            try {
                final String sessionId = new JSONObject(crashAsString)
                        .getJSONObject("backtrace")
                        .optString("sessionid", null);
                return (sessionId == null || sessionId.isEmpty()) ? null : sessionId;
            } catch (Exception e) {
                log.warn("NativeReporting: could not read sessionid from native report: " + e);
                return null;
            }
        }

    }
}