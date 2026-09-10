package com.newrelic.agent.android.sessionReplay.models;

import com.google.gson.annotations.JsonAdapter;

import java.util.HashMap;
import java.util.Map;

@JsonAdapter(AttributesSerializer.class)
public class Attributes{
    public String id;
    public String type;
    public String inputType;
    public String value;
    public Boolean checked;
    public String dataNrMasked;
    public String dataNrType;
    public String min;
    public String max;
    public String step;
    /**
     * The URL loaded inside an {@code <iframe>} element node, for diagnostics only.
     *
     * Deliberately NOT {@code src}. rrweb strips {@code src} from every iframe it serializes
     * ("prevent auto loading" — {@code rrweb-snapshot}'s {@code serializeElementNode}), because the
     * replayer renders the <em>recorded</em> document into the iframe's {@code about:blank}
     * {@code contentDocument} instead. A real {@code src} makes the replayed iframe navigate to the
     * live URL, which inside the replayer's sandboxed frame leaves {@code contentDocument} null —
     * and {@code Replayer.attachDocumentToIframe} dereferences it without a null guard, so the
     * grafted document is silently discarded and the iframe shows an empty head and body.
     *
     * A {@code data-*} attribute is inert: it records the URL without any loading behavior.
     *
     * Also a first-class field rather than a {@link #metadata} entry, because
     * {@link AttributesSerializer} folds every metadata key other than {@code "style"} into a nested
     * {@code style} object.
     */
    public String dataNrSrc;
    public Map<String, String> metadata = new HashMap<>();

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }

    public Attributes(String id) {
        this.id = id;
    }
    public String getId() {
        return id;
    }
    public void setId(String id) {
        this.id = id;
    }
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
