/*
 * Copyright (c) 2026-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.ai;

import com.newrelic.agent.android.Agent;
import com.newrelic.agent.android.analytics.AnalyticsAttribute;
import com.newrelic.agent.android.analytics.AnalyticsEvent;
import com.newrelic.agent.android.analytics.AnalyticsEventCategory;
import com.newrelic.agent.android.harvest.ActivityHistory;
import com.newrelic.agent.android.harvest.ApplicationInformation;
import com.newrelic.agent.android.harvest.ActivitySighting;
import com.newrelic.agent.android.harvest.DeviceInformation;
import com.newrelic.agent.android.harvest.HarvestData;
import com.newrelic.agent.android.harvest.HttpTransaction;
import com.newrelic.agent.android.harvest.HttpTransactions;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.tracing.TraceMachine;

import java.util.Collection;

/**
 * Maps a harvest payload onto {@link SessionDigest}.
 *
 * This is the only class that knows the shape of {@code HarvestData}, which is the point: the digest
 * and its prose rendering stay independent of the agent's harvest types and can be exercised by
 * calling {@code record*} directly.
 *
 * <h3>What it deliberately does not read</h3>
 * Breadcrumb text, custom attribute values, exception messages, request and response bodies, and
 * headers are all reachable from here and none of them are touched. That restraint is the privacy
 * invariant: the summary can contain no data category the customer is not already sending.
 *
 * <h3>Not a source of handled exceptions</h3>
 * Handled exceptions never appear in {@code HarvestData} -- they travel through
 * {@code AgentDataReporter} as flatbuffers on a separate pipeline. So the digest carries no
 * exception class names, and the error signal comes from HTTP failures, crash events, and ANR flags
 * instead. Wiring handled exceptions in would mean tapping that second pipeline.
 */
public class SessionDigestCollector {

    private static final AgentLog log = AgentLogManager.getAgentLog();

    private final SessionDigest digest;

    public SessionDigestCollector(SessionDigest digest) {
        this.digest = digest;
    }

    /**
     * Folds one harvest cycle into the digest.
     *
     * Analytics events only appear in {@code HarvestData} on cycles where the event manager decided
     * a transmit was required, so quiet cycles contribute no events. Nothing is lost: those events
     * stay queued and arrive in a later cycle's payload. The digest just trails slightly behind the
     * live session.
     */
    public void fold(HarvestData harvestData) {
        if (harvestData == null) {
            return;
        }

        foldContext();
        foldScreenVisits();
        foldHttpTransactions(harvestData.getHttpTransactions());
        foldAnalyticsEvents(harvestData.getAnalyticsEvents());

        digest.endCycle();
    }

    /**
     * Copies app and device context onto the digest.
     *
     * Everything here already ships with every harvest, so it adds no new data category -- it just
     * lets the record say which app on which device, which is the first thing an engineer reading a
     * summary wants to know. Wrapped because these are agent globals that may not be populated yet on
     * the very first cycle.
     */
    private void foldContext() {
        try {
            final ApplicationInformation app = Agent.getApplicationInformation();
            final DeviceInformation device = Agent.getDeviceInformation();

            digest.setContext(
                    app == null ? null : app.getAppName(),
                    app == null ? null : app.getAppVersion(),
                    device == null ? null : device.getManufacturer(),
                    device == null ? null : device.getModel(),
                    device == null ? null : device.getOsName(),
                    device == null ? null : device.getOsVersion());
        } catch (Throwable t) {
            // Context is a nice-to-have; the record stands without it.
            log.debug("SessionSummary: could not read app/device context: " + t);
        }
    }

    /**
     * Reads screen dwell times from TraceMachine's activity history.
     *
     * Deliberately <em>not</em> from {@code HarvestData.getActivityTraces()}. An
     * {@code ActivityTrace}'s root trace measures the instrumented lifecycle method, so its duration
     * is tens of milliseconds -- using it produced prose that claimed "the user opened Checkout for
     * 38 milliseconds", which is not merely imprecise but false, and asking a model to summarize
     * false statements is worse than giving it nothing. {@code ActivitySighting} is the type that
     * tracks how long the user was actually on a screen.
     *
     * Sightings are cumulative for the session and replace rather than append, since TraceMachine
     * owns the list and updates the current sighting's duration in place as the user stays on screen.
     */
    private void foldScreenVisits() {
        final ActivityHistory history = TraceMachine.getActivityHistory();
        if (history == null) {
            return;
        }

        digest.resetScreenVisits();

        for (ActivitySighting sighting : history.getActivitySightings()) {
            if (sighting == null) {
                continue;
            }

            digest.recordScreenVisit(stripDisplayPrefix(sighting.getName()),
                    sighting.getTimestampMs(), sighting.getDuration());
        }
    }

    /**
     * TraceMachine prefixes activity display names with "Display " (see
     * {@code TraceMachine.ACTIVTY_DISPLAY_NAME_PREFIX}). That is an internal metric-naming detail and
     * reads as noise in a sentence, so the screen name is used bare.
     */
    static String stripDisplayPrefix(String displayName) {
        if (displayName == null) {
            return null;
        }

        return displayName.startsWith(TraceMachine.ACTIVTY_DISPLAY_NAME_PREFIX)
                ? displayName.substring(TraceMachine.ACTIVTY_DISPLAY_NAME_PREFIX.length())
                : displayName;
    }

    private void foldHttpTransactions(HttpTransactions httpTransactions) {
        if (httpTransactions == null) {
            return;
        }

        for (HttpTransaction transaction : httpTransactions.getHttpTransactions()) {
            if (transaction == null) {
                continue;
            }

            // HttpTransaction.getTotalTime() is seconds (TransactionState computes
            // totalTimeAsSeconds), so it is converted here rather than in the digest.
            digest.recordHttpRequest(transaction.getHttpMethod(), transaction.getUrl(),
                    transaction.getStatusCode(), transaction.getErrorCode(),
                    transaction.getTotalTime() * 1000.0d,
                    transaction.getBytesSent(), transaction.getBytesReceived());
        }
    }

    private void foldAnalyticsEvents(Collection<AnalyticsEvent> events) {
        if (events == null) {
            return;
        }

        for (AnalyticsEvent event : events) {
            if (event == null) {
                continue;
            }

            final AnalyticsEventCategory category = event.getCategory();
            if (category == AnalyticsEventCategory.Interaction) {
                foldInteraction(event);
            } else if (category == AnalyticsEventCategory.UserAction) {
                digest.recordUserAction();
            } else if (category == AnalyticsEventCategory.Crash) {
                digest.recordCrash();
            }

            if (hasTrueFlag(event, AnalyticsAttribute.ANR)) {
                digest.recordAnr();
            }
        }
    }

    private void foldInteraction(AnalyticsEvent event) {
        for (AnalyticsAttribute attribute : event.getAttributeSet()) {
            if (attribute == null
                    || !AnalyticsAttribute.INTERACTION_DURATION_ATTRIBUTE.equals(attribute.getName())) {
                continue;
            }

            // interactionDuration is recorded in seconds (AnalyticsControllerImpl)
            digest.recordInteraction(event.getName(), attribute.getDoubleValue() * 1000.0d);
            return;
        }
    }

    private static boolean hasTrueFlag(AnalyticsEvent event, String attributeName) {
        for (AnalyticsAttribute attribute : event.getAttributeSet()) {
            if (attribute != null && attributeName.equals(attribute.getName())) {
                return attribute.getAttributeDataType() == AnalyticsAttribute.AttributeDataType.BOOLEAN
                        && attribute.getBooleanValue();
            }
        }
        return false;
    }
}
