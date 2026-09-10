/*
 * Copyright (c) 2026-present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.webView;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.newrelic.agent.android.sessionReplay.models.RRWebEvent;

import java.util.Map;

/**
 * Pulls a WebView's rrweb events out of a browser agent replay payload and wraps each one in the
 * {@code EventType.Plugin} envelope the replay plugin consumes.
 *
 * <h2>Why there is nothing else here</h2>
 * An earlier design grafted the WebView's document into the native rrweb tree, which required
 * remapping every node ID out of the web document's ID space into the native one, plus building
 * graft, document-removal and iframe-attribute mutations. All of that is gone. The plugin hosts a
 * nested {@code Replayer} with its own {@code Mirror} per channel, so a WebView's events cross into
 * the native stream <em>untouched</em> — same IDs, same timestamps, same everything. This class only
 * addresses the envelope.
 *
 * The name is kept from the graft implementation on purpose, so a diff between the two branches
 * lines up file-for-file. Nothing here merges any more.
 *
 * No Android dependencies and no state: every method is a function of its arguments.
 */
public final class WebViewReplayMerger {

    /** The plugin name in {@code data.plugin}; must match the replay plugin's {@code PLUGIN_NAME}. */
    public static final String PLUGIN_NAME = "nr-webview-replay";

    public static final int TYPE_FULL_SNAPSHOT = RRWebEvent.RRWEB_EVENT_FULL_SNAPSHOT;

    private static final String KEY_TIMESTAMP = "timestamp";
    private static final String KEY_TYPE = "type";
    private static final String KEY_DATA = "data";
    private static final String KEY_BODY = "body";

    private WebViewReplayMerger() {
    }

    // ---- payload extraction ------------------------------------------------------------------

    /**
     * Pulls the rrweb event array out of a browser agent replay payload.
     *
     * Tolerates the shapes the payload has been observed to take, because which one arrives depends
     * on the agent build: the events array itself, a payload object with a {@code body} member
     * holding that array, or either of those as JSON <em>text</em>.
     *
     * @param jsonText plain-text JSON from the bridge
     * @return the event array, or null if this text does not contain one
     */
    public static JsonArray extractEvents(String jsonText) {
        if (jsonText == null || jsonText.isEmpty()) {
            return null;
        }
        try {
            return extractEvents(JsonParser.parseString(jsonText));
        } catch (Exception e) {
            return null;
        }
    }

