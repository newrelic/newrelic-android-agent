/*
 * Copyright (c) 2026-present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.webView;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Pure transformations that turn a WebView's own rrweb event stream into events that belong to the
 * native replay stream: node IDs remapped out of the web document's ID space into the native one,
 * and the child document grafted onto the {@code <iframe>} node that represents the WebView.
 *
 * This is a deliberate reimplementation of what rrweb already does for cross-origin iframes —
 * {@code CrossOriginIframeMirror} remaps the child frame's IDs into the parent's space, and the
 * child snapshot is attached to the iframe node through a mutation {@code adds} entry — with an
 * Android {@code WebView} standing in for the iframe.
 *
 * No Android dependencies and no state: every method is a function of its arguments. The bridge
 * hands over uncompressed JSON text, so {@link #extractEvents(String)} is the whole entry path;
 * there is no decode step on either side of the bridge.
 */
public final class WebViewReplayMerger {

    public static final int TYPE_FULL_SNAPSHOT = 2;
    public static final int TYPE_INCREMENTAL = 3;
    public static final int TYPE_META = 4;

    public static final int SOURCE_MUTATION = 0;

    /**
     * {@code IncrementalSource.ViewportResize}. Carries no node IDs, so the ID walker has nothing to
     * do with it — but it carries {@code width}/{@code height}, and a replayer applies those to the
     * <em>main</em> viewport. Letting a child's resize through would rescale the entire replay to the
     * WebView's dimensions: the same class of bug as emitting the child's Meta event, arriving
     * through a different door.
     */
    public static final int SOURCE_VIEWPORT_RESIZE = 4;

    /**
     * Every rrweb field that holds a node ID, across every incremental source. Enumerating the
     * per-source field layout exhaustively is a maintenance trap, so the walker is generic over
     * these key names instead.
     */
    private static final Set<String> ID_KEYS = new HashSet<>(Arrays.asList(
            "id", "parentId", "nextId", "previousId", "rootId", "start", "end"));

    private static final String KEY_ATTRIBUTES = "attributes";
    private static final String KEY_STYLE_IDS = "styleIds";
    private static final String KEY_TIMESTAMP = "timestamp";
    private static final String KEY_TYPE = "type";
    private static final String KEY_DATA = "data";
    private static final String KEY_SOURCE = "source";
    private static final String KEY_NODE = "node";
    private static final String KEY_HREF = "href";
    private static final String KEY_BODY = "body";

    /** What the merge path should do with an event. */
    public enum Disposition {
        /** Type 2: remap the child document and graft it onto the iframe node. */
        GRAFT,
        /** Type 3: remap node IDs and pass through. */
        REMAP,
        /** Type 4: consume for its href, never emit — a top-level Meta resizes the whole player. */
        META_HREF,
        /** Everything else, plus ViewportResize. Dropped and counted. */
        DROP
    }

    /** Allocates a fresh node ID in the native ID space. */
    public interface IdAllocator {
        int allocate();
    }

    private WebViewReplayMerger() {
    }

    // ---- payload extraction ------------------------------------------------------------------

    /**
     * Pulls the rrweb event array out of a browser agent replay payload.
     *
     * Tolerates the three shapes the payload has been observed to take, because which one arrives
     * depends on the agent build and on whether the body was compressed: the events array itself,
     * a payload object with a {@code body} member holding that array, or either of those as JSON
     * <em>text</em> (the harvester stringifies the body before compressing it).
     *
     * @param jsonText decoded, plain-text JSON
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

    /**
     * Counts the nodes in a serialized rrweb node tree.
     *
     * Diagnostic. "The iframe is empty" has three very different causes — no graft was emitted, a
     * graft was emitted carrying an empty document, or a graft with real content did not attach at
     * replay time — and they are indistinguishable without knowing how big the document being grafted
     * actually was.
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

    // ---- classification ---------------------------------------------------------------------

    public static Disposition classify(JsonObject event) {
        switch (typeOf(event)) {
            case TYPE_FULL_SNAPSHOT:
                return Disposition.GRAFT;
            case TYPE_INCREMENTAL:
                return sourceOf(event) == SOURCE_VIEWPORT_RESIZE ? Disposition.DROP : Disposition.REMAP;
            case TYPE_META:
                return Disposition.META_HREF;
            default:
                return Disposition.DROP;
        }
    }

    /** @return the event's {@code type}, or -1 when absent or unreadable. */
    public static int typeOf(JsonObject event) {
        return readInt(event, KEY_TYPE, -1);
    }

