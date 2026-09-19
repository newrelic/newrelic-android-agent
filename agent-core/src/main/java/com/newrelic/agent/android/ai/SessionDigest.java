/*
 * Copyright (c) 2026-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Bounded, thread-safe accumulation of one session's telemetry, plus the prose rendering that is
 * handed to the on-device model.
 *
 * <h3>Privacy invariant</h3>
 * This class can only hold telemetry the agent already transmits to New Relic: activity and
 * interaction names, request method/path/status, timings, and counts. There is deliberately no
 * field capable of holding a custom attribute value, breadcrumb text, an exception message, a
 * request or response body, a header, or a URL query string. The invariant is structural -- there
 * is nothing to redact because there is nothing here to hold it.
 *
 * <h3>Bounding</h3>
 * Every collection is capped, so a 40-minute session renders a prompt the same size as a
 * 40-second one. The caps are the reason {@link #renderPromptText()} never needs to truncate.
 *
 * <h3>Determinism</h3>
 * The same accumulated state renders byte-identical text. Iteration order is insertion-ordered,
 * all formatting is {@link Locale#US}, and clause order is fixed.
 */
public class SessionDigest {

    /**
     * Bumped whenever the persisted representation changes meaning. A stored digest carrying a
     * different version is discarded rather than misread.
     */
    public static final int SCHEMA_VERSION = 1;

    static final int MAX_SCREENS = 12;
    static final int MAX_INTERACTIONS = 5;
    static final int MAX_HTTP_FAILURE_BUCKETS = 8;
    static final int MAX_UNIQUE_SCREEN_NAMES = 24;
    static final int MAX_PROMPT_CHARS = 2000;

    /**
     * Floor for the rendered record.
     *
     * ML Kit's ARTICLE input type rejects anything shorter outright. Rather than pad to clear it, the
     * renderer states the facts that <em>didn't</em> happen -- no crash, no failed requests, no slow
     * interactions -- which are real, derivable information that a summarizer can legitimately use,
     * and which happen to make even a trivial session comfortably exceed the floor. Verified against
     * an empty session, a single-screen session, and a rich one.
     */
    static final int MIN_PROMPT_CHARS = 400;

    static final double SLOW_INTERACTION_THRESHOLD_MS = 1000.0d;

    private final List<ScreenVisit> screens = new ArrayList<>();
    private final List<Interaction> interactions = new ArrayList<>();
    private final Map<String, Integer> httpFailures = new LinkedHashMap<>();

    /** Every distinct screen name seen this session, including ones elided from the visit sequence. */
    private final Set<String> uniqueScreenNames = new LinkedHashSet<>();

    private int elidedScreens;
    private int elidedHttpFailureBuckets;
    private int interactionsSeen;

    private long sessionDurationMs;
    private int httpRequestCount;
    private int httpFailureCount;
    private long bytesSent;
    private long bytesReceived;
    private int userActionCount;
    private int interactionCount;
    private boolean crashed;
    private boolean anr;

    private String slowestRequestLabel;
    private double slowestRequestMs = -1.0d;

    // App and device context. Already transmitted with every harvest, so including it introduces no
    // new data category -- it just gives the model something to ground the narrative in.
    private String appName;
    private String appVersion;
    private String deviceManufacturer;
    private String deviceModel;
    private String osName;
    private String osVersion;

    /**
     * Incremented on every fold. The controller compares this against the revision it last
     * summarized to know whether the tail of the session made it into any summary.
     */
    private int revision;

    // ---------------------------------------------------------------- accumulation

    /**
     * Records one screen visit. Called by {@link SessionDigestCollector}, which is the only thing
     * that knows about {@code HarvestData}; keeping that mapping outside this class is what lets the
     * rendering be exercised without constructing a harvest payload.
     */
    public synchronized void recordScreenVisit(String name, long entryTimestamp, long durationMs) {
        if (name == null || name.isEmpty()) {
            return;
        }

        screens.add(new ScreenVisit(name, entryTimestamp, Math.max(0L, durationMs)));

        if (uniqueScreenNames.size() < MAX_UNIQUE_SCREEN_NAMES) {
            uniqueScreenNames.add(name);
        }

        // Keep the visit sequence chronological regardless of the order traces were harvested in.
        Collections.sort(screens);
        boundScreens();
    }