    private static JsonArray extractEvents(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (element.isJsonArray()) {
            return element.getAsJsonArray();
        }
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if (object.has(KEY_BODY)) {
                return extractEvents(object.get(KEY_BODY));
            }
            // `body` is the harvester's convention and the only shape observed on device, but it is
            // the agent's choice rather than ours. Rather than dead-end on a payload that carries the
            // events under some other key, look for the one member that is an array of rrweb-shaped
            // events. Narrow on purpose: top level only, and only an array whose first element is an
            // object with an integer `type`.
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                if (looksLikeEventArray(entry.getValue())) {
                    return entry.getValue().getAsJsonArray();
                }
            }
            return null;
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            // A stringified array or payload. Guarded against unbounded recursion by the fact that
            // parsing a string primitive's contents cannot yield that same string primitive.
            try {
                return extractEvents(JsonParser.parseString(element.getAsString()));
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    private static boolean looksLikeEventArray(JsonElement element) {
        try {
            if (element == null || !element.isJsonArray()) {
                return false;
            }
            JsonArray array = element.getAsJsonArray();
            if (array.size() == 0) {
                return false;
            }
            JsonElement first = array.get(0);
            if (!first.isJsonObject()) {
                return false;
            }
            JsonObject event = first.getAsJsonObject();
            return event.has(KEY_TYPE) && event.get(KEY_TYPE).getAsJsonPrimitive().isNumber();
        } catch (Exception e) {
            return false;
        }
    }

    // ---- envelope ----------------------------------------------------------------------------

    /**
     * Wraps one of a WebView's own rrweb events as a plugin event in the native stream.
     *
     * <p>The inner event is attached <em>by reference and unmodified</em>. That is the whole point of
     * this design: the nested replayer has its own mirror, so the WebView's node IDs never need to
     * mean anything in the native ID space.</p>
     *
     * <p>The envelope's timestamp is the inner event's own, not the time the batch was delivered
     * across the bridge. Both sides stamp from the same system clock in the same units
     * ({@code System.currentTimeMillis()} natively, {@code Date.now()} in the page), so the real
     * timestamp is meaningful — and using delivery time instead would collapse a whole harvest
     * interval's worth of WebView activity onto a single instant in the merged timeline.</p>
     *
     * @param channelId  identifies which WebView this belongs to; matches the mount point's
     *                   {@code data-nr-webview-channel} attribute
     * @param innerEvent the WebView's rrweb event, untouched
     * @param fallbackTimestamp used when the inner event carries no readable timestamp
     */
    public static JsonObject buildPluginEvent(String channelId, JsonObject innerEvent,
                                              long fallbackTimestamp) {
        if (innerEvent == null) {
            return null;
        }

        stripInternalMembers(innerEvent);

        JsonObject payload = new JsonObject();
        payload.addProperty("channelId", channelId);
        payload.add("innerEvent", innerEvent);

        JsonObject data = new JsonObject();
        data.addProperty("plugin", PLUGIN_NAME);
        data.add("payload", payload);

        JsonObject event = new JsonObject();
        event.addProperty(KEY_TYPE, RRWebEvent.RRWEB_EVENT_PLUGIN);
        event.addProperty(KEY_TIMESTAMP, timestampOf(innerEvent, fallbackTimestamp));
        event.add(KEY_DATA, data);
        return event;
    }

    /**
     * Removes the browser agent's internal bookkeeping members from an event before it is forwarded.
     *
     * The one that matters is {@code __serialized}: a <em>string</em> holding the agent's own
     * pre-serialized copy of the entire event, attached to every event it emits. Forwarding it
     * doubles the size of every WebView event — measured at 213,031 bytes on a single document event
     * whose actual node tree was 202,178. It is not part of the rrweb event schema and no replayer
     * reads it (rrweb's bundle contains no reference to the name at all).
     *
     * The graft design never hit this because it extracted {@code data.node} and discarded the rest
     * of the event. Forwarding events whole is what makes the plugin design simple, so the redundant
     * members have to be dropped explicitly instead.
     *
     * <p>Mutates in place rather than copying. The caller owns this tree — it was just parsed from
     * the bridge's payload string and is referenced by nothing else — and a defensive copy of a
     * 200 KB document tree is not worth making to avoid touching it.</p>
     */
    private static void stripInternalMembers(JsonObject innerEvent) {
        // Iterate over a snapshot of the key set: removing through the live view would fail fast.
        for (String key : new java.util.ArrayList<>(innerEvent.keySet())) {
            if (key.startsWith("__")) {
                innerEvent.remove(key);
            }
        }
    }

    /**
     * Rebuilds a document envelope around an already-serialized inner event, under a new envelope
     * timestamp.
     *
     * Used to re-emit a WebView's document once per harvest chunk. Works on the serialized form
     * deliberately: a real page's document runs to hundreds of kilobytes, and re-parsing it into a
     * Gson tree only to walk it again on every harvest costs several times the memory and CPU of
     * keeping the string and concatenating.
     *
     * <p>The <em>inner</em> event keeps its original timestamp — it is a true statement about when
     * that document was captured. Only the envelope is re-stamped, so the harvest sort places the
     * document inside the chunk that needs it rather than ahead of the chunk's own start.</p>
     *
     * @param innerEventJson the serialized inner rrweb event, as cached from a previous envelope
     */
    public static String buildPluginEventJson(String channelId, String innerEventJson, long timestamp) {
        return "{\"" + KEY_TYPE + "\":" + RRWebEvent.RRWEB_EVENT_PLUGIN
                + ",\"" + KEY_TIMESTAMP + "\":" + timestamp
                + ",\"" + KEY_DATA + "\":{\"plugin\":\"" + PLUGIN_NAME + "\""
                + ",\"payload\":{\"channelId\":\"" + channelId + "\""
                + ",\"innerEvent\":" + innerEventJson + "}}}";
    }

    // ---- accessors ---------------------------------------------------------------------------

    /** @return the event's {@code type}, or -1 when absent or unreadable. */
    public static int typeOf(JsonObject event) {
        if (event == null) {
            return -1;
        }
        try {
            JsonElement value = event.get(KEY_TYPE);
            if (value != null && !value.isJsonNull()) {
                return value.getAsInt();
            }
        } catch (Exception e) {
            // Unreadable; fall through.
        }
        return -1;
    }

    /** @return the event's {@code timestamp}, or {@code fallback} when absent or unreadable. */
    public static long timestampOf(JsonObject event, long fallback) {
        if (event == null) {
            return fallback;
        }
        try {
            JsonElement timestamp = event.get(KEY_TIMESTAMP);
            if (timestamp != null && !timestamp.isJsonNull()) {
                long value = timestamp.getAsLong();
                if (value > 0L) {
                    return value;
                }
            }
        } catch (Exception e) {
            // Unreadable; fall through.
        }
        return fallback;
    }

    /**
     * Counts the nodes in a serialized rrweb node tree.
     *
     * Diagnostic. "The WebView renders empty" has several causes — no full snapshot arrived, one
     * arrived carrying an empty document, or it arrived intact and the plugin failed to mount it —
     * and they are indistinguishable without knowing how big the document actually was.
     */
    public static int countNodes(JsonElement node) {
        if (node == null || !node.isJsonObject()) {
            return 0;
        }
        int count = 1;
        JsonElement children = node.getAsJsonObject().get("childNodes");
        if (children != null && children.isJsonArray()) {
            for (JsonElement child : children.getAsJsonArray()) {
                count += countNodes(child);
            }
        }
        return count;
    }

    /** @return the {@code data.node} of a type-2 full snapshot, or null. Diagnostics. */
    public static JsonElement snapshotNode(JsonObject fullSnapshot) {
        try {
            return fullSnapshot.getAsJsonObject(KEY_DATA).get("node");
        } catch (Exception e) {
            return null;
        }
    }
}