    /** @return the incremental event's {@code data.source}, or -1 when absent or unreadable. */
    public static int sourceOf(JsonObject event) {
        if (event == null) {
            return -1;
        }
        try {
            JsonElement data = event.get(KEY_DATA);
            if (data != null && data.isJsonObject()) {
                return readInt(data.getAsJsonObject(), KEY_SOURCE, -1);
            }
        } catch (Exception e) {
            // Unreadable; treated as "no source".
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

    /** @return a type-4 Meta event's {@code data.href}, or null. */
    public static String metaHref(JsonObject event) {
        if (event == null) {
            return null;
        }
        try {
            JsonElement data = event.get(KEY_DATA);
            if (data != null && data.isJsonObject()) {
                JsonElement href = data.getAsJsonObject().get(KEY_HREF);
                if (href != null && href.isJsonPrimitive() && href.getAsString() != null
                        && !href.getAsString().isEmpty()) {
                    return href.getAsString();
                }
            }
        } catch (Exception e) {
            // Unreadable; no href.
        }
        return null;
    }

    // ---- ID remapping ------------------------------------------------------------------------

    /**
     * Returns a copy of {@code event} with every node ID translated from the web document's ID space
     * into the native one, allocating and recording a mapping the first time each web ID is seen.
     *
     * A web ID first seen in a reference position — a {@code parentId} for a node that was never
     * snapshotted, say — still gets an allocation. It simply will not resolve in the player, which
     * is the correct failure mode and no worse than dropping the event.
     *
     * @param event   an rrweb event; not modified
     * @param mapping the current generation's web-ID to native-ID table; extended in place
     */
    public static JsonObject remap(JsonObject event, Map<Integer, Integer> mapping, IdAllocator allocator) {
        if (event == null) {
            return null;
        }
        return remapElement(event, mapping, allocator).getAsJsonObject();
    }

    private static JsonElement remapElement(JsonElement element, Map<Integer, Integer> mapping,
                                            IdAllocator allocator) {
        if (element == null || element.isJsonNull()) {
            return JsonNull.INSTANCE;
        }

        if (element.isJsonArray()) {
            JsonArray out = new JsonArray();
            for (JsonElement child : element.getAsJsonArray()) {
                out.add(remapElement(child, mapping, allocator));
            }
            return out;
        }

        if (element.isJsonObject()) {
            JsonObject out = new JsonObject();
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                String key = entry.getKey();
                JsonElement value = entry.getValue();

                if (KEY_ATTRIBUTES.equals(key) && value != null && value.isJsonObject()) {
                    // LOAD-BEARING EXCLUSION. An element node's `attributes` is a map of HTML
                    // attributes, so its `id` is a *string* CSS id, not a node ID. Descending into
                    // it would silently rewrite page markup — no exception, no wrong-looking data,
                    // just an HTML id turned into an integer.
                    //
                    // Note the deliberate asymmetry with the array case below: a MutationData
                    // `attributes` is an ARRAY of {id, attributes:{...}} records whose own `id` IS a
                    // node ID and must be remapped. So only an *object* under this key is opaque;
                    // an array is walked, and the inner attribute maps it contains then hit this
                    // same branch and are left alone.
                    out.add(key, value.deepCopy());
                } else if (KEY_STYLE_IDS.equals(key) && value != null && value.isJsonArray()) {
                    // AdoptedStyleSheet (source 15). These are stylesheet IDs from a mirror that is
                    // separate from the node mirror, so routing them through the same table can in
                    // principle alias a stylesheet ID with an equal-valued node ID. Accepted:
                    // source 15 is rare, and the spec calls for remapping them.
                    JsonArray ids = new JsonArray();
                    for (JsonElement id : value.getAsJsonArray()) {
                        ids.add(mapId(id, mapping, allocator));
                    }
                    out.add(key, ids);
                } else if (ID_KEYS.contains(key)) {
                    out.add(key, mapId(value, mapping, allocator));
                } else {
                    out.add(key, remapElement(value, mapping, allocator));
                }
            }
            return out;
        }

        return element.deepCopy();
    }

    private static JsonElement mapId(JsonElement value, Map<Integer, Integer> mapping, IdAllocator allocator) {
        if (value == null || value.isJsonNull()) {
            return JsonNull.INSTANCE;
        }
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            // `nextId` is legitimately null, and a non-numeric value under an ID key is not an ID.
            return value.deepCopy();
        }

        double raw;
        try {
            raw = value.getAsDouble();
        } catch (Exception e) {
            return value.deepCopy();
        }

        int webId = (int) raw;
        if (raw != (double) webId) {
            return value.deepCopy();    // fractional: not a node ID
        }

        Integer nativeId = mapping.get(webId);
        if (nativeId == null) {
            nativeId = allocator.allocate();
            mapping.put(webId, nativeId);
        }
        return new JsonPrimitive(nativeId);
    }

