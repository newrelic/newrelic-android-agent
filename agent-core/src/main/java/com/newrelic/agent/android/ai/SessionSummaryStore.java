/*
 * Copyright (c) 2026-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.ai;

/**
 * Carries one session's rendered prompt text across process death, so a session whose tail never
 * made it into a summary can be summarized on the next launch instead of being lost.
 *
 * Only the rendered prose is stored, not the structured digest. The deferred path has no use for
 * the structure -- it feeds the same text to the same model -- and storing prose means there is no
 * deserialization of accumulated state to get wrong.
 */
public interface SessionSummaryStore {

    /**
     * @return true if the digest is durably stored
     */
    boolean save(PendingDigest pending);

    /**
     * @return the stored digest, or null if there is none, it is unreadable, or it was written by
     * an agent using a different {@link SessionDigest#SCHEMA_VERSION}
     */
    PendingDigest load();

    void clear();

    final class PendingDigest {
        public final int schemaVersion;
        public final String sessionId;
        public final String promptText;

        public PendingDigest(int schemaVersion, String sessionId, String promptText) {
            this.schemaVersion = schemaVersion;
            this.sessionId = sessionId;
            this.promptText = promptText;
        }

        public boolean isUsable() {
            return schemaVersion == SessionDigest.SCHEMA_VERSION
                    && sessionId != null && !sessionId.isEmpty()
                    && promptText != null && !promptText.isEmpty();
        }
    }
}