    /**
     * Records app and device context. Called once per fold from the collector, which is the only side
     * that knows how to reach the agent's globals.
     */
    public synchronized void setContext(String appName, String appVersion, String deviceManufacturer,
                                       String deviceModel, String osName, String osVersion) {
        this.appName = appName;
        this.appVersion = appVersion;
        this.deviceManufacturer = deviceManufacturer;
        this.deviceModel = deviceModel;
        this.osName = osName;
        this.osVersion = osVersion;
    }

    /**
     * Clears the recorded visit sequence before it is re-read from TraceMachine's activity history.
     *
     * Screen visits are the one input that is <em>replaced</em> each cycle rather than appended to:
     * TraceMachine holds the whole session's sightings and updates the current one's duration in
     * place, so re-reading and replacing keeps dwell times current. Appending would duplicate every
     * screen once per harvest cycle.
     */
    public synchronized void resetScreenVisits() {
        screens.clear();
        elidedScreens = 0;
    }

    /**
     * Marks the end of one folded harvest cycle. The controller compares the resulting revision
     * against the one it last summarized to know whether the tail of the session is covered.
     */
    public synchronized void endCycle() {
        revision++;
    }

    /**
     * Drops from the middle of the sequence, which preserves where the session started and -- more
     * useful for debugging -- where it ended.
     */
    private void boundScreens() {
        while (screens.size() > MAX_SCREENS) {
            screens.remove(MAX_SCREENS / 2);
            elidedScreens++;
        }
    }

    /**
     * Records one network request. Failures are bucketed by method, path, and outcome rather than
     * listed individually, so a retry storm reads as one clause with a count instead of many
     * near-identical sentences.
     */
    public synchronized void recordHttpRequest(String method, String url, int statusCode, int errorCode,
                                               double durationMs, long sent, long received) {
        httpRequestCount++;
        bytesSent += Math.max(0L, sent);
        bytesReceived += Math.max(0L, received);

        final String path = pathOf(url);

        if (durationMs > slowestRequestMs) {
            slowestRequestMs = durationMs;
            slowestRequestLabel = normalizeMethod(method) + " " + path;
        }

        if (statusCode < 400 && errorCode == 0) {
            return;
        }

        httpFailureCount++;

        final String bucket = failureBucketKey(method, path, statusCode, errorCode);

        final Integer existing = httpFailures.get(bucket);
        if (existing != null) {
            httpFailures.put(bucket, existing + 1);
        } else if (httpFailures.size() < MAX_HTTP_FAILURE_BUCKETS) {
            httpFailures.put(bucket, 1);
        } else {
            elidedHttpFailureBuckets++;
        }
    }

    /**
     * Records one interaction. Only the slowest {@link #MAX_INTERACTIONS} are retained -- they are
     * the ones worth spending a sentence on. Retention is by duration; rendering is chronological.
     */
    public synchronized void recordInteraction(String name, double durationMs) {
        if (name == null || name.isEmpty() || durationMs < 0.0d) {
            return;
        }

        interactionCount++;
        interactions.add(new Interaction(name, durationMs, interactionsSeen++));

        Collections.sort(interactions);
        while (interactions.size() > MAX_INTERACTIONS) {
            interactions.remove(interactions.size() - 1);
        }
    }

    public synchronized void recordUserAction() {
        userActionCount++;
    }

    public synchronized void recordCrash() {
        crashed = true;
    }

    public synchronized void recordAnr() {
        anr = true;
    }

    public synchronized void setSessionDurationMs(long sessionDurationMs) {
        this.sessionDurationMs = sessionDurationMs;
    }

    public synchronized int getRevision() {
        return revision;
    }

    /**
     * @return true once there is enough here to be worth spending an inference on
     */
    public synchronized boolean isMaterial() {
        return screens.size() >= 2
                || !httpFailures.isEmpty()
                || crashed
                || anr
                || sessionDurationMs >= 30_000L;
    }

    // ---------------------------------------------------------------- rendering