    // ---- event construction ------------------------------------------------------------------

    /**
     * Wraps a WebView's type-2 FullSnapshot as a single-add mutation that attaches its (remapped)
     * document node to the iframe representing that WebView.
     *
     * The timestamp is the child snapshot's own, unmodified: native events are stamped with
     * {@code System.currentTimeMillis()} and rrweb inside the WebView with {@code Date.now()}, which
     * is the same epoch in the same units off the same system clock. There is no offset to solve.
     *
     * @return the graft mutation, or null if the snapshot carries no {@code data.node}
     */
    public static JsonObject buildGraft(int iframeNodeId, JsonObject fullSnapshot,
                                        Map<Integer, Integer> mapping, IdAllocator allocator) {
        if (fullSnapshot == null) {
            return null;
        }

        JsonElement documentNode;
        try {
            JsonElement data = fullSnapshot.get(KEY_DATA);
            if (data == null || !data.isJsonObject()) {
                return null;
            }
            documentNode = data.getAsJsonObject().get(KEY_NODE);
        } catch (Exception e) {
            return null;
        }
        if (documentNode == null || !documentNode.isJsonObject()) {
            return null;
        }

        JsonElement remappedNode = remapElement(documentNode, mapping, allocator);

        JsonObject add = new JsonObject();
        add.addProperty("parentId", iframeNodeId);
        add.add("nextId", JsonNull.INSTANCE);
        add.add(KEY_NODE, remappedNode);

        JsonArray adds = new JsonArray();
        adds.add(add);

        JsonObject data = emptyMutationData();
        data.add("adds", adds);

        return incrementalEvent(timestampOf(fullSnapshot, System.currentTimeMillis()), data);
    }

    /**
     * Builds a graft event by splicing an already-serialized, already-remapped document into a
     * mutation envelope.
     *
     * Used to re-attach a cached document after a native full snapshot reset the replayer's mirror.
     * Works on the serialized form deliberately: a page's document runs to megabytes, and holding it
     * as a Gson tree — then walking that tree to re-serialize it on every native full snapshot —
     * costs several times the memory and CPU of keeping the string and concatenating.
     *
     * @param documentJson the serialized {@code type: 0} document node, remapped into native IDs
     */
    public static String buildGraftEventJson(int iframeNodeId, String documentJson, long timestamp) {
        return "{\"" + KEY_TYPE + "\":" + TYPE_INCREMENTAL
                + ",\"" + KEY_TIMESTAMP + "\":" + timestamp
                + ",\"" + KEY_DATA + "\":{\"" + KEY_SOURCE + "\":" + SOURCE_MUTATION
                + ",\"texts\":[],\"" + KEY_ATTRIBUTES + "\":[],\"removes\":[]"
                + ",\"adds\":[{\"parentId\":" + iframeNodeId
                + ",\"nextId\":null,\"" + KEY_NODE + "\":" + documentJson + "}]}}";
    }

