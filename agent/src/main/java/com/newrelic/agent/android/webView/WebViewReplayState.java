/*
 * Copyright (c) 2026-present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.webView;

import android.webkit.WebView;

import com.google.gson.JsonObject;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.metric.MetricNames;
import com.newrelic.agent.android.sessionReplay.SessionReplay;
import com.newrelic.agent.android.sessionReplay.internal.NewRelicIdGenerator;
import com.newrelic.agent.android.sessionReplay.viewMapper.SessionReplayWebViewThingy;
import com.newrelic.agent.android.sessionReplay.viewMapper.ViewDetails;
import com.newrelic.agent.android.stats.StatsEngine;

import com.newrelic.agent.android.util.NamedThreadFactory;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Merge state for one WebView, plus the sequencing that drives {@link WebViewReplayMerger}.
 *
 * Owned by that WebView's {@code WebViewJSInterface}. Because the bridge is already per-WebView,
 * there is no registry and no keying problem: several concurrent WebViews are isolated for free,
 * each with its own ID mapping, wait buffer, and iframe node ID. The only shared resource is
 * {@link NewRelicIdGenerator}, which is an {@code AtomicInteger}.
 *
 * Threading: {@link #onNavigationStarted()} and {@link #onRegistered()} run on the UI thread, from
 * the page lifecycle callbacks. {@link #processEvent} runs on the merge executor. Every public
 * method is synchronized on this instance.
 */
public class WebViewReplayState {

    private static final AgentLog log = AgentLogManager.getAgentLog();
    private static final String LOG_TAG = "[NR-WV-SR]";

    /**
     * Bounds on the wait buffer, whichever fires first. The cap bounds memory; the deadline
     * terminates the wait when the WebView is never redrawn into a native snapshot at all — the case
     * where the gate would otherwise stay shut forever.
     */
    static final int MAX_PENDING_EVENTS = 200;
    static final long PENDING_DEADLINE_MS = 15_000L;

    /** Node IDs come from the shared native generator, so a collision is impossible by construction. */
    private static final WebViewReplayMerger.IdAllocator ALLOCATOR = NewRelicIdGenerator::generateId;

    /**
     * Where all merge work runs. Off the WebView's JS thread, because remapping a several-thousand-node
     * document there would stall the page, and off the UI thread, because re-attaching a cached
     * document means writing megabytes. Single-threaded so per-WebView state needs no further
     * synchronisation for ordering and events are processed in arrival order; shared across every
     * WebView, which also gives a total order between concurrent ones.
     */
    private static final ExecutorService MERGE_EXECUTOR = Executors.newSingleThreadExecutor(
            new NamedThreadFactory("NRWebViewReplayMerge"));

    /**
     * Every live per-WebView state, so a native full snapshot can be broadcast to all of them.
     *
     * Weak keys: the state is reachable only from the bridge, which is reachable only from the
     * WebView, so an entry clears itself once that WebView is collected. This is the one thing the
     * per-WebView-bridge design cannot supply on its own — the bridge answers "which WebView is this
     * event for", and a broadcast needs the opposite direction.
     */
    private static final Set<WebViewReplayState> LIVE_STATES =
            Collections.newSetFromMap(new WeakHashMap<>());

    /** Posts work onto the merge thread. */
    public static void post(Runnable task) {
        try {
            MERGE_EXECUTOR.execute(task);
        } catch (Throwable t) {
            log.error(LOG_TAG + " could not schedule merge work", t);
        }
    }

    /**
     * Re-attaches every cached WebView document after a native full snapshot.
     *
     * This is not an optimisation, it is what keeps WebView content on screen. A native full snapshot
     * makes the replayer reset its mirror and rebuild from the snapshot's node tree, and the iframe in
     * that tree is empty — so the child document grafted earlier is gone. Since the native agent
     * forces a full snapshot at every harvest boundary, without this the WebView is blank for every
     * chunk in which the browser agent did not happen to check out. Device measurement: 92% of such a
     * chunk blank, and four consecutive chunks at 94–100%.
     *
     * @param timestampMs the snapshot's timestamp; the re-attached document carries the same one, so
     *                    the harvest sort's type ordering (FullSnapshot before Incremental) places it
     *                    immediately after the snapshot that made it necessary
     */
    public static void onNativeFullSnapshot(long timestampMs) {
        List<WebViewReplayState> states;
        synchronized (WebViewReplayState.class) {
            states = new ArrayList<>(LIVE_STATES);
        }
        for (WebViewReplayState state : states) {
            post(() -> state.reattachCachedDocument(timestampMs));
        }
    }

