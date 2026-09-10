/*
 * Copyright (c) 2026-present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.sessionReplay.viewMapper;

import android.webkit.WebView;

import com.newrelic.agent.android.sessionReplay.models.Attributes;
import com.newrelic.agent.android.sessionReplay.models.RRWebElementNode;

import java.util.ArrayList;

/**
 * Maps an Android {@link WebView} to an rrweb {@code <div>} carrying a
 * {@code data-nr-webview-channel} attribute: the mount point for that WebView's own replay stream.
 *
 * The WebView's DOM never enters this node. It travels as a separate stream of
 * {@code EventType.Plugin} events, and the replay plugin hosts a nested {@code Replayer} — with its
 * own {@code Mirror} and its own ID space — rooted at this node. So the two streams share exactly
 * one thing: the channel ID in this attribute.
 *
 * Deliberately a {@code div} and not an {@code iframe}. The plugin mounts by constructing
 * {@code new Replayer([], {root: node})}, which appends the replayer's own wrapper element (itself
 * containing an iframe) as a child of {@code node} — and children of an {@code <iframe>} element are
 * ignored by HTML, so an iframe here would render nothing. The previous graft-based design needed
 * an iframe precisely because it went the other way, building the child document into the iframe's
 * own {@code contentDocument}.
 *
 * Position and size are inherited from {@link SessionReplayViewThingy}'s CSS generation, so they
 * come from the native view's measured frame.
 */
public class SessionReplayWebViewThingy extends SessionReplayViewThingy {

    /**
     * The URL loaded at capture time, or null. Read here rather than later because capture runs on
     * the UI thread (see {@code Debouncer}'s main-looper handler) and {@link WebView#getUrl()} is a
     * UI-thread-only call. Diagnostics only — nothing depends on it.
     */
    private final String url;

    public SessionReplayWebViewThingy(ViewDetails viewDetails, WebView webView) {
        super(viewDetails);
        String resolved = null;
        try {
            resolved = webView.getUrl();
        } catch (Throwable t) {
            // A destroyed or cross-process-gone WebView can throw here, and the mount point is
            // still valid without a URL. Not worth failing the whole frame over.
        }
        this.url = resolved;
    }

    /**
     * A WebView's content is rendered by the web engine, not by Android child views. Descending into
     * its (implementation-defined, API-level-dependent) internal view hierarchy would emit nodes
     * that correspond to nothing the user sees, inside a node the plugin is about to mount into.
     */
    @Override
    public boolean shouldRecordSubviews() {
        return false;
    }

    @Override
    public RRWebElementNode generateRRWebNode() {
        Attributes attributes = new Attributes(viewDetails.getCSSSelector());

        // The channel ID is this WebView's stable node ID, which comes from a tag on the View and so
        // survives navigation. That matters: the plugin keys its nested replayer and retained
        // history by channel, and a channel ID that changed on navigation would orphan both.
        attributes.dataNrWebviewChannel = String.valueOf(viewDetails.viewId);

        if (url != null && !url.isEmpty()) {
            // Inert data-* attribute, for diagnostics when reading a raw payload. Never `src`.
            attributes.dataNrSrc = url;
        }

        return new RRWebElementNode(attributes, RRWebElementNode.TAG_TYPE_DIV,
                viewDetails.viewId, new ArrayList<>());
    }
}
