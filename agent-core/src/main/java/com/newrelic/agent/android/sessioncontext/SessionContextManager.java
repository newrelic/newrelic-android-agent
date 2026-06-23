/*
 * Copyright (c) 2026-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.newrelic.agent.android.sessioncontext;

import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.analytics.AnalyticsAttribute;
import com.newrelic.agent.android.analytics.AnalyticsControllerImpl;
import com.newrelic.agent.android.harvest.Harvest;
import com.newrelic.agent.android.harvest.HarvestLifecycleAware;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;

import java.util.Collections;
import java.util.Set;

/**
 * Writes a {@link SessionManifest} snapshot of the current session's context to the
 * configured {@link SessionContextStore} once per harvest cycle.
 *
 * <p>This is an <b>always-on</b> harvest listener — it is intentionally NOT tied to the
 * AEI code path, which only runs on API 30+ and behind {@code FeatureFlag.ApplicationExitReporting}
 * + remote config. {@link #onHarvest()} fires every ~60s cycle in the CONNECTED state, so
 * the data token's agent id is always valid when the snapshot is taken.
 */
public class SessionContextManager implements HarvestLifecycleAware {

    private static final AgentLog log = AgentLogManager.getAgentLog();

    private static SessionContextManager instance;

    /** Create (once) and register as a harvest listener. Idempotent. */
    public static synchronized SessionContextManager initialize() {
        if (instance == null) {
            instance = new SessionContextManager();
            Harvest.addHarvestListener(instance);
        }
        return instance;
    }

    public static synchronized void shutdown() {
        if (instance != null) {
            Harvest.removeHarvestListener(instance);
            instance = null;
        }
    }

    @Override
    public void onHarvest() {
        snapshotCurrentSessionContext();
    }

    void snapshotCurrentSessionContext() {
        SessionContextStore store = AgentConfiguration.getInstance().getSessionContextStore();
        if (store == null) {
            return;
        }
        try {
            AnalyticsControllerImpl controller = AnalyticsControllerImpl.getInstance();
            Set<AnalyticsAttribute> sessionAttributes = (controller == null)
                    ? Collections.emptySet() : controller.getSessionAttributes();

            long sessionStartMs = 0L;
            Harvest harvest = Harvest.getInstance();
            if (harvest != null && harvest.getHarvestTimer() != null) {
                sessionStartMs = harvest.getHarvestTimer().getSessionStartTimeMs();
            }

            SessionManifest manifest = new SessionManifest(
                    AgentConfiguration.getInstance().getSessionID(),
                    Harvest.getHarvestConfiguration().getDataToken().getAgentId(),
                    sessionStartMs,
                    System.currentTimeMillis(),
                    sessionAttributes);
            store.upsert(manifest);
        } catch (Exception e) {
            log.error("SessionContextManager: failed to snapshot session context: " + e);
        }
    }
}