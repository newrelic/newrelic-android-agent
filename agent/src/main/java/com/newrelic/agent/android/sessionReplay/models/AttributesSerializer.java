package com.newrelic.agent.android.sessionReplay.models;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;
import java.util.Map;

/**
 * Custom serializer for Attributes class that outputs the metadata map directly as key-value pairs
 * in the JSON without the "attributes" wrapper.
 */
public class AttributesSerializer implements JsonSerializer<Attributes> {
    
    @Override
    public JsonElement serialize(Attributes src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject jsonObject = new JsonObject();
        JsonObject metadataObject = new JsonObject();

        // Add all metadata entries directly to the root JSON object
        for (Map.Entry<String, String> entry : src.getMetadata().entrySet()) {
            if ("style".equals(entry.getKey())) {
                // Handle style separately - add it as a nested object
                jsonObject.addProperty(entry.getKey(), entry.getValue());
            } else {
                // All other metadata entries become inline style properties
                metadataObject.addProperty(entry.getKey(), entry.getValue());
            }
        }

        // Only add the style object if it has content
        if (metadataObject.size() > 0) {
            jsonObject.add("style", metadataObject);
        }
        // Add other fields of Attributes if needed
        if (src.getId() != null) {
            jsonObject.addProperty("id", src.getId());
        }
        if (src.getType() != null) {
            jsonObject.addProperty("type", src.getType());
        }
        if (src.inputType != null) {
            jsonObject.addProperty("inputType", src.inputType);
        }
        if (src.getValue() != null) {
            jsonObject.addProperty("value", src.getValue());
        }
        if (src.checked != null && src.checked) {
            jsonObject.addProperty("checked", true);
        }
        if (src.dataNrMasked != null) {
            jsonObject.addProperty("data-nr-masked", src.dataNrMasked);
        if (src.min != null) {
            jsonObject.addProperty("min", src.min);
        }
        if (src.max != null) {
            jsonObject.addProperty("max", src.max);
        }
        if (src.step != null) {
            jsonObject.addProperty("step", src.step);
        }

        return jsonObject;
    }
}