    /**
     * @return the serialized document node from a {@link #buildGraft} result, ready to be cached for
     * later re-attachment, or null.
     */
    public static String graftedDocumentJson(JsonObject graft) {
        try {
            return graft.getAsJsonObject(KEY_DATA).getAsJsonArray("adds").get(0)
                    .getAsJsonObject().getAsJsonObject(KEY_NODE).toString();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * @return the native node ID of the document root a {@link #buildGraft} result attaches, or null.
     * Needed so the subtree can be removed again on navigation.
     */
    public static Integer graftedDocumentId(JsonObject graft) {
        try {
            JsonObject node = graft.getAsJsonObject(KEY_DATA)
                    .getAsJsonArray("adds").get(0).getAsJsonObject()
                    .getAsJsonObject(KEY_NODE);
            if (node.has("id") && !node.get("id").isJsonNull()) {
                return node.get("id").getAsInt();
            }
        } catch (Exception e) {
            // Shape is ours, so this should not happen; null just means "cannot remove later".
        }
        return null;
    }

    /**
     * Records the child document's URL on the iframe. This is what the child's type-4 Meta event
     * becomes: its {@code href} is worth keeping, but emitting the Meta itself would resize the whole
     * player viewport to the WebView's dimensions.
     *
     * Written as {@code data-nr-src}, never {@code src}. rrweb strips {@code src} from serialized
     * iframes on purpose, because the replayer builds the recorded document into the iframe's
     * {@code about:blank} document — a live {@code src} navigates the iframe away, leaves
     * {@code contentDocument} null, and makes {@code attachDocumentToIframe} throw, which discards
     * the graft and leaves an empty head and body.
     */
    public static JsonObject buildSourceUrlAttribute(int iframeNodeId, String href, long timestamp) {
        JsonObject attributes = new JsonObject();
        attributes.addProperty("data-nr-src", href);

        JsonObject record = new JsonObject();
        record.addProperty("id", iframeNodeId);
        record.add(KEY_ATTRIBUTES, attributes);

        JsonArray records = new JsonArray();
        records.add(record);

        JsonObject data = emptyMutationData();
        data.add(KEY_ATTRIBUTES, records);

        return incrementalEvent(timestamp, data);
    }

    /**
     * Tears the previous document's subtree off the iframe. Emitted on navigation, before the next
     * graft lands, so two generations of a page never coexist under one iframe.
     */
    public static JsonObject buildDocumentRemoval(int iframeNodeId, int childDocumentNativeId, long timestamp) {
        JsonObject record = new JsonObject();
        record.addProperty("parentId", iframeNodeId);
        record.addProperty("id", childDocumentNativeId);

        JsonArray records = new JsonArray();
        records.add(record);

        JsonObject data = emptyMutationData();
        data.add("removes", records);

        return incrementalEvent(timestamp, data);
    }

    /**
     * A mutation payload with every list present and empty. Replayers index into all four, so
     * omitting one is not equivalent to sending it empty.
     */
    private static JsonObject emptyMutationData() {
        JsonObject data = new JsonObject();
        data.addProperty(KEY_SOURCE, SOURCE_MUTATION);
        data.add("texts", new JsonArray());
        data.add(KEY_ATTRIBUTES, new JsonArray());
        data.add("removes", new JsonArray());
        data.add("adds", new JsonArray());
        return data;
    }

    private static JsonObject incrementalEvent(long timestamp, JsonObject data) {
        JsonObject event = new JsonObject();
        event.addProperty(KEY_TYPE, TYPE_INCREMENTAL);
        event.addProperty(KEY_TIMESTAMP, timestamp);
        event.add(KEY_DATA, data);
        return event;
    }

    private static int readInt(JsonObject object, String key, int fallback) {
        if (object == null) {
            return fallback;
        }
        try {
            JsonElement value = object.get(key);
            if (value != null && !value.isJsonNull()) {
                return value.getAsInt();
            }
        } catch (Exception e) {
            // Unreadable; fall through.
        }
        return fallback;
    }
}