    private final WeakReference<WebView> webViewRef;

    /**
     * The native node ID of the {@code <iframe>} standing in for this WebView. Resolved exactly once,
     * on the UI thread, because view tags are not safe to touch off it — and bridge calls arrive on
     * the WebView's JS thread. Events that arrive before resolution go into the same buffer as the
     * snapshot wait, so no bridge call ever reaches a {@code View}.
     */
    private Integer iframeNodeId;

    /**
     * When this WebView was registered for merging. Long.MAX_VALUE means "not registered", which
     * keeps the gate shut.
     */
    private long registeredAtMs = Long.MAX_VALUE;

    private int generation;
    private Map<Integer, Integer> webToNative = new HashMap<>();
    private Integer childDocumentNativeId;
    private boolean graftEmitted;

    private final List<JsonObject> pending = new ArrayList<>();
    private long firstBufferedAtMs;

    /**
     * The most recent grafted document, serialized and already remapped into native IDs, kept so it
     * can be re-attached after a native full snapshot. Held as a string rather than a Gson tree: a
     * real page runs to megabytes, and the tree form costs several times as much memory.
     */
    private String lastGraftedDocumentJson;

    public WebViewReplayState(WebView webView) {
        this.webViewRef = new WeakReference<>(webView);
        synchronized (WebViewReplayState.class) {
            LIVE_STATES.add(this);
        }
        // Registered lazily on first construction: nothing needs re-attaching until a WebView exists,
        // and this avoids the session replay package depending on this one.
        SessionReplay.setFullSnapshotListener(WebViewReplayState::onNativeFullSnapshot);
    }

    /**
     * Re-emits the cached document so it survives a native full snapshot. Merge thread.
     */
    private synchronized void reattachCachedDocument(long timestampMs) {
        if (lastGraftedDocumentJson == null || iframeNodeId == null) {
            return;         // nothing has been grafted onto this WebView yet
        }
        if (webViewRef.get() == null) {
            lastGraftedDocumentJson = null;      // the WebView is gone; stop holding its DOM
            return;
        }
        SessionReplay.recordWebViewReplayEvent(WebViewReplayMerger.buildGraftEventJson(
                iframeNodeId, lastGraftedDocumentJson, timestampMs));
        graftEmitted = true;
        StatsEngine.SUPPORTABILITY.inc(
                MetricNames.SUPPORTABILITY_MOBILE_ANDROID_WEBVIEW_REPLAY_REGRAFTED);
        log.debug(LOG_TAG + " re-attached document " + childDocumentNativeId + " to iframe "
                + iframeNodeId + " after a native full snapshot at " + timestampMs);
    }

    /**
     * Navigation. The previous document's node IDs are dead, so the mapping and any buffered events
     * for it go, and the grafted subtree is explicitly removed rather than left to be overwritten.
     *
     * <p>UI thread.</p>
     */
    public synchronized void onNavigationStarted() {
        generation++;
        webToNative = new HashMap<>();

        int evicted = pending.size();
        pending.clear();
        firstBufferedAtMs = 0L;

        if (graftEmitted && iframeNodeId != null && childDocumentNativeId != null) {
            emit(WebViewReplayMerger.buildDocumentRemoval(
                    iframeNodeId, childDocumentNativeId, System.currentTimeMillis()));
        }

        graftEmitted = false;
        childDocumentNativeId = null;
        // The old document belongs to the page being navigated away from, and its web IDs are dead.
        // Re-attaching it after a later full snapshot would put the previous page back on screen.
        lastGraftedDocumentJson = null;
        // registeredAtMs is deliberately NOT reset here, so navigating does not force another native
        // full snapshot. The iframe's node ID comes from a tag on the View and is stable across
        // navigations, so the node the next graft attaches to is already in the stream. Forcing a
        // snapshot would only wipe the mirror — and with it any grafted document — for nothing.
        // Chunk self-containment after a harvest is a separate condition inside isGateOpen().

        log.debug(LOG_TAG + " navigation: generation=" + generation
                + (evicted > 0 ? ", discarded " + evicted + " buffered event(s)" : ""));
    }

