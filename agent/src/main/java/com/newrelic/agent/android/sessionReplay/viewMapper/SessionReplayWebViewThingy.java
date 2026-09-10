/*
 * Copyright (c) 2026-present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.sessionReplay.viewMapper;

import android.webkit.WebView;

import com.newrelic.agent.android.sessionReplay.models.Attributes;
import com.newrelic.agent.android.sessionReplay.models.RRWebElementNode;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maps an Android {@link WebView} to an rrweb {@code <iframe>} element node instead of the default
 * {@code <div>} that {@link SessionReplayViewThingy} would produce.
 *
 * The iframe is empty in the native snapshot. The WebView's DOM arrives separately, siphoned from
 * the injected browser agent's {@code beforeHarvest} hook, and is grafted onto this node as a
 * single-add mutation carrying a remapped child document — the same mechanism rrweb itself uses
 * for cross-origin iframes.
 *
 * Position and size are inherited from {@link SessionReplayViewThingy}'s CSS generation, so they
 * come from the native view's measured frame. The child document's own viewport dimensions are
 * deliberately unused: a top-level Meta event would resize the entire replay player.
 */
public class SessionReplayWebViewThingy extends SessionReplayViewThingy {

    /**
     * When each iframe node ID was last serialized into the replay stream.
     *
     * This is the graft-ordering gate. A graft's {@code parentId} must already exist in the stream
     * before the mutation that references it, so the merge path buffers WebView events until the
     * iframe node has actually been written. Stamping it here — the one place the node is produced,
     * on both the full-snapshot and incremental-add paths — makes that a proof rather than a proxy:
     * a WebView that is registered but off-screen, or not yet laid out, never reaches this method
     * and correctly keeps the gate shut.
     *
     * Bounded by the number of distinct WebView node IDs seen in a process, which is small.
     */
    private static final Map<Integer, Long> renderStamps = new ConcurrentHashMap<>();

    /**
     * The URL loaded at capture time, or null. Read here rather than at graft time because capture
     * runs on the UI thread (see {@code Debouncer}'s main-looper handler) and
     * {@link WebView#getUrl()} is a UI-thread-only call.
     */
    private final String url;

    public SessionReplayWebViewThingy(ViewDetails viewDetails, WebView webView) {
        super(viewDetails);
        String resolved = null;
        try {
            resolved = webView.getUrl();
        } catch (Throwable t) {
            // A destroyed or cross-process-gone WebView can throw here. An iframe with no src is
            // still a valid graft target, so this is not worth failing the whole frame over.
        }
        this.url = resolved;
    }

    /**
     * A WebView's content is rendered by the web engine, not by Android child views. Descending
     * into its (implementation-defined, API-level-dependent) internal view hierarchy would emit
     * nodes that correspond to nothing the user sees, inside a node whose children are about to be
     * replaced by the grafted document.
     */
    @Override
    public boolean shouldRecordSubviews() {
        return false;
    }

    @Override
    public RRWebElementNode generateRRWebNode() {
        Attributes attributes = new Attributes(viewDetails.getCSSSelector());
        if (url != null && !url.isEmpty()) {
            // Recorded as an inert data-* attribute, never as `src`. A real `src` makes the replayed
            // iframe navigate to the live URL, which destroys the contentDocument the grafted child
            // document has to be built into. See Attributes#dataNrSrc.
            attributes.dataNrSrc = url;
        }
        renderStamps.put(viewDetails.viewId, System.currentTimeMillis());
        return new RRWebElementNode(attributes, RRWebElementNode.TAG_TYPE_IFRAME,
                viewDetails.viewId, new ArrayList<>());
    }

    /**
     * @return the wall-clock ms at which {@code nodeId} was last serialized into the replay stream,
     * or 0 if it never has been.
     */
    public static long lastRenderedAtMs(int nodeId) {
        Long stamp = renderStamps.get(nodeId);
        return stamp == null ? 0L : stamp;
    }
}