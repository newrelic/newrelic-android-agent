/*
 * Copyright (c) 2026-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.newrelic.agent.android.sessioncontext;

import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.analytics.AnalyticsAttribute;
import com.newrelic.agent.android.analytics.AnalyticsControllerImpl;
import com.newrelic.agent.android.background.ApplicationStateEvent;
import com.newrelic.agent.android.background.ApplicationStateListener;
import com.newrelic.agent.android.background.ApplicationStateMonitor;
import com.newrelic.agent.android.harvest.Harvest;
import com.newrelic.agent.android.harvest.HarvestLifecycleAware;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;

import java.util.Collections;
import java.util.Set;

/**
 * Writes a {@link SessionManifest} snapshot of the current session's context to the
 * configured {@link SessionContextStore} once per harvest cycle, and removes it once the
 * session's data has been safely harvested at background time.
 *
 * <p>This is an <b>always-on</b> harvest listener — it is intentionally NOT tied to the
 * AEI code path, which only runs on API 30+ and behind {@code FeatureFlag.ApplicationExitReporting}
 * + remote config. {@link #onHarvest()} fires every ~60s cycle in the CONNECTED state, so
 * the data token's agent id is always valid when the snapshot is taken.
 *
 * <p><b>Cleanup.</b> The manifest is only needed to attribute an abnormal-termination event
 * (crash / ANR / process-exit / force-close) to the session that was alive when it happened.
 * When the app backgrounds, the agent forces a final harvest; once that harvest has either
 * been delivered online or persisted offline, the current session's manifest is deleted.
 * (A subsequent OS kill of the backgrounded process therefore recovers with no session
 * attributes plus a {@code SessionContext/Missing} supportability metric — an accepted
 * trade-off.) The store's bounded entry count is the backstop for anything not cleaned here.
 */
public class SessionContextManager implements HarvestLifecycleAware, ApplicationStateListener {

    private static final AgentLog log = AgentLogManager.getAgentLog();

    private static SessionContextManager instance;

    /**
     * Whether the most recent harvest was delivered online (2xx) or persisted offline.
     * A server rejection (4xx/5xx) clears it. Read on background to decide whether the
     * session's data is safe enough to drop its manifest. Written on the harvest thread,
     * read on the app-state thread after the forced background harvest has completed.
     */
    private volatile boolean lastHarvestHandled = false;

    /** Create (once) and register as harvest + application-state listener. Idempotent. */
    public static synchronized SessionContextManager initialize() {
        if (instance == null) {
            instance = new SessionContextManager();
            Harvest.addHarvestListener(instance);
            ApplicationStateMonitor.getInstance().addApplicationStateListener(instance);
        }
        return instance;
    }

    public static synchronized void shutdown() {
        if (instance != null) {
            Harvest.removeHarvestListener(instance);
            ApplicationStateMonitor.getInstance().removeApplicationStateListener(instance);
            instance = null;
        }
    }

    @Override
    public void onHarvest() {
        snapshotCurrentSessionContext();
    }

    @Override
    public void onHarvestComplete() {
        // Harvest POST accepted (2xx).
        lastHarvestHandled = true;
    }

    @Override
    public void onHarvestSendFailed() {
        // No connectivity — the harvest data was persisted offline for later upload.
        // Treated as "handled" so background cleanup runs whether online or offline.
        lastHarvestHandled = true;
    }

    @Override
    public void onHarvestError() {
        // Server rejected the data (4xx/5xx); it was not accepted. Keep the manifest.
        lastHarvestHandled = false;
    }

    @Override
    public void applicationForegrounded(ApplicationStateEvent e) {
        // No-op: the next onHarvest() re-snapshots the (continuing or new) session.
    }

    /**
     * The app has gone to the background. The agent forces a final harvest before this
     * callback runs (it is registered ahead of us and blocks until the tick finishes), so
     * {@link #lastHarvestHandled} reflects that harvest's outcome. If the data was delivered
     * or persisted, drop the current session's manifest — it is no longer needed for a clean
     * shutdown, and the bounded store handles anything left behind.
     */
    @Override
    public void applicationBackgrounded(ApplicationStateEvent e) {
        if (lastHarvestHandled) {
            deleteCurrentSessionContext();
        }
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

    void deleteCurrentSessionContext() {
        SessionContextStore store = AgentConfiguration.getInstance().getSessionContextStore();
        if (store == null) {
            return;
        }
        String sessionId = AgentConfiguration.getInstance().getSessionID();
        if (sessionId != null && !sessionId.isEmpty()) {
            store.delete(sessionId);
        }
    }
}