    /**
     * Page load finished: resolve the iframe node ID and ask for a native full snapshot, so the node
     * the graft hangs off exists in the stream before the graft references it.
     *
     * <p>UI thread.</p>
     */
    public synchronized void onRegistered() {
        WebView webView = webViewRef.get();
        if (webView == null) {
            log.debug(LOG_TAG + " skipping registration; the WebView is gone");
            return;
        }
        // Cheap and idempotent: the tag is already allocated after the first call.
        iframeNodeId = ViewDetails.getStableId(webView);

        // Force a native full snapshot ONLY when this document generation does not have one yet.
        //
        // onPageFinished is not once-per-page. Redirects, consent interstitials and SPA route
        // changes fire it repeatedly — measured at 13 times in 65 seconds on a real page. Forcing a
        // snapshot on each one is actively destructive rather than merely wasteful: a native full
        // snapshot makes the replayer reset its mirror and rebuild the document from scratch, which
        // DESTROYS the child document already grafted onto this iframe. The WebView then renders
        // blank until the browser agent's next checkout, which is minutes away.
        //
        // Long.MAX_VALUE is the "needs a snapshot" marker: it is the initial value and what
        // onNavigationStarted resets to, so exactly one snapshot is forced per document generation.
        if (registeredAtMs == Long.MAX_VALUE) {
            registeredAtMs = System.currentTimeMillis();
            SessionReplay.setTakeFullSnapshot(true);
            log.debug(LOG_TAG + " registered: iframeNodeId=" + iframeNodeId
                    + " generation=" + generation + " at=" + registeredAtMs);
        } else {
            log.debug(LOG_TAG + " re-registration ignored; generation " + generation
                    + " already requested its native snapshot (iframeNodeId=" + iframeNodeId + ")");
        }
    }

    /**
     * Merges one event from the WebView's rrweb stream, buffering it if the iframe node it depends on
     * is not in the stream yet.
     *
     * <p>Merge executor thread.</p>
     */
    public synchronized void processEvent(JsonObject event) {
        if (event == null) {
            return;
        }
        if (webViewRef.get() == null) {
            // The normal outcome of a destroyed WebView, not a failure: debug, not warn.
            log.debug(LOG_TAG + " dropping an event; the WebView is gone");
            return;
        }
        if (!isGateOpen()) {
            buffer(event);
            return;
        }
        drainPending();
        handle(event);
    }

    /**
     * Whether the iframe node this WebView maps to is present in the current chunk of the replay
     * stream, so a graft can reference it as a {@code parentId}.
     *
     * Reads the render stamp the {@code <iframe>} mapper writes rather than "a full snapshot happened
     * after registration": the weaker signal is satisfied by a snapshot the WebView was not in — it
     * was off-screen, or not yet laid out — and would emit a graft whose {@code parentId} never
     * existed.
     *
     * Also gated on the last harvest, because a harvest truncates the working buffer: a graft written
     * after the clear but before the next native snapshot would land in a chunk with no tree to
     * attach to. Waiting is cheap — the buffer holds the newest snapshot, native draws are debounced
     * to about one per second, and a harvest forces a full one.
     */
    private boolean isGateOpen() {
        if (iframeNodeId == null) {
            return false;
        }
        long renderedAtMs = SessionReplayWebViewThingy.lastRenderedAtMs(iframeNodeId);
        return renderedAtMs >= registeredAtMs
                && renderedAtMs >= SessionReplay.getLastHarvestClearedAtMs();
    }

