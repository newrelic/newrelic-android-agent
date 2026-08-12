/*
 * Copyright (c) 2026-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.newrelic.agent.android.sessionReplay.recovery;

import android.app.ApplicationExitInfo;
import android.os.Build;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.metric.MetricNames;
import com.newrelic.agent.android.sessioncontext.SessionContextStore;
import com.newrelic.agent.android.sessioncontext.SessionManifest;
import com.newrelic.agent.android.stats.StatsEngine;
import com.newrelic.agent.android.util.Constants;
import com.newrelic.agent.android.util.Streams;

import java.io.BufferedReader;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Scans for Session Replay {@code .tmp} files left behind by a prior session that died
 * abnormally, and (when eligible) re-reports their events through the normal Session Replay
 * upload path so they are uploaded on the next launch. Runs once at SR init.
 *
 * <p>Recovered data is uploaded directly (not via the offline store) so it is sent regardless
 * of whether the {@code OfflineStorage} feature flag is enabled. Reporting is fire-and-forget:
 * an orphan is removed as soon as its events are submitted. The dead session's {@code sessionId}
 * is preserved so the replay is attributed to the session that actually produced it.
 */
public class SessionReplayOrphanRecoverer {
    private static final AgentLog log = AgentLogManager.getAgentLog();

    /** Sink for recovered replay bytes — defaults to {@code SessionReplayReporter::reportSessionReplayData}. */
    public interface ReplayUploader {
        /** @return true if the payload was submitted (reporter ready), false otherwise. */
        boolean upload(byte[] rawEventBytes, Map<String, Object> attributes);
    }

    private final File srDir;
    private final SessionContextStore contextStore;
    private final ReplayUploader uploader;
    private final String currentSessionId;
    private final long payloadTtlMs;

    public SessionReplayOrphanRecoverer(File srDir, SessionContextStore contextStore,
                                        ReplayUploader uploader, String currentSessionId,
                                        long payloadTtlMs) {
        this.srDir = srDir;
        this.contextStore = contextStore;
        this.uploader = uploader;
        this.currentSessionId = currentSessionId == null ? "" : currentSessionId;
        this.payloadTtlMs = payloadTtlMs;
    }

    /** Reasons whose buffered replay we want to upload. */
    private static boolean isAbnormal(Integer reason) {
        if (reason == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return false;
        }
        switch (reason) {
            case ApplicationExitInfo.REASON_ANR:
            case ApplicationExitInfo.REASON_CRASH:
            case ApplicationExitInfo.REASON_CRASH_NATIVE:
            case ApplicationExitInfo.REASON_LOW_MEMORY:
            case ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE:
                return true;
            default:
                return false;
        }
    }

    public void recover() {
        if (srDir == null || !srDir.isDirectory() || uploader == null) {
            return;
        }
        File[] files = srDir.listFiles((d, name) -> name.endsWith(".tmp"));
        if (files == null) {
            return;
        }
        for (File file : files) {
            try {
                recoverOne(file);
            } catch (Exception e) {
                log.error("SessionReplayOrphanRecoverer: error on [" + file.getName() + "]: " + e);
            }
        }
    }

