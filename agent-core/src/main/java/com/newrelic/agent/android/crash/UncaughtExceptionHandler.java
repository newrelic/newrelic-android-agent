/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.crash;

import com.newrelic.agent.android.Agent;
import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.FeatureFlag;
import com.newrelic.agent.android.analytics.AnalyticsAttribute;
import com.newrelic.agent.android.analytics.AnalyticsControllerImpl;
import com.newrelic.agent.android.background.ApplicationStateMonitor;
import com.newrelic.agent.android.harvest.Harvest;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.metric.MetricNames;
import com.newrelic.agent.android.payload.PayloadController;
import com.newrelic.agent.android.stats.StatsEngine;

import java.util.concurrent.atomic.AtomicBoolean;

public class UncaughtExceptionHandler implements Thread.UncaughtExceptionHandler {
    protected static final AgentLog log = AgentLogManager.getAgentLog();

    protected final AtomicBoolean handledException = new AtomicBoolean(false);
    private final CrashReporter crashReporter;

    static Thread.UncaughtExceptionHandler previousExceptionHandler = null;

    public UncaughtExceptionHandler(CrashReporter crashReporter) {
        this.crashReporter = crashReporter;
    }

    public void installExceptionHandler() {
        // Save the previous exception handler so we can chain it
        final Thread.UncaughtExceptionHandler currentExceptionHandler = Thread.getDefaultUncaughtExceptionHandler();

        if (currentExceptionHandler != null) {
            if (currentExceptionHandler instanceof UncaughtExceptionHandler) {
                log.debug("New Relic crash handler already installed.");
                return;
            }

            if (previousExceptionHandler != null) {
                if (previousExceptionHandler instanceof UncaughtExceptionHandler) {
                    log.warning("Previous uncaught exception handler[" + previousExceptionHandler.getClass().getName() + "] exists, and it is us! Replace it.");
                } else {
                    log.warning("Previous uncaught exception handler[" + previousExceptionHandler.getClass().getName() + "] exists. Assuming it delegates to [" + UncaughtExceptionHandler.class.getName() + "]");
                    return;
                }
            }
            log.debug("Installing New Relic crash handler and chaining to " + currentExceptionHandler.getClass().getName());

        } else {
            log.debug("Installing New Relic crash handler.");
        }

        previousExceptionHandler = currentExceptionHandler;
        Thread.setDefaultUncaughtExceptionHandler(this);
    }


    @Override
    public void uncaughtException(final Thread thread, final Throwable throwable) {
        // Only handle an exception once (even chaining, we did our due diligence already)
        if (!handledException.compareAndSet(false, true)) {
            StatsEngine.get().inc(MetricNames.SUPPORTABILITY_CRASH_RECURSIVE_HANDLER);
            return;
        }

        try {
            final AgentConfiguration agentConfiguration = crashReporter.getAgentConfiguration();

            // Check that the crash reporting feature is enabled, and that the CrashReporter instance is ready.
            if (!(crashReporter.isEnabled() && FeatureFlag.featureEnabled(FeatureFlag.CrashReporting))) {
                log.debug("A crash has been detected but crash reporting is disabled!");
                return;
            }

            log.debug("A crash has been detected in " + thread.getStackTrace()[0].getClassName() + " and will be reported ASAP.");
            log.debug("Analytics data is currently " + (agentConfiguration.getEnableAnalyticsEvents() ? "enabled " : "disabled"));


            final AnalyticsControllerImpl analyticsController = AnalyticsControllerImpl.getInstance();

            analyticsController.setEnabled(true);
            long sessionDuration = Harvest.getMillisSinceStart();
            if (sessionDuration != Harvest.INVALID_SESSION_DURATION) {
                analyticsController.setAttribute(AnalyticsAttribute.SESSION_DURATION_ATTRIBUTE, ((float) sessionDuration / 1000.0f), false);
            }

            final Crash crash = new Crash(throwable,
                    analyticsController.getSessionAttributes(),
                    analyticsController.getEventManager().getQueuedEvents(),
                    agentConfiguration.getEnableAnalyticsEvents());

            // Store the crash right away.  We'll delete it later if we're able to send it.
            crashReporter.storeAndReportCrash(crash);

        } finally {
            // InstantApps don't provide the same lifecycle hints as normal apps.
            // The app UI does go to background, so let the monitor know it is
            // now hidden so it shuts down the agent
            if (Agent.isInstantApp()) {
                // To avoid harvesting during Agent.stop(), shut down the Harvester now.
                // Harvest takes longer than we're allowed with IA
                Harvest.shutdown();
                ApplicationStateMonitor.getInstance().uiHidden();
            }

            // PayloadController.shutdown() below will reset the previous handler, so save it here
            Thread.UncaughtExceptionHandler exceptionHandler = previousExceptionHandler;

            // all submitted tasks will still run out
            PayloadController.shutdown();

            // To prevent recursive crashing, just proxy this exception through
            // Chaining to an existing handler is usually a one-way trip:
            // the app is usually killed and the call never returns, which is why PayloadController
            // must be shutdown prior to chaining.
            chainExceptionHandler(exceptionHandler, thread, throwable);
        }
    }

    // Chain the exception along to the previous exception handler
    void chainExceptionHandler(Thread.UncaughtExceptionHandler exceptionHandler, final Thread thread, final Throwable throwable) {
        if (exceptionHandler != null) {
            log.debug("Chaining crash reporting duties to " + exceptionHandler.getClass().getSimpleName());
            exceptionHandler.uncaughtException(thread, throwable);
        }
    }

    // restore the previous exception handler state
    public void resetExceptionHandler() {
        if (previousExceptionHandler != null) {
            final Thread.UncaughtExceptionHandler currentExceptionHandler = Thread.getDefaultUncaughtExceptionHandler();

            if (currentExceptionHandler != null) {
                // if the default handler was reset after we started, leave it alone
                if (currentExceptionHandler instanceof UncaughtExceptionHandler) {
                    Thread.setDefaultUncaughtExceptionHandler(previousExceptionHandler);
                    previousExceptionHandler = null;
                } else {
                    log.warning("Previous uncaught exception handler[" + currentExceptionHandler.getClass().getName() + "] was set after agent start. Let it be...");
                }
            }
        }
        handledException.set(false);
    }

    public Thread.UncaughtExceptionHandler getPreviousExceptionHandler() {
        return previousExceptionHandler;
    }

}