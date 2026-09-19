/*
 * Copyright (c) 2026-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.ai;

import android.content.Context;

import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;

/**
 * File-backed {@link SessionSummaryStore}, holding at most one pending digest at a time.
 *
 * Lives under the cache directory alongside {@code newrelic/applicationExitInfo}, following the
 * same convention as {@code ApplicationExitMonitor}. Cache is the right choice: losing a pending
 * digest to OS cache eviction costs one deferred summary, which is exactly the kind of loss this
 * feature should absorb silently.
 *
 * <h3>Format</h3>
 * <pre>
 * line 1  schema version
 * line 2  session id
 * line 3+ rendered prompt text
 * </pre>
 * Deliberately not JSON. The payload is prose that already contains newlines and arbitrary
 * punctuation; a line-oriented format has nothing to escape and nothing to get wrong, and a
 * truncated file fails the version or id check rather than parsing into something plausible.
 */
public class SessionSummaryFileStore implements SessionSummaryStore {

    private static final AgentLog log = AgentLogManager.getAgentLog();

    static final String STORE_DIR = "newrelic/sessionSummary";
    static final String STORE_FILE = "pendingDigest";
    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private final File storeFile;

    public SessionSummaryFileStore(Context context) {
        this.storeFile = new File(new File(context.getCacheDir(), STORE_DIR), STORE_FILE);
    }

    @Override
    public boolean save(PendingDigest pending) {
        if (pending == null || !pending.isUsable()) {
            return false;
        }

        final File parent = storeFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            log.debug("SessionSummary: could not create store directory " + parent);
            return false;
        }

        BufferedWriter writer = null;
        try {
            writer = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(storeFile, false), UTF_8));

            writer.write(Integer.toString(pending.schemaVersion));
            writer.write('\n');
            writer.write(pending.sessionId);
            writer.write('\n');
            writer.write(pending.promptText);
            writer.flush();

            return true;
        } catch (Throwable t) {
            log.debug("SessionSummary: failed to save pending digest: " + t);
            return false;
        } finally {
            closeQuietly(writer);
        }
    }

    @Override
    public PendingDigest load() {
        if (!storeFile.exists()) {
            return null;
        }

        BufferedReader reader = null;
        try {
            reader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(storeFile), UTF_8));

            final String versionLine = reader.readLine();
            final String sessionId = reader.readLine();
            if (versionLine == null || sessionId == null) {
                return null;
            }

            final int schemaVersion;
            try {
                schemaVersion = Integer.parseInt(versionLine.trim());
            } catch (NumberFormatException e) {
                log.debug("SessionSummary: pending digest has an unreadable schema version");
                return null;
            }

            final StringBuilder promptText = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (promptText.length() > 0) {
                    promptText.append('\n');
                }
                promptText.append(line);
            }

            return new PendingDigest(schemaVersion, sessionId, promptText.toString());
        } catch (Throwable t) {
            log.debug("SessionSummary: failed to load pending digest: " + t);
            return null;
        } finally {
            closeQuietly(reader);
        }
    }

    @Override
    public void clear() {
        try {
            if (storeFile.exists() && !storeFile.delete()) {
                log.debug("SessionSummary: could not delete " + storeFile);
            }
        } catch (Throwable t) {
            log.debug("SessionSummary: failed to clear pending digest: " + t);
        }
    }

    private static void closeQuietly(java.io.Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (IOException e) {
            // nothing useful to do
        }
    }
}