    /**
     * Renders the digest as English prose.
     *
     * This text <em>is</em> the prompt for summarizers that accept no instructions (ML Kit's
     * GenAI summarization task is options-driven, with no instruction channel), so it is written
     * to be in-distribution for a model trained on articles and conversations rather than on
     * key/value telemetry. {@link SessionSummaryController} prepends an instruction block for
     * summarizers that do accept one.
     *
     * @return prose, never null, never longer than {@link #MAX_PROMPT_CHARS}
     */
    public synchronized String renderPromptText() {
        final List<String> lines = new ArrayList<>();

        final String contextLine = renderContextLine();
        if (contextLine != null) {
            lines.add(contextLine);
        }

        lines.add(renderSessionLine());

        final String screenLine = renderScreenLine();
        if (screenLine != null) {
            lines.add(screenLine);
        }

        lines.addAll(renderInteractionLines());
        lines.addAll(renderHttpFailureLines());

        final String trafficLine = renderTrafficLine();
        if (trafficLine != null) {
            lines.add(trafficLine);
        }

        final String slowestLine = renderSlowestRequestLine();
        if (slowestLine != null) {
            lines.add(slowestLine);
        }

        lines.addAll(renderOutcomeLines());
        lines.addAll(renderAbsenceLines());

        final StringBuilder prose = new StringBuilder();
        for (String line : lines) {
            if (prose.length() > 0) {
                prose.append('\n');
            }
            prose.append(line);
        }

        // Final guarantee for the input floor. Only a session with almost nothing in it can land here,
        // and such a session is normally gated out by isMaterial() long before reaching a model. When
        // it does happen, say plainly that there is little to describe rather than pad with filler --
        // that is honest, it is useful to a reader, and it keeps the floor an invariant of the renderer
        // instead of a property that happens to hold for the sessions we sampled.
        if (prose.length() < MIN_PROMPT_CHARS) {
            prose.append('\n')
                    .append("there is very little activity in this session to describe: nothing ")
                    .append("beyond what is listed above was recorded, no errors occurred, and the ")
                    .append("session ended without further user activity.");
        }

        // The caps above are what keep us inside the budget. Hitting this is a bug in the caps,
        // not a condition to handle gracefully, so it is loud in debug and harmless in production.
        if (prose.length() > MAX_PROMPT_CHARS) {
            return prose.substring(0, MAX_PROMPT_CHARS);
        }

        return prose.toString();
    }

    /**
     * Grounds the record in what app, on what device. Every value here already goes to New Relic with
     * each harvest, so it adds context without adding a data category.
     */
    private String renderContextLine() {
        if (isBlank(appName) && isBlank(deviceModel) && isBlank(osVersion)) {
            return null;
        }

        final StringBuilder line = new StringBuilder("this is a session of the ");
        line.append(isBlank(appName) ? "mobile app" : appName);

        if (!isBlank(appVersion)) {
            line.append(" app, version ").append(appVersion);
        } else {
            line.append(" app");
        }

        if (!isBlank(deviceModel)) {
            line.append(", running on a ");
            if (!isBlank(deviceManufacturer)) {
                line.append(deviceManufacturer).append(' ');
            }
            line.append(deviceModel);
        }

        if (!isBlank(osVersion)) {
            line.append(" with ").append(isBlank(osName) ? "Android" : osName)
                    .append(' ').append(osVersion);
        }

        line.append('.');
        return line.toString();
    }

    /**
     * The slowest request of the session, named. Useful even when nothing failed -- a slow endpoint is
     * often the thing an engineer is looking for.
     */
    private String renderSlowestRequestLine() {
        if (slowestRequestLabel == null || slowestRequestMs <= 0.0d) {
            return null;
        }

        return "the slowest network request was " + slowestRequestLabel + ", which took "
                + formatDuration(Math.round(slowestRequestMs)) + ".";
    }

    /**
     * States what did not happen.
     *
     * These are facts, not filler: "no requests failed" is genuinely different information from
     * silence, and it is what lets a summarizer say a session went cleanly rather than guess. It also
     * means even a twenty-second single-screen session clears {@link #MIN_PROMPT_CHARS}.
     */
    private List<String> renderAbsenceLines() {
        final List<String> lines = new ArrayList<>();

        if (httpFailureCount == 0 && httpRequestCount > 0) {
            lines.add("none of the network requests failed; every one returned a successful response.");
        }

        if (interactionCount > 0 && !hasSlowInteraction()) {
            lines.add("no interaction was slow enough to be worth noting; the app stayed responsive "
                    + "throughout the session.");
        } else if (interactionCount == 0) {
            lines.add("no user interactions were recorded during this session.");
        }

        if (!crashed && !anr) {
            lines.add("the app did not crash and did not stop responding at any point during this "
                    + "session.");
        }

        return lines;
    }

