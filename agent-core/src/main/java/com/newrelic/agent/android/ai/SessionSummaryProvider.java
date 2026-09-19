/*
 * Copyright (c) 2026-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.ai;

import com.newrelic.agent.android.analytics.AnalyticsAttribute;

import java.util.Collections;
import java.util.Set;

/**
 * Seam allowing {@link com.newrelic.agent.android.harvest.Harvest#finalizeSession()} to decorate
 * the session event without agent-core depending on anything AI-related.
 *
 * The summary is attached to the session event specifically, <em>not</em> set as a session
 * attribute. Session attributes are copied into {@code HarvestData.setSessionAttributes(...)} and
 * therefore decorate every event in the payload -- a ~1KB string multiplied across hundreds of
 * events per harvest is real ingest cost, which would work against the point of the feature.
 *
 * Defaults to {@link #NONE}, so with the feature flag off the session event is byte-identical to
 * what the agent produced before this existed.
 */
public interface SessionSummaryProvider {

    SessionSummaryProvider NONE = new SessionSummaryProvider() {
        @Override
        public Set<AnalyticsAttribute> getSessionEventAttributes() {
            return Collections.emptySet();
        }
    };

    /**
     * @return attributes to attach to the session event, never null. Called on the harvest thread
     * during session finalization, so implementations must not block.
     */
    Set<AnalyticsAttribute> getSessionEventAttributes();

    final class Registry {
        private static volatile SessionSummaryProvider provider = NONE;

        private Registry() {
        }

        public static void set(SessionSummaryProvider sessionSummaryProvider) {
            provider = (sessionSummaryProvider == null) ? NONE : sessionSummaryProvider;
        }

        public static SessionSummaryProvider get() {
            return provider;
        }

        public static void reset() {
            provider = NONE;
        }
    }
}
