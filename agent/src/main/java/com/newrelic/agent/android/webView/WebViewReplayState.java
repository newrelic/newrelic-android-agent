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
import com.newrelic.agent.android.sessionReplay.viewMapper.ViewDetails;
import com.newrelic.agent.android.stats.StatsEngine;
import com.newrelic.agent.android.util.NamedThreadFactory;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Channel state for one WebView: its channel ID, and the wrapping of its rrweb events into the
 * native stream.
 *
 * Owned by that WebView's {@code WebViewJSInterface}. Because the bridge is already per-WebView
 * there is no registry and no keying problem — the instance <em>is</em> the key, so concurrent
 * WebViews are isolated for free.
 *
 * <h2>What used to be here</h2>
 * Under the graft design this class also carried a web-to-native ID mapping, a gate that withheld
 * events until the iframe node they referenced had been written into the current chunk, a bounded
 * wait buffer with eviction, a cached copy of the last grafted document, and a broadcast that
 * re-attached that document after every native full snapshot. The replay plugin makes all of it
 * unnecessary:
 *
 * <ul>
 *   <li>no ID remap — the nested {@code Replayer} has its own {@code Mirror}</li>
 *   <li>no gate — the plugin accumulates events per channel and replays them once its mount point is
 *       built, so a plugin event may precede the snapshot that carries the mount point</li>
 *   <li>no per-snapshot re-attach — the plugin's {@code onBuild} fires again on rebuild and
 *       rehydrates the nested replayer from its retained history</li>
 * </ul>
 *
 * One piece of re-emission does survive, and it is a property of the <em>payload</em> rather than of
 * the player: harvest chunks are uploaded and replayed independently, so the plugin's retained
 * history cannot cross a chunk boundary. A chunk containing a WebView's mutations but not its
 * document has nothing to mount them onto and renders blank. So the document is re-emitted
 * <strong>once per chunk</strong> — against once per native full snapshot under the graft design,
 * which put up to four copies of the same document in a single chunk (measured 3.5 MB).
 *
 * The class name is kept so a diff against the graft branch lines up file-for-file.
 *
 * Threading: {@link #onNavigationStarted()} and {@link #onRegistered()} run on the UI thread, from
 * the page lifecycle callbacks. {@link #processEvent} runs on the merge executor. Every public
 * method is synchronized on this instance.
 */
public class WebViewReplayState {

    private static final AgentLog log = AgentLogManager.getAgentLog();
    private static final String LOG_TAG = "[NR-WV-SR]";

    /**
     * Where the wrapping runs. Off the WebView's JS thread: a harvest batch has been measured at
     * nearly 2 MB of JSON, and parsing that on the JS thread would stall the page. Single-threaded so
     * events are processed in arrival order, and shared across every WebView so concurrent ones have
     * a total order too.
     */
    private static final ExecutorService MERGE_EXECUTOR = Executors.newSingleThreadExecutor(
            new NamedThreadFactory("NRWebViewReplayMerge"));

    /**
     * Every live per-WebView channel, so a native full snapshot can be broadcast to all of them.
     *
     * Weak keys: a channel is reachable only from its bridge, which is reachable only from its
     * WebView, so an entry clears itself once that WebView is collected. This is the one thing the
     * per-WebView-bridge design cannot supply on its own — the bridge answers "which WebView is this
     * event for", and a broadcast needs the opposite direction.
     */
    private static final Set<WebViewReplayState> LIVE_STATES =
            Collections.newSetFromMap(new WeakHashMap<>());

    /**
     * Re-emits each WebView's document if the chunk it would land in does not have one yet.
     *
     * Hung off the native full-snapshot notification because a harvest forces a full snapshot, so the
     * first snapshot after each harvest boundary is a reliable "new chunk has started" edge. The
     * decision of whether to actually write anything is {@link #reemitDocumentForNewChunk}'s, and it
     * is gated on the harvest clock rather than on this notification — otherwise a busy screen, which
     * produces a snapshot roughly every second, would write the document dozens of times per chunk.
     */
    public static void onNativeFullSnapshot(long timestampMs) {
        List<WebViewReplayState> states;
        synchronized (WebViewReplayState.class) {
            states = new ArrayList<>(LIVE_STATES);
        }
        for (WebViewReplayState state : states) {
            post(() -> state.reemitDocumentForNewChunk(timestampMs));
        }
    }

    /** Posts work onto the merge thread. */
    public static void post(Runnable task) {
        try {
            MERGE_EXECUTOR.execute(task);
        } catch (Throwable t) {
            log.error(LOG_TAG + " could not schedule merge work", t);
        }
    }

    private final WeakReference<WebView> webViewRef;

    /**
     * Identifies this WebView's stream. Resolved exactly once, on the UI thread, because it comes
     * from a tag on the View and view tags are not safe to touch off it — and bridge calls arrive on
     * the WebView's JS thread. Null until {@link #onRegistered()} has run; events arriving before
     * that are dropped, since without a channel ID there is nothing to address them to.
     */
    private String channelId;

    /** Whether this generation of the document has already asked for a native full snapshot. */
    private boolean snapshotRequested;

    /**
     * The most recent document event's serialized <em>inner</em> event, kept so it can be re-emitted
     * into each new chunk. Held as a string rather than a Gson tree because a real page's document is
     * hundreds of kilobytes and the tree form costs several times as much memory.
     */
    private String lastDocumentInnerJson;

    /** When a document was last written, against which the harvest clock is compared. */
    private long lastDocumentWrittenAtMs;

    public WebViewReplayState(WebView webView) {
        this.webViewRef = new WeakReference<>(webView);
        synchronized (WebViewReplayState.class) {
            LIVE_STATES.add(this);
        }
        // Registered lazily on first construction: nothing needs re-emitting until a WebView exists,
        // and this avoids the session replay package depending on this one.
        SessionReplay.setFullSnapshotListener(WebViewReplayState::onNativeFullSnapshot);
    }

    /**
     * Writes this WebView's document into a chunk that does not have one yet. Merge thread.
     *
     * The gate is "has a harvest emptied the working buffer since the last time I wrote a document",
     * which is exactly once per chunk: a harvest clears the buffer and forces a full snapshot, and
     * that snapshot brings us here.
     */
    private synchronized void reemitDocumentForNewChunk(long timestampMs) {
        if (lastDocumentInnerJson == null || channelId == null) {
            return;                 // nothing captured for this WebView yet
        }
        if (webViewRef.get() == null) {
            lastDocumentInnerJson = null;       // the WebView is gone; stop holding its DOM
            return;
        }
        if (SessionReplay.getLastHarvestClearedAtMs() <= lastDocumentWrittenAtMs) {
            return;                 // this chunk already carries the document
        }

        SessionReplay.recordWebViewReplayEvent(WebViewReplayMerger.buildPluginEventJson(
                channelId, lastDocumentInnerJson, timestampMs));
        lastDocumentWrittenAtMs = System.currentTimeMillis();
        StatsEngine.SUPPORTABILITY.inc(
                MetricNames.SUPPORTABILITY_MOBILE_ANDROID_WEBVIEW_REPLAY_REGRAFTED);
        log.debug(LOG_TAG + " re-emitted the document for channel " + channelId
                + " into a new chunk at " + timestampMs);
    }

    /**
     * Navigation.
     *
     * Nothing to reset. The channel ID is stable across navigations by design — it comes from a tag
     * on the View — and the plugin rebuilds its nested replayer when the newly loaded page's own
     * rrweb emits its next full snapshot. Under the graft design this method had to clear an ID
     * mapping, discard buffered events and emit a document-removal mutation.
     *
     * <p>UI thread.</p>
     */
    public synchronized void onNavigationStarted() {
        log.debug(LOG_TAG + " navigation on channel " + channelId
                + "; the plugin will rebuild from the next full snapshot");
    }

    /**
     * Page load finished: resolve the channel ID and ask for one native full snapshot, so the mount
     * point reaches the stream promptly rather than waiting for the next harvest boundary to force
     * one.
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
        channelId = String.valueOf(ViewDetails.getStableId(webView));

        // onPageFinished is not once-per-page — redirects, consent interstitials and SPA route
        // changes fire it repeatedly, measured at 13 times in 65 seconds on a real page. Requesting a
        // snapshot on each one is pure waste. It is no longer *destructive* the way it was under the
        // graft design, where a full snapshot reset the replayer's mirror and destroyed the grafted
        // document, but there is still no reason to do it more than once.
        if (!snapshotRequested) {
            snapshotRequested = true;
            SessionReplay.setTakeFullSnapshot(true);
            log.debug(LOG_TAG + " registered: channelId=" + channelId);
        } else {
            log.debug(LOG_TAG + " re-registration ignored; channel " + channelId
                    + " already requested its native snapshot");
        }
    }

    /**
     * Wraps one event from the WebView's rrweb stream and writes it into the native stream.
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
        if (channelId == null) {
            // onPageFinished has not run yet, so there is no channel to address this to. The mount
            // point would not be in the stream either, since its node ID is allocated by the same
            // call. Counted rather than buffered: the browser agent cannot harvest before the page
            // has loaded, so this should not happen, and a metric will say if it does.
            countDrop("no-channel");
            log.debug(LOG_TAG + " dropping an event; the channel is not registered yet");
            return;
        }

        JsonObject wrapped = WebViewReplayMerger.buildPluginEvent(
                channelId, event, System.currentTimeMillis());
        if (wrapped == null) {
            countDrop("unwrappable");
            return;
        }

        int type = WebViewReplayMerger.typeOf(event);
        if (type == WebViewReplayMerger.TYPE_FULL_SNAPSHOT) {
            // Cached so it can be re-emitted into later chunks, which is what keeps the WebView on
            // screen between browser agent checkouts. Only the inner event is kept: the envelope is
            // rebuilt per chunk so it can carry that chunk's timestamp.
            lastDocumentInnerJson = event.toString();
            lastDocumentWrittenAtMs = System.currentTimeMillis();
            // Logged at INFO with the document's size, because "the WebView renders empty" has
            // several causes and only this line separates them: no line at all means the browser
            // agent never checked out, a line with a handful of nodes means it handed over an empty
            // document, and a line with a real node count means the problem is at replay time.
            log.info(LOG_TAG + " DOCUMENT channel=" + channelId
                    + " nodes=" + WebViewReplayMerger.countNodes(
                            WebViewReplayMerger.snapshotNode(event))
                    + " chars=" + wrapped.toString().length());
            StatsEngine.SUPPORTABILITY.inc(
                    MetricNames.SUPPORTABILITY_MOBILE_ANDROID_WEBVIEW_REPLAY_GRAFTED);
        } else {
            StatsEngine.SUPPORTABILITY.inc(
                    MetricNames.SUPPORTABILITY_MOBILE_ANDROID_WEBVIEW_REPLAY_MERGED);
        }

        SessionReplay.recordWebViewReplayEvent(wrapped);
    }

    private static void countDrop(String reason) {
        StatsEngine.SUPPORTABILITY.inc(
                MetricNames.SUPPORTABILITY_MOBILE_ANDROID_WEBVIEW_REPLAY_DROPPED + reason);
    }
}
