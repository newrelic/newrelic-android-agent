/*
 * Copyright (c) 2026-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.newrelic.agent.android.sessionReplay.recovery;

import android.app.ApplicationExitInfo;
import android.content.Context;

import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.SpyContext;
import com.newrelic.agent.android.sessionReplay.OfflineSessionReplayPayload;
import com.newrelic.agent.android.sessionReplay.OfflineSessionReplayStore;
import com.newrelic.agent.android.sessioncontext.FileSessionContextStore;
import com.newrelic.agent.android.sessioncontext.SessionContextStore;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class SessionReplayOrphanRecovererTest {

    static class InMemoryOfflineStore implements OfflineSessionReplayStore {
        final List<OfflineSessionReplayPayload> items = new ArrayList<>();
        @Override public boolean store(OfflineSessionReplayPayload d) { items.add(d); return true; }
        @Override public List<OfflineSessionReplayPayload> fetchAll() { return new ArrayList<>(items); }
        @Override public int count() { return items.size(); }
        @Override public void clear() { items.clear(); }
        @Override public void delete(OfflineSessionReplayPayload d) { items.remove(d); }
    }

    private Context context;
    private File srDir;
    private SessionContextStore ctxStore;
    private InMemoryOfflineStore offlineStore;

    private File writeOrphan(String sessionId) throws Exception {
        File f = new File(srDir, "sessionReplaydata" + sessionId + ".tmp");
        try (FileWriter w = new FileWriter(f)) {
            w.write("{\"type\":2,\"timestamp\":1000}\n{\"type\":3,\"timestamp\":2000}\n");
        }
        return f;
    }

    @Before
    public void setUp() {
        context = new SpyContext().getContext();
        srDir = new File(context.getCacheDir(), "newrelic/sessionReplay/");
        srDir.mkdirs();
        ctxStore = new FileSessionContextStore(context, new AgentConfiguration());
        offlineStore = new InMemoryOfflineStore();
        AgentConfiguration.getInstance().setSessionContextStore(ctxStore);
        AgentConfiguration.getInstance().setOfflineSessionReplayStore(offlineStore);
    }

    @Test
    public void recoversAbnormalExitOrphan() throws Exception {
        writeOrphan("S_DEAD");
        ctxStore.updateExitReason("S_DEAD", ApplicationExitInfo.REASON_ANR);

        new SessionReplayOrphanRecoverer(context, srDir, ctxStore, offlineStore, "S_CURRENT", 86_400_000L)
                .recover();

        Assert.assertEquals(1, offlineStore.count());
        Assert.assertEquals("true",
                offlineStore.fetchAll().get(0).getAttributes().get("recovered"));
        Assert.assertFalse(new File(srDir, "sessionReplaydataS_DEAD.tmp").exists());
    }

    @Test
    public void recoversReachedFullModeOrphan() throws Exception {
        writeOrphan("S_FULL");
        ctxStore.updateSessionReplayState("S_FULL", true, true);

        new SessionReplayOrphanRecoverer(context, srDir, ctxStore, offlineStore, "S_CURRENT", 86_400_000L)
                .recover();

        Assert.assertEquals(1, offlineStore.count());
    }

    @Test
    public void retainsIneligibleCleanOrphan() throws Exception {
        writeOrphan("S_CLEAN"); // no exit reason, never reached full

        new SessionReplayOrphanRecoverer(context, srDir, ctxStore, offlineStore, "S_CURRENT", 86_400_000L)
                .recover();

        Assert.assertEquals(0, offlineStore.count());
        Assert.assertTrue("ineligible orphan is retained for re-evaluation, not deleted",
                new File(srDir, "sessionReplaydataS_CLEAN.tmp").exists());
    }

    @Test
    public void skipsCurrentSession() throws Exception {
        writeOrphan("S_CURRENT");
        ctxStore.updateExitReason("S_CURRENT", ApplicationExitInfo.REASON_ANR);

        new SessionReplayOrphanRecoverer(context, srDir, ctxStore, offlineStore, "S_CURRENT", 86_400_000L)
                .recover();

        Assert.assertEquals(0, offlineStore.count());
    }
}