    private void buffer(JsonObject event) {
        if (pending.isEmpty()) {
            firstBufferedAtMs = System.currentTimeMillis();
            // Reported once per wait, at INFO, with the numbers being compared. A silent wait looks
            // exactly like "the browser agent never harvested", and this is the line that tells the
            // two apart — and says which of the two gate conditions is the one holding.
            long renderedAtMs = iframeNodeId == null
                    ? 0L : SessionReplayWebViewThingy.lastRenderedAtMs(iframeNodeId);
            log.info(LOG_TAG + " WAIT: buffering until the iframe node is in the stream"
                    + " (iframeNodeId=" + iframeNodeId
                    + " iframeRenderedAt=" + renderedAtMs
                    + " registeredAt=" + registeredAtMs
                    + " harvestClearedAt=" + SessionReplay.getLastHarvestClearedAtMs()
                    + " blockedBy=" + (iframeNodeId == null ? "not-registered"
                        : renderedAtMs < registeredAtMs ? "no-snapshot-since-registration"
                        : "no-snapshot-since-harvest") + ")");
        }
        pending.add(event);

        boolean overCap = pending.size() > MAX_PENDING_EVENTS;
        boolean pastDeadline = System.currentTimeMillis() - firstBufferedAtMs > PENDING_DEADLINE_MS;
        if (overCap || pastDeadline) {
            evict(overCap ? "cap" : "deadline");
        }
    }

    /**
     * Bounds the buffer by keeping the newest full snapshot and the newest Meta, and discarding the
     * incrementals.
     *
     * rrweb only emits a full snapshot on its checkout interval, so throwing one away can orphan
     * every mutation that follows it for a long time — whereas an incremental is one frame of
     * change. Keeping the Meta as well costs nothing and preserves the iframe's {@code src}.
     */
    private void evict(String reason) {
        JsonObject newestSnapshot = null;
        JsonObject newestMeta = null;
        for (JsonObject event : pending) {
            int type = WebViewReplayMerger.typeOf(event);
            if (type == WebViewReplayMerger.TYPE_FULL_SNAPSHOT) {
                newestSnapshot = event;
            } else if (type == WebViewReplayMerger.TYPE_META) {
                newestMeta = event;
            }
        }

        int before = pending.size();
        pending.clear();
        if (newestMeta != null) {
            pending.add(newestMeta);
        }
        if (newestSnapshot != null) {
            pending.add(newestSnapshot);
        }
        firstBufferedAtMs = System.currentTimeMillis();

        // Retry against the next native snapshot rather than waiting for the harvest boundary to
        // force one. Without this the gate can stay shut for a whole harvest cycle.
        SessionReplay.setTakeFullSnapshot(true);

        StatsEngine.SUPPORTABILITY.inc(
                MetricNames.SUPPORTABILITY_MOBILE_ANDROID_WEBVIEW_REPLAY_BUFFER_EVICTED);
        log.warn(LOG_TAG + " wait buffer evicted (" + reason + "): dropped "
                + (before - pending.size()) + " incremental event(s), still waiting for the iframe node");
    }

    private void drainPending() {
        if (pending.isEmpty()) {
            return;
        }
        List<JsonObject> flushing = new ArrayList<>(pending);
        // Cleared before handling so a re-entrant buffer() cannot double-process an event.
        pending.clear();
        firstBufferedAtMs = 0L;
        log.debug(LOG_TAG + " flushing " + flushing.size() + " buffered event(s)");
        for (JsonObject event : flushing) {
            handle(event);
        }
    }

