
/*
 * Copyright (c) 2026-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.ai;

/**
 * The prompt text, and the normalization applied to whatever the model gives back.
 *
 * Both live here rather than inside the summarizer adapters because neither can be covered in CI
 * once it is on the far side of a model call -- emulators do not ship AICore. Keeping the prompt
 * and the output handling in plain JVM code means the parts most likely to need iteration are the
 * parts that are cheapest to test.
 */
public final class SessionSummaryPrompt {

    /**
     * Prepended for summarizers that accept a free-form prompt. Written for an engineer reading
     * the result in New Relic, and deliberately defensive about the two failure modes that make a
     * summary worse than no summary: inventing detail that was never in the telemetry, and
     * restating the telemetry verbatim so the summary adds nothing.
     */
    public static final String INSTRUCTIONS =
            "You are summarizing one mobile app session for an engineer debugging their app.\n"
                    + "The RECORD below is factual telemetry collected on the device.\n"
                    + "Write 2-3 sentences: what the user did, and what went wrong if anything.\n"
                    + "Name specific screens and failures.\n"
                    + "Do not speculate about the user's intent beyond what the record shows.\n"
                    + "Do not invent details that are not in the record.\n"
                    + "Do not repeat the record verbatim.\n";

    static final String RECORD_HEADER = "\nRECORD:\n";
    static final String SUMMARY_FOOTER = "\nSUMMARY:\n";

    /**
     * Upper bound on the value written to the {@code sessionSummary} attribute. The validator's
     * real ceiling is {@code ATTRIBUTE_VALUE_MAX_LENGTH} (4096 bytes); this is far tighter,
     * because per-session ingest cost is the whole point of the feature and three sentences do not
     * need a kilobyte.
     */
    public static final int MAX_SUMMARY_CHARS = 1000;

    private SessionSummaryPrompt() {
    }

    /**
     * @param prose              from {@link SessionDigest#renderPromptText()}
     * @param withInstructions   true for summarizers that accept a free-form prompt
     */
    public static String compose(String prose, boolean withInstructions) {
        if (prose == null) {
            prose = "";
        }

        if (!withInstructions) {
            return prose;
        }

        return INSTRUCTIONS + RECORD_HEADER + prose + SUMMARY_FOOTER;
    }

    /**
     * Flattens raw model output into something safe to put in an attribute value.
     *
     * To be clear about what this is: normalization for ingest correctness, not a privacy filter.
     * An attribute value carrying embedded newlines is a correctness problem regardless of what the
     * text says. The guarantee that no sensitive category reaches the model at all is structural
     * and lives in {@link SessionDigest}.
     *
     * @return the normalized summary, or null if the model returned nothing usable
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }

        final String[] lines = raw.split("\\R");
        final StringBuilder normalized = new StringBuilder();

        for (String line : lines) {
            final String bullet = stripBulletMarker(line).trim();
            if (bullet.isEmpty()) {
                continue;
            }

            if (normalized.length() > 0) {
                // A bullet that already ends in terminal punctuation is a sentence; joining those
                // with a semicolon produces "...checked out.; Three orders failed...".
                normalized.append(endsSentence(normalized) ? " " : "; ");
            }
            normalized.append(bullet);
        }

        // Any remaining control characters (tabs, vertical tabs, form feeds) become spaces.
        final String flattened = normalized.toString()
                .replaceAll("\\p{Cntrl}", " ")
                .replaceAll("\\s{2,}", " ")
                .trim();

        if (flattened.isEmpty()) {
            return null;
        }

        return flattened.length() > MAX_SUMMARY_CHARS
                ? flattened.substring(0, MAX_SUMMARY_CHARS).trim()
                : flattened;
    }

    private static boolean endsSentence(CharSequence text) {
        if (text.length() == 0) {
            return false;
        }

        final char last = text.charAt(text.length() - 1);
        return last == '.' || last == '!' || last == '?';
    }

    /**
     * Removes list decoration the model adds when asked for bullets: {@code * }, {@code - },
     * {@code • }, and {@code 1. } style numbering.
     */
    static String stripBulletMarker(String line) {
        final String trimmed = line.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }

        return trimmed.replaceFirst("^(?:[*\\-•·]+|\\d+[.)])\\s*", "");
    }
}