    private boolean hasSlowInteraction() {
        for (Interaction interaction : interactions) {
            if (interaction.durationMs >= SLOW_INTERACTION_THRESHOLD_MS) {
                return true;
            }
        }
        return false;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String renderSessionLine() {
        final int screenCount = screens.size() + elidedScreens;

        if (sessionDurationMs <= 0L && screenCount == 0) {
            return "the session was too short to record any activity.";
        }

        final StringBuilder line = new StringBuilder("the session lasted ")
                .append(formatDuration(sessionDurationMs));

        if (screenCount == 1) {
            line.append(" on a single screen.");
        } else if (screenCount > 1) {
            line.append(" across ").append(screenCount).append(" screen visits");

            final int unique = uniqueScreenNames.size();
            if (unique > 0 && unique < screenCount) {
                // Revisits are a signal in themselves: bouncing back to a screen repeatedly often
                // means the user did not get what they came for.
                line.append(" covering ").append(unique)
                        .append(unique == 1 ? " distinct screen" : " distinct screens");
            }

            line.append('.');
        } else {
            line.append('.');
        }

        return line.toString();
    }

    private String renderScreenLine() {
        if (screens.isEmpty()) {
            return null;
        }

        final StringBuilder line = new StringBuilder("the user opened ");
        for (int i = 0; i < screens.size(); i++) {
            final ScreenVisit visit = screens.get(i);

            if (i > 0) {
                line.append(i == screens.size() - 1 ? ", and then " : ", then ");
            }

            line.append(visit.name);
            if (visit.durationMs > 0L) {
                line.append(" for ").append(formatDuration(visit.durationMs));
            }
        }

        if (elidedScreens > 0) {
            line.append(", omitting ").append(elidedScreens)
                    .append(elidedScreens == 1 ? " other screen in between" : " other screens in between");
        }

        line.append('.');
        return line.toString();
    }

    /**
     * Renders only interactions slow enough to be worth a sentence, in the order they happened.
     *
     * A list of sub-second interactions is noise: it crowds out the signal and gives the model more
     * near-identical sentences to paraphrase, which makes the summary worse rather than richer. A
     * fast interaction is the expected case and says nothing.
     */
    private List<String> renderInteractionLines() {
        final List<String> slow = new ArrayList<>();

        final List<Interaction> chronological = new ArrayList<>(interactions);
        Collections.sort(chronological, Interaction.BY_SEQUENCE);

        for (Interaction interaction : chronological) {
            if (interaction.durationMs < SLOW_INTERACTION_THRESHOLD_MS) {
                continue;
            }

            slow.add("the " + interaction.name + " interaction took "
                    + formatDuration(Math.round(interaction.durationMs)) + ", which is slow.");
        }

        return slow;
    }

    private List<String> renderHttpFailureLines() {
        final List<String> lines = new ArrayList<>();

        for (Map.Entry<String, Integer> failure : httpFailures.entrySet()) {
            final int count = failure.getValue();
            final StringBuilder line = new StringBuilder();

            if (count == 1) {
                line.append("one request to ");
            } else {
                line.append(count).append(" requests to ");
            }

            line.append(failure.getKey()).append('.');
            lines.add(line.toString());
        }

        if (elidedHttpFailureBuckets > 0) {
            lines.add("other requests failed as well, in " + elidedHttpFailureBuckets
                    + " additional ways not listed here.");
        }

        return lines;
    }

    private String renderTrafficLine() {
        if (httpRequestCount == 0 && userActionCount == 0 && interactionCount == 0) {
            return "the app made no network requests and recorded no user activity during the session.";
        }

        final StringBuilder line = new StringBuilder("in total the app made ")
                .append(httpRequestCount)
                .append(httpRequestCount == 1 ? " network request" : " network requests");

        if (httpFailureCount > 0) {
            line.append(", of which ").append(httpFailureCount)
                    .append(httpFailureCount == 1 ? " failed" : " failed");
        }

        final long totalBytes = bytesSent + bytesReceived;
        if (totalBytes > 0L) {
            line.append(", transferring ").append(formatBytes(totalBytes));
        }

        if (userActionCount > 0) {
            line.append(", and the user performed ").append(userActionCount)
                    .append(userActionCount == 1 ? " action" : " actions");
        }

        if (interactionCount > 0) {
            line.append(" across ").append(interactionCount)
                    .append(interactionCount == 1 ? " tracked interaction" : " tracked interactions");
        }

        line.append('.');
        return line.toString();
    }

    /** Whole units only, and never a locale-dependent decimal separator. */
    static String formatBytes(long bytes) {
        if (bytes < 1024L) {
            return bytes + (bytes == 1L ? " byte" : " bytes");
        }
        if (bytes < 1024L * 1024L) {
            return (bytes / 1024L) + " KB";
        }
        return String.format(Locale.US, "%.1f MB", bytes / (1024.0d * 1024.0d));
    }

    private List<String> renderOutcomeLines() {
        final List<String> lines = new ArrayList<>();

        if (crashed) {
            lines.add("the app crashed during this session.");
        }
        if (anr) {
            lines.add("the app stopped responding during this session.");
        }

        if (!screens.isEmpty()) {
            lines.add("the user left the app while on the "
                    + screens.get(screens.size() - 1).name + " screen.");
        }

        return lines;
    }

    // ---------------------------------------------------------------- helpers

    /**
     * Builds the human-readable clause describing one failure bucket. Requests are bucketed by
     * method, path, and outcome rather than listed individually, so a retry storm reads as
     * "12 requests to POST /v1/orders failed with server error 500" instead of twelve sentences.
     */
    static String normalizeMethod(String method) {
        return (method == null || method.isEmpty()) ? "GET" : method.toUpperCase(Locale.US);
    }

    static String failureBucketKey(String method, String path, int statusCode, int errorCode) {
        final StringBuilder key = new StringBuilder();

        key.append(normalizeMethod(method));
        key.append(' ').append(path).append(" failed with ");

        if (statusCode >= 500) {
            key.append("server error ").append(statusCode);
        } else if (statusCode >= 400) {
            key.append("client error ").append(statusCode);
        } else {
            key.append("network error ").append(errorCode);
        }

        return key.toString();
    }

    /**
     * Reduces a URL to its path. Dropping the query string is not incidental -- it is where
     * tokens, session ids, and email addresses live, and the privacy invariant depends on them
     * never entering the digest.
     */
    static String pathOf(String url) {
        if (url == null || url.isEmpty()) {
            return "/";
        }

        String path = url;

        final int schemeEnd = path.indexOf("://");
        if (schemeEnd >= 0) {
            final int firstSlash = path.indexOf('/', schemeEnd + 3);
            path = firstSlash >= 0 ? path.substring(firstSlash) : "/";
        }

        final int query = path.indexOf('?');
        if (query >= 0) {
            path = path.substring(0, query);
        }

        final int fragment = path.indexOf('#');
        if (fragment >= 0) {
            path = path.substring(0, fragment);
        }

        return path.isEmpty() ? "/" : path;
    }

    /**
     * Locale-independent by construction: {@link Locale#US} is passed explicitly so a device set
     * to a comma-decimal locale renders the same text as one set to en-US.
     */
    static String formatDuration(long millis) {
        if (millis < 0L) {
            millis = 0L;
        }

        if (millis < 1000L) {
            return millis + (millis == 1L ? " millisecond" : " milliseconds");
        }

        if (millis < 60_000L) {
            final double seconds = millis / 1000.0d;
            if (millis % 1000L == 0L) {
                final long whole = millis / 1000L;
                return whole + (whole == 1L ? " second" : " seconds");
            }
            return String.format(Locale.US, "%.1f seconds", seconds);
        }

        final long minutes = millis / 60_000L;
        final long seconds = (millis % 60_000L) / 1000L;

        final StringBuilder formatted = new StringBuilder()
                .append(minutes).append(minutes == 1L ? " minute" : " minutes");

        if (seconds > 0L) {
            formatted.append(' ').append(seconds).append(seconds == 1L ? " second" : " seconds");
        }

        return formatted.toString();
    }

    // ---------------------------------------------------------------- value types

    private static final class ScreenVisit implements Comparable<ScreenVisit> {
        final String name;
        final long entryTimestamp;
        final long durationMs;

        ScreenVisit(String name, long entryTimestamp, long durationMs) {
            this.name = name;
            this.entryTimestamp = entryTimestamp;
            this.durationMs = durationMs;
        }

        @Override
        public int compareTo(ScreenVisit other) {
            return Long.compare(entryTimestamp, other.entryTimestamp);
        }
    }

    /**
     * Natural order is slowest first, so trimming the tail keeps the interactions worth mentioning.
     * {@link #BY_SEQUENCE} restores the order they actually happened in for rendering -- prose that
     * jumps backwards in time reads as though the events happened out of order.
     */
    private static final class Interaction implements Comparable<Interaction> {
        final String name;
        final double durationMs;
        final int sequence;

        static final java.util.Comparator<Interaction> BY_SEQUENCE =
                new java.util.Comparator<Interaction>() {
                    @Override
                    public int compare(Interaction left, Interaction right) {
                        return Integer.compare(left.sequence, right.sequence);
                    }
                };

        Interaction(String name, double durationMs, int sequence) {
            this.name = name;
            this.durationMs = durationMs;
            this.sequence = sequence;
        }

        @Override
        public int compareTo(Interaction other) {
            return Double.compare(other.durationMs, durationMs);
        }
    }
}