    private void recoverOne(File file) {
        String sessionId = sessionIdFromFileName(file.getName());
        if (sessionId == null || sessionId.isEmpty() || sessionId.equals(currentSessionId)) {
            return; // not an orphan
        }

        // Read events; derive timestamps.
        JsonArray events = new JsonArray();
        long[] bounds = {0L, 0L}; // first, last
        if (!readEvents(file, events, bounds) || events.isEmpty()) {
            delete(file);
            return;
        }

        SessionManifest manifest = contextStore.get(sessionId);
        Integer exitReason = manifest != null ? manifest.getExitReason() : null;
        boolean reachedFull = manifest != null && Boolean.TRUE.equals(manifest.getReachedFullMode());

        boolean eligible = isAbnormal(exitReason) || reachedFull;
        if (!eligible) {
            // Could be a clean ERROR-mode buffer, or the exit reason isn't recorded yet.
            // Drop only if stale; otherwise retain for re-evaluation on a later launch.
            if (isStale(file)) {
                StatsEngine.get().inc(MetricNames.SUPPORTABILITY_SESSION_REPLAY_RECOVER_STALE);
                delete(file);
            }
            return;
        }

        boolean isFirstChunk = manifest == null || manifest.getIsFirstChunk() == null
                || Boolean.TRUE.equals(manifest.getIsFirstChunk());

        Map<String, Object> attrs = buildRecoveredAttributes(sessionId, bounds, isFirstChunk);

        // Fire-and-forget direct report (not OfflineStorage-gated). Submitted → orphan removed;
        // when OfflineStorage is enabled the reporter itself re-caches on a failed send.
        boolean submitted = uploader.upload(events.toString().getBytes(), attrs);
        if (submitted) {
            StatsEngine.get().inc(MetricNames.SUPPORTABILITY_SESSION_REPLAY_RECOVERED);
            delete(file);
        } else {
            // Reporter not ready yet — keep the orphan so a later launch can retry.
            log.debug("SessionReplayOrphanRecoverer: reporter not ready, retaining [" + file.getName() + "]");
        }
    }

    private Map<String, Object> buildRecoveredAttributes(String sessionId, long[] bounds, boolean isFirstChunk) {
        Map<String, Object> attrs = new LinkedHashMap<>();
        // Keys consumed by SessionReplaySender: timestamps, first-chunk, has-meta, and the
        // prior session's id (sender overrides sessionId from this map) so the replay is
        // attributed to the dead session rather than the current one.
        attrs.put(Constants.SessionReplay.FIRST_TIMESTAMP, bounds[0]);
        attrs.put(Constants.SessionReplay.LAST_TIMESTAMP, bounds[1]);
        attrs.put(Constants.SessionReplay.IS_FIRST_CHUNK, isFirstChunk);
        attrs.put(Constants.SessionReplay.HAS_META, "true");
        attrs.put(Constants.SessionReplay.SESSION_ID, sessionId);
        return attrs;
    }

    private boolean readEvents(File file, JsonArray out, long[] bounds) {
        Gson gson = new Gson();
        try (BufferedReader reader = Streams.newBufferedFileReader(file)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                JsonObject frame = gson.fromJson(line, JsonObject.class);
                if (frame.has("timestamp")) {
                    long t = frame.get("timestamp").getAsLong();
                    if (bounds[0] == 0L || t < bounds[0]) bounds[0] = t;
                    if (t > bounds[1]) bounds[1] = t;
                }
                out.add(frame);
            }
            return true;
        } catch (Exception e) {
            log.error("SessionReplayOrphanRecoverer: corrupt orphan [" + file.getName() + "]: " + e);
            StatsEngine.get().inc(MetricNames.SUPPORTABILITY_SESSION_REPLAY_RECOVER_CORRUPT);
            delete(file);
            return false;
        }
    }

    private boolean isStale(File file) {
        return payloadTtlMs > 0 && (System.currentTimeMillis() - file.lastModified()) > payloadTtlMs;
    }

    private void delete(File file) {
        if (file.exists() && !file.delete()) {
            log.debug("SessionReplayOrphanRecoverer: failed to delete [" + file.getName() + "]");
        }
    }

    private static String sessionIdFromFileName(String name) {
        // SESSION_REPLAY_FILE_MASK = "sessionReplaydata%s.%s" → prefix "sessionReplaydata", ext ".tmp"
        final String prefix = String.format(Locale.US, Constants.SessionReplay.SESSION_REPLAY_FILE_MASK, "", "")
                .replace(".", ""); // "sessionReplaydata"
        if (name.startsWith(prefix) && name.endsWith(".tmp")) {
            return name.substring(prefix.length(), name.length() - ".tmp".length());
        }
        return null;
    }
}
