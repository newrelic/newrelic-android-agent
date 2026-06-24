/*
 * Copyright (c) 2026-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.newrelic.agent.android.sessionReplay.recovery;

import android.app.ApplicationExitInfo;
import android.content.Context;

import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.SpyContext;
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
import java.util.Map;

@RunWith(RobolectricTestRunner.class)
public class SessionReplayOrphanRecovererTest {

    /** Capturing uploader that records each recovered payload's attributes. */
    static class CapturingUploader implements SessionReplayOrphanRecoverer.ReplayUploader {
        final List<Map<String, Object>> uploads = new ArrayList<>();
        boolean result = true;

        @Override
        public boolean upload(byte[] rawEventBytes, Map<String, Object> attributes) {
            uploads.add(attributes);
            return result;
        }
    }

    private Context context;
    private File srDir;
    private SessionContextStore ctxStore;
    private CapturingUploader uploader;

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
        uploader = new CapturingUploader();
    }

    @Test
    public void recoversAbnormalExitOrphan() throws Exception {
        writeOrphan("S_DEAD");
        ctxStore.updateExitReason("S_DEAD", ApplicationExitInfo.REASON_ANR);

        new SessionReplayOrphanRecoverer(srDir, ctxStore, uploader, "S_CURRENT", 86_400_000L)
                .recover();

        Assert.assertEquals(1, uploader.uploads.size());
        Map<String, Object> attrs = uploader.uploads.get(0);
        Assert.assertEquals("true", attrs.get("recovered"));
        // Replay must be attributed to the dead session, not the current one.
        Assert.assertEquals("S_DEAD", attrs.get("sessionId"));
        Assert.assertFalse(new File(srDir, "sessionReplaydataS_DEAD.tmp").exists());
    }

    @Test
    public void recoversReachedFullModeOrphan() throws Exception {
        writeOrphan("S_FULL");
        ctxStore.updateSessionReplayState("S_FULL", true, true);

        new SessionReplayOrphanRecoverer(srDir, ctxStore, uploader, "S_CURRENT", 86_400_000L)
                .recover();

        Assert.assertEquals(1, uploader.uploads.size());
    }

    @Test
    public void retainsIneligibleCleanOrphan() throws Exception {
        writeOrphan("S_CLEAN"); // no exit reason, never reached full

        new SessionReplayOrphanRecoverer(srDir, ctxStore, uploader, "S_CURRENT", 86_400_000L)
                .recover();

        Assert.assertEquals(0, uploader.uploads.size());
        Assert.assertTrue("ineligible orphan is retained for re-evaluation, not deleted",
                new File(srDir, "sessionReplaydataS_CLEAN.tmp").exists());
    }

    @Test
    public void skipsCurrentSession() throws Exception {
        writeOrphan("S_CURRENT");
        ctxStore.updateExitReason("S_CURRENT", ApplicationExitInfo.REASON_ANR);

        new SessionReplayOrphanRecoverer(srDir, ctxStore, uploader, "S_CURRENT", 86_400_000L)
                .recover();

        Assert.assertEquals(0, uploader.uploads.size());
    }

    @Test
    public void retainsOrphanWhenUploaderNotReady() throws Exception {
        writeOrphan("S_DEAD");
        ctxStore.updateExitReason("S_DEAD", ApplicationExitInfo.REASON_ANR);
        uploader.result = false; // reporter not ready — submission fails

        new SessionReplayOrphanRecoverer(srDir, ctxStore, uploader, "S_CURRENT", 86_400_000L)
                .recover();

        Assert.assertEquals(1, uploader.uploads.size());
        Assert.assertTrue("orphan retained for retry when the report was not submitted",
                new File(srDir, "sessionReplaydataS_DEAD.tmp").exists());
    }
}