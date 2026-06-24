/*
 * Copyright (c) 2026-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.newrelic.agent.android.sessioncontext;

import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.harvest.DataToken;
import com.newrelic.agent.android.harvest.Harvest;
import com.newrelic.agent.android.harvest.HarvestConfiguration;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.logging.ConsoleAgentLog;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SessionContextManagerTest {

    static class InMemorySessionContextStore implements SessionContextStore {
        final Map<String, SessionManifest> map = new HashMap<>();

        @Override public boolean upsert(SessionManifest m) { map.put(m.getSessionId(), m); return true; }
        @Override public SessionManifest get(String id) { return map.get(id); }
        @Override public List<SessionManifest> fetchAll() { return new ArrayList<>(map.values()); }
        @Override public void delete(String id) { map.remove(id); }
        @Override public int count() { return map.size(); }
        @Override public void clear() { map.clear(); }
        @Override public void updateSessionReplayState(String id, boolean reachedFullMode, boolean isFirstChunk) {
            SessionManifest e = map.get(id);
            map.put(id, new SessionManifest(SessionManifest.CURRENT_SCHEMA_VERSION, id,
                    e != null ? e.getRealAgentId() : 0,
                    e != null ? e.getSessionStartMs() : 0L,
                    e != null ? e.getLastUpdateMs() : 0L,
                    e != null ? e.getAttributes() : null,
                    reachedFullMode, isFirstChunk, e != null ? e.getExitReason() : null));
        }
        @Override public void updateExitReason(String id, int exitReason) {
            SessionManifest e = map.get(id);
            if (e == null) { return; }
            map.put(id, new SessionManifest(SessionManifest.CURRENT_SCHEMA_VERSION, id, e.getRealAgentId(),
                    e.getSessionStartMs(), e.getLastUpdateMs(), e.getAttributes(),
                    e.getReachedFullMode(), e.getIsFirstChunk(), Integer.valueOf(exitReason)));
        }
    }

    private InMemorySessionContextStore store;

    @BeforeClass
    public static void beforeClass() {
        AgentLogManager.setAgentLog(new ConsoleAgentLog());
        HarvestConfiguration harvestConfig = Harvest.getHarvestConfiguration();
        harvestConfig.setData_token(new DataToken(1, 2).asIntArray());
    }

    @Before
    public void setUp() {
        store = new InMemorySessionContextStore();
        AgentConfiguration.getInstance().setSessionContextStore(store);
    }

    @After
    public void tearDown() {
        AgentConfiguration.getInstance().setSessionContextStore(null);
    }

    @Test
    public void snapshotWritesManifestForCurrentSession() {
        new SessionContextManager().snapshotCurrentSessionContext();

        String sessionId = AgentConfiguration.getInstance().getSessionID();
        SessionManifest manifest = store.get(sessionId);
        Assert.assertNotNull(manifest);
        Assert.assertEquals(sessionId, manifest.getSessionId());
    }

    @Test
    public void onHarvestDelegatesToSnapshot() {
        new SessionContextManager().onHarvest();

        Assert.assertEquals(1, store.count());
    }

    @Test
    public void noStoreConfiguredIsNoOp() {
        AgentConfiguration.getInstance().setSessionContextStore(null);
        // Must not throw.
        new SessionContextManager().snapshotCurrentSessionContext();
    }

    @Test
    public void initializeReturnsSameInstanceAndReRegistersIdempotently() {
        try {
            SessionContextManager first = SessionContextManager.initialize();
            // A second initialize() (as happens on a stop/start cycle) must reuse the singleton
            // and re-register without throwing or double-registering.
            SessionContextManager second = SessionContextManager.initialize();
            Assert.assertSame(first, second);
        } finally {
            SessionContextManager.shutdown();
        }
    }
}