    private void handle(JsonObject event) {
        WebViewReplayMerger.Disposition disposition = WebViewReplayMerger.classify(event);
        switch (disposition) {
            case GRAFT: {
                // rrweb re-serializes the whole document on its checkout interval, not only on
                // navigation, and it resets its own mirror when it does. So a second type-2 for the
                // same page describes the same DOM under entirely new web IDs.
                //
                // Two things follow. The previously grafted subtree must be torn down, or the iframe
                // ends up holding two documents and the stream contains two live document roots. And
                // the ID table must be dropped, because none of those web IDs will ever be referenced
                // again — keeping them would grow the table by a full page of entries per checkout for
                // the life of the session.
                if (graftEmitted && childDocumentNativeId != null) {
                    log.debug(LOG_TAG + " re-checkout: removing document " + childDocumentNativeId
                            + " before grafting its replacement");
                    emit(WebViewReplayMerger.buildDocumentRemoval(iframeNodeId, childDocumentNativeId,
                            WebViewReplayMerger.timestampOf(event, System.currentTimeMillis())));
                    childDocumentNativeId = null;
                }
                webToNative = new HashMap<>();

                JsonObject graft = WebViewReplayMerger.buildGraft(
                        iframeNodeId, event, webToNative, ALLOCATOR);
                if (graft == null) {
                    countDrop("malformed-snapshot");
                    return;
                }
                Integer documentId = WebViewReplayMerger.graftedDocumentId(graft);
                if (documentId != null) {
                    childDocumentNativeId = documentId;
                }
                graftEmitted = true;
                emit(graft);
                StatsEngine.SUPPORTABILITY.inc(
                        MetricNames.SUPPORTABILITY_MOBILE_ANDROID_WEBVIEW_REPLAY_GRAFTED);
                // Logged at INFO with the document's size, because "the iframe renders empty" has
                // three separate causes and only this line separates them: no GRAFT line at all
                // means nothing was emitted, a GRAFT line with a handful of nodes means the browser
                // agent handed over an empty document, and a GRAFT line with a real node count means
                // the problem is at replay time rather than here.
                JsonObject graftedNode = graft.getAsJsonObject("data").getAsJsonArray("adds")
                        .get(0).getAsJsonObject().getAsJsonObject("node");
                // Cached so it can be re-attached after each native full snapshot, which is what
                // keeps this document on screen between browser agent checkouts.
                lastGraftedDocumentJson = WebViewReplayMerger.graftedDocumentJson(graft);
                log.info(LOG_TAG + " GRAFT document=" + documentId + " onto iframe=" + iframeNodeId
                        + " nodes=" + WebViewReplayMerger.countNodes(graftedNode)
                        + " chars=" + graft.toString().length()
                        + " mappedIds=" + webToNative.size()
                        + " cached=" + (lastGraftedDocumentJson != null));
                break;
            }
            case REMAP: {
                JsonObject remapped = WebViewReplayMerger.remap(event, webToNative, ALLOCATOR);
                if (remapped == null) {
                    countDrop("malformed-incremental");
                    return;
                }
                emit(remapped);
                StatsEngine.SUPPORTABILITY.inc(
                        MetricNames.SUPPORTABILITY_MOBILE_ANDROID_WEBVIEW_REPLAY_MERGED);
                break;
            }
            case META_HREF: {
                String href = WebViewReplayMerger.metaHref(event);
                if (href == null) {
                    countDrop("meta-without-href");
                    return;
                }
                emit(WebViewReplayMerger.buildSourceUrlAttribute(iframeNodeId, href,
                        WebViewReplayMerger.timestampOf(event, System.currentTimeMillis())));
                break;
            }
            case DROP:
            default:
                countDrop(dropReason(event));
                break;
        }
    }

    /**
     * Names why an event was dropped, so the supportability counters can say whether the
     * type-2/type-3 whitelist is too narrow rather than just how often it fired.
     */
    private static String dropReason(JsonObject event) {
        int type = WebViewReplayMerger.typeOf(event);
        if (type == WebViewReplayMerger.TYPE_INCREMENTAL
                && WebViewReplayMerger.sourceOf(event) == WebViewReplayMerger.SOURCE_VIEWPORT_RESIZE) {
            return "ViewportResize";
        }
        return "Type" + type;
    }

    private static void countDrop(String reason) {
        StatsEngine.SUPPORTABILITY.inc(
                MetricNames.SUPPORTABILITY_MOBILE_ANDROID_WEBVIEW_REPLAY_DROPPED + reason);
    }

    private static void emit(JsonObject event) {
        SessionReplay.recordWebViewReplayEvent(event);
    